plugins {
    alias(libs.plugins.fitnex.android.library)
    alias(libs.plugins.fitnex.hilt)
}

android {
    namespace = "com.fitnex.core.auth"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network")) // provides AuthTokenProvider for the backend client

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)
}
