# Keep Kotlin metadata for reflection-based libs
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.kimi.proxy.android.**$$serializer { *; }
-keepclassmembers class com.kimi.proxy.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.kimi.proxy.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose
-dontwarn androidx.compose.**

# App
-keep class com.kimi.proxy.android.data.model.** { *; }
