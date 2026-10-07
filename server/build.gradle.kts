import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "io.github.lobadzip"
version = "1.0.0"

application {
    mainClass = "io.github.lobadzip.strela.server.ApplicationKt"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    implementation(projects.shared)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.serialization.json)

    // The router is a remote OSRM instance. The JDK client, because CIO's TLS fails the handshake there.
    implementation(libs.ktor.client.java)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnitPlatform()
}

// The baked routes live with the app's resources; the server reads the same file from its classpath.
tasks.processResources {
    from(rootProject.file("composeApp/src/commonMain/composeResources/files/routes.json"))
}

tasks.register<JavaExec>("bakeRoutes") {
    description = "Precomputes OSRM routes for every trip in the demo city"
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.lobadzip.strela.server.routing.BakeRoutesKt"
    workingDir = rootDir
}

// Run from the repository root so the web build and the route cache resolve the same way as in Docker.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
