plugins {
    alias(libs.plugins.fitnex.android.feature)
}

android {
    namespace = "com.fitnex.feature.workout"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:anatomy"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.activity.compose) // notification / camera permission requests in live mode
    // Camera rep counter: on-device pose estimation, frames never leave the phone.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.pose.detection)

    testImplementation(project(":core:testing"))
}
