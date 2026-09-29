package com.flightradius.app.data.secure

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flightradius.app.data.opensky.CredentialSource
import com.flightradius.app.data.opensky.OpenSkyCredentials
import com.flightradius.app.util.log.AppLog
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Result of reading the stored credentials. */
sealed interface StoredCredentials {
    data object None : StoredCredentials
    data class Present(val credentials: OpenSkyCredentials) : StoredCredentials

    /** A blob exists but can't be decrypted (e.g. Keystore key lost). */
    data object Unreadable : StoredCredentials
}

/**
 * Persists the OpenSky API client, encrypted with [SecretCipher], in its
 * own DataStore file (excluded from backups — see data_extraction_rules).
 * Plaintext only ever lives in memory; nothing here is logged.
 */
class CredentialStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val base64: Base64Codec = AndroidBase64
) : CredentialSource {

    val stored: Flow<StoredCredentials> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> decode(prefs[KEY]) }
        .distinctUntilChanged()

    override suspend fun current(): OpenSkyCredentials? =
        (stored.first() as? StoredCredentials.Present)?.credentials

    suspend fun save(credentials: OpenSkyCredentials) {
        val payload = Json.encodeToString(
            Payload.serializer(),
            Payload(credentials.clientId.trim(), credentials.clientSecret.trim())
        )
        val blob = base64.encode(cipher.encrypt(payload.encodeToByteArray()))
        dataStore.edit { it[KEY] = blob }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private fun decode(blob: String?): StoredCredentials {
        if (blob.isNullOrEmpty()) return StoredCredentials.None
        return try {
            val payload = Json.decodeFromString(
                Payload.serializer(),
                cipher.decrypt(base64.decode(blob)).decodeToString()
            )
            if (payload.clientId.isBlank() || payload.clientSecret.isBlank()) {
                StoredCredentials.None
            } else {
                StoredCredentials.Present(OpenSkyCredentials(payload.clientId, payload.clientSecret))
            }
        } catch (e: Exception) {
            // Class name only: the exception message could echo key material.
            AppLog.w(TAG, "stored credentials unreadable", "cause" to e.javaClass.simpleName)
            StoredCredentials.Unreadable
        }
    }

    @Serializable
    private data class Payload(val clientId: String, val clientSecret: String)

    /** Seam so JVM tests don't need android.util.Base64. */
    interface Base64Codec {
        fun encode(bytes: ByteArray): String
        fun decode(text: String): ByteArray
    }

    private object AndroidBase64 : Base64Codec {
        override fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
        override fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
    }

    private companion object {
        const val TAG = "CredentialStore"
        val KEY = stringPreferencesKey("opensky_api_client")
    }
}
