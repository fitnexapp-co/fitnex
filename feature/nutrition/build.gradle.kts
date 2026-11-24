plugins {
    alias(libs.plugins.fitnex.android.feature)
}

android {
    namespace = "com.fitnex.feature.nutrition"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.activity.compose) // camera / photo picker launchers
    // Barcode scanner: camera preview + on-device ML Kit barcode reading.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(project(":core:testing"))
}
