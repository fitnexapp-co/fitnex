plugins {
    alias(libs.plugins.fitnex.android.library)
    alias(libs.plugins.fitnex.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fitnex.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.serialization.json)
    api(libs.androidx.health.connect) // steps, heart rate, weight in; workouts out

    testImplementation(project(":core:testing"))
    testImplementation(project(":core:anatomy")) // catalog ↔ muscle-map consistency check
    testImplementation(libs.room.runtime)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
