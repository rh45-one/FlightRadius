# ---------------------------------------------------------------------------
# FlightRadius release rules (kotlinx.serialization + Retrofit + Room + Hilt)
# ---------------------------------------------------------------------------

# kotlinx.serialization: keep generated serializers & companions
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.flightradius.app.**$$serializer { *; }
-keepclassmembers class com.flightradius.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.flightradius.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes so descriptor metadata survives shrinking
-keep @kotlinx.serialization.Serializable class com.flightradius.app.**

# Retrofit: service interface + annotations used via reflection
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep interface com.flightradius.app.data.api.FlightRadiusApi { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp / OkIO
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Room: generated impl + entities are referenced reflectively by Room runtime
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt / Dagger generated code
-keep class dagger.hilt.** { *; }
-keep class com.flightradius.app.*_Factory { *; }
-keep class com.flightradius.app.Hilt_* { *; }
-dontwarn dagger.**

# Kotlin metadata / coroutines
-keepclassmembers class kotlin.Metadata { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
