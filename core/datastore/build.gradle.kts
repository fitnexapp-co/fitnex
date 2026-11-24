plugins {
    alias(libs.plugins.fitnex.android.library)
    alias(libs.plugins.fitnex.hilt)
}

android {
    namespace = "com.fitnex.core.datastore"
}

dependencies {
    implementation(project(":core:common"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface) // profile photo orientation
}
