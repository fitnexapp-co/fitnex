plugins {
    alias(libs.plugins.fitnex.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnex.core.model"
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
