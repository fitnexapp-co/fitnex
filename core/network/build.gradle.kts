plugins {
    alias(libs.plugins.fitnex.android.library)
    alias(libs.plugins.fitnex.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnex.core.network"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        val backendUrl = providers.gradleProperty("fitnex.backendUrl").get()
        require(backendUrl.startsWith("https://")) { "fitnex.backendUrl must use HTTPS" }
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
    }
}

dependencies {
    implementation(project(":core:common"))
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
}
