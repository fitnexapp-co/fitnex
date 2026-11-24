plugins {
    alias(libs.plugins.fitnex.android.library)
    alias(libs.plugins.fitnex.hilt)
}

android {
    namespace = "com.fitnex.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
