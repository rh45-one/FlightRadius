package com.flightradius.app.data.secure

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flightradius.app.data.opensky.OpenSkyCredentials
import java.io.File
import java.security.GeneralSecurityException
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CredentialStoreTest {

    /** Reversible stand-in for the Keystore cipher; flags tampering. */
    private class XorCipher(private val key: Byte = 0x5A) : SecretCipher {
        var broken = false
        override fun encrypt(plaintext: ByteArray) = byteArrayOf(MAGIC) + plaintext.map { (it.toInt() xor key.toInt()).toByte() }
        override fun decrypt(blob: ByteArray): ByteArray {
            if (broken || blob.firstOrNull() != MAGIC) throw GeneralSecurityException("bad blob")
            return blob.drop(1).map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
        }
        companion object { const val MAGIC: Byte = 7 }
    }

    private object JvmBase64 : CredentialStore.Base64Codec {
        override fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        override fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
    }

    private val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val cipher = XorCipher()
    private lateinit var file: File
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { file }
    }
    private val store by lazy { CredentialStore(dataStore, cipher, JvmBase64) }

    @Before
    fun setUp() {
        folder.create()
        file = File(folder.root, "secure.preferences_pb")
    }

    @After
    fun tearDown() {
        scope.cancel()
        folder.delete()
    }

    @Test
    fun `saves, reads back and clears credentials`() = runBlocking {
        assertEquals(StoredCredentials.None, store.stored.first())

        store.save(OpenSkyCredentials("  client ", " s3cret "))
        assertEquals(OpenSkyCredentials("client", "s3cret"), store.current())

        store.clear()
        assertNull(store.current())
    }

    @Test
    fun `nothing is persisted in plaintext`() = runBlocking {
        store.save(OpenSkyCredentials("client-id-123", "very-secret-456"))
        val raw = file.readBytes().decodeToString()
        assertFalse(raw.contains("client-id-123"))
        assertFalse(raw.contains("very-secret-456"))
    }

    @Test
    fun `an undecryptable blob is reported, not thrown`() = runBlocking {
        store.save(OpenSkyCredentials("client", "secret"))
        cipher.broken = true
        assertEquals(StoredCredentials.Unreadable, store.stored.first())
        assertNull(store.current())
    }

    @Test
    fun `garbage in the preference is unreadable`() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("opensky_api_client")] = "%%%not-base64%%%" }
        assertEquals(StoredCredentials.Unreadable, store.stored.first())
    }

    @Test
    fun `credentials never render in toString`() {
        val text = OpenSkyCredentials("id-7f3a", "pw-9c1e").toString()
        assertFalse(text.contains("id-7f3a"))
        assertFalse(text.contains("pw-9c1e"))
    }
}
