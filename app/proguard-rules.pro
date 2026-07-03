# kotlinx.serialization — keep generated serializers so R8 does not strip them.
# Without these, generic payloads (OutgoingPayload<T>/PayloadData<T>) and the
# Prefs Json.decodeFromString calls throw at runtime in minified builds, which
# silently breaks the gateway and app/media detection.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.my.kizzy.**$$serializer { *; }
-keepclassmembers class com.my.kizzy.** { *** Companion; }
-keepclasseswithmembers class com.my.kizzy.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class kizzy.gateway.**$$serializer { *; }
-keepclassmembers class kizzy.gateway.** { *** Companion; }
-keepclasseswithmembers class kizzy.gateway.** { kotlinx.serialization.KSerializer serializer(...); }

#OkHttp Rules
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Ktor Rules
-keepclassmembers class io.ktor.http.** { *; }

# Keep Domain data classes
-keep class com.my.kizzy.domain.model.** { <fields>; }

# Keep Data data classes
-keep class com.my.kizzy.data.remote.** { <fields>; }

# Keep Gateway data classes
-keep class kizzy.gateway.entities.** { <fields>; }

# slf4j error during build
-dontwarn org.slf4j.impl.StaticLoggerBinder

# some unknown error
-dontwarn java.lang.invoke.StringConcatFactory

-dontwarn com.my.kizzy.resources.R$drawable