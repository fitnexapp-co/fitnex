plugins {
    alias(libs.plugins.fitnex.android.library)
}

android {
    namespace = "com.fitnex.core.testing"
}

dependencies {
    api(project(":core:auth"))
    api(project(":core:data"))
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.test)
}
