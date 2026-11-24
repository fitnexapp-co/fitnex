plugins {
    alias(libs.plugins.fitnex.android.feature)
}

android {
    namespace = "com.fitnex.feature.progress"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:anatomy"))
    implementation(project(":core:datastore")) // weekly goal

    testImplementation(project(":core:testing"))
}
