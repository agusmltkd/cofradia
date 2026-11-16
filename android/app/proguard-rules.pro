# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.agusmltkd.compas.**$$serializer { *; }
-keepclassmembers class com.agusmltkd.compas.** { *** Companion; }
-keepclasseswithmembers class com.agusmltkd.compas.** { kotlinx.serialization.KSerializer serializer(...); }

# Recurso JSON de demo que se lee por classloader
-keep class com.agusmltkd.compas.core.Demo { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
