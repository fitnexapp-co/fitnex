plugins {
    alias(libs.plugins.fitnex.android.feature)
}

android {
    namespace = "com.fitnex.feature.profile"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose) // "save as" launcher for the data export

    testImplementation(project(":core:testing"))
}
