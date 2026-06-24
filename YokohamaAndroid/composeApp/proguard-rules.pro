# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.megatransportes.yokoh.**$$serializer { *; }
-keepclassmembers class com.megatransportes.yokoh.** {
    *** Companion;
}
-keepclasseswithmembers class com.megatransportes.yokoh.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep data/models
-keep class com.megatransportes.yokoh.data.models.** { *; }

# Keep enum classes
-keepclassmembers enum * { *; }

# Platform
-keep class com.megatransportes.yokoh.platform.** { *; }
-keep class com.megatransportes.yokoh.data.api.** { *; }
-keep class com.megatransportes.yokoh.data.session.** { *; }
-keep class com.megatransportes.yokoh.data.repository.** { *; }