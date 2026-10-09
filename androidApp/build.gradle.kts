import java.net.Inet4Address
import java.net.NetworkInterface

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

/**
 * The Mac's address on the local network, so a phone on the same Wi-Fi finds the demo server
 * without anyone typing an IP. Re-evaluated on every build, even with the configuration cache.
 */
abstract class LanAddress : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String? = NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .sortedBy { if (it.name.startsWith("en")) 0 else 1 }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}

val defaultServerUrl: String = providers.gradleProperty("strela.serverUrl")
    .orElse(providers.of(LanAddress::class) {}.map { "http://$it:8080" })
    .getOrElse("http://10.0.2.2:8080")

android {
    namespace = "io.github.lobadzip.strela"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.lobadzip.strela"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.0.1"
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$defaultServerUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // A portfolio APK has to install with one tap, so release is signed with the debug key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
}
