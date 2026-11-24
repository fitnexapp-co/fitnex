plugins {
    alias(libs.plugins.fitnex.android.feature)
}

android {
    namespace = "com.fitnex.feature.calendar"
}

dependencies {
    implementation(project(":core:data"))

    testImplementation(project(":core:testing"))
}
