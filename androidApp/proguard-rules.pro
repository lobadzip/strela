# kotlinx.serialization keeps its generated serializers via its own consumer rules.
# Ktor's OkHttp engine references optional classes that are absent on Android.
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
