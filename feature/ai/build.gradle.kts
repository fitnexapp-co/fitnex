plugins {
    alias(libs.plugins.fitnex.android.feature)
}

android {
    namespace = "com.fitnex.feature.ai"
}

dependencies {
    implementation(project(":core:data"))
}
