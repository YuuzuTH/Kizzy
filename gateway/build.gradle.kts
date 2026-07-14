plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation (projects.domain)
    implementation (libs.kotlinx.coroutine)
    implementation (libs.bundles.network.ktor)
    implementation (libs.ktor.websockets)
    testImplementation (libs.junit)
    testImplementation (libs.ktor.server.cio)
    testImplementation (libs.ktor.server.websockets)
    // ktor-server-* above transitively pull in kotlinx-serialization's own BOM, which forces
    // kotlinx-serialization-json down to 1.5.0 on the test classpath — pin explicitly back to
    // the project's real version so `Json.decodeFromString<T>(string)`'s reified overload
    // resolves the same way it does everywhere else in the app.
    testImplementation (libs.kotlinx.serialization.json)
}

configurations.testCompileClasspath {
    resolutionStrategy.force(libs.kotlinx.serialization.json.get())
}
configurations.testRuntimeClasspath {
    resolutionStrategy.force(libs.kotlinx.serialization.json.get())
}