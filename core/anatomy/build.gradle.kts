plugins {
    alias(libs.plugins.fitnex.android.library)
    alias(libs.plugins.fitnex.android.compose)
}

android {
    namespace = "com.fitnex.core.anatomy"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:designsystem"))

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
