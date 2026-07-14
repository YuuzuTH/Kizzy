import com.android.build.api.dsl.LibraryDefaultConfig

plugins {
    id("kizzy.android.library")
    id("kizzy.android.hilt")
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "com.my.kizzy.data"
    defaultConfig {
        buildConfigFieldFromGradleProperty("BASE_URL","BASE_URL")
        buildConfigFieldFromGradleProperty("DISCORD_API_BASE_URL","DISCORD_API_URL")
        buildConfigFieldFromGradleProperty("GITHUB_API_BASE_URL","GITHUB_API_URL")
        buildConfigFieldFromGradleProperty("IMGUR_API_BASE_URL", "IMGUR_API_URL")
        // Discord webhook URL for the in-app log reporter (see LogWebhookReporter).
        // Read from an env var at build time, never committed — empty on forks/local
        // builds means the feature just no-ops instead of failing the build.
        buildConfigField(
            "String",
            "LOG_WEBHOOK_URL",
            "\"${System.getenv("KIZZY_LOG_WEBHOOK") ?: ""}\""
        )
    }
}

dependencies {
    implementation (libs.core.ktx)
    implementation (projects.domain)
    implementation (libs.bundles.network.ktor)
    implementation (libs.ktor.content.negotiation)
    implementation (libs.ktor.logging)
    implementation (projects.common.preference)
    implementation (projects.common.resources)
    implementation (projects.gateway)
    implementation (libs.blankj.utilcodex)
    testImplementation(libs.junit)
}

fun LibraryDefaultConfig.buildConfigFieldFromGradleProperty(fieldName: String, gradlePropertyName: String) {
    val propertyValue = project.properties[gradlePropertyName] as? String
    if (propertyValue != null) {
        buildConfigField("String", fieldName, propertyValue)
    }
}