plugins {
    alias(libs.plugins.fitnex.android.library)
    alias(libs.plugins.fitnex.android.compose)
}

android {
    namespace = "com.fitnex.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose) // fitnexScreen: opaque pages for push/pop
    implementation(project(":core:model")) // plain data shown by shared components (e.g. the streak)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
