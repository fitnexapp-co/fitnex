plugins {
    alias(libs.plugins.fitnex.android.library)
    alias(libs.plugins.fitnex.hilt)
    alias(libs.plugins.fitnex.room)
}

android {
    namespace = "com.fitnex.core.database"
    // Exported schemas are test assets so MigrationTestHelper can rebuild every past version.
    sourceSets {
        getByName("test") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
