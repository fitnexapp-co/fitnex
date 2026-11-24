plugins {
    alias(libs.plugins.fitnex.android.feature)
}

android {
    namespace = "com.fitnex.feature.auth"
}

dependencies {
    implementation(project(":core:data"))

    testImplementation(project(":core:testing"))
}
