import java.util.Properties

plugins {
    alias(libs.plugins.fitnex.android.application)
    alias(libs.plugins.fitnex.android.compose)
    alias(libs.plugins.fitnex.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services) // reads app/google-services.json (Firebase project config)
}

android {
    namespace = "com.fitnex.app"

    defaultConfig {
        applicationId = "com.fitnex.app"
        versionCode = 22
        versionName = "1.7.0"
        // -Pfitnex.abi=arm64-v8a builds a phone-only APK for sideloading (~30 MB instead of ~84 MB:
        // ML Kit's pose engine ships one 15–20 MB native library per CPU type). Google Play uses the
        // AAB and delivers only the device's own ABI automatically.
        (project.findProperty("fitnex.abi") as String?)?.let { abi -> ndk { abiFilters += abi.split(",") } }
    }

    // Release signing comes from keystore.properties (never committed). Without it, release builds
    // are produced unsigned and must be signed before installing or uploading to Google Play.
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:data"))

    implementation(project(":feature:progress"))
    implementation(project(":feature:nutrition"))
    implementation(project(":feature:workout"))
    implementation(project(":feature:ai"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:auth"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    // Training reminders (daily worker) and the home-screen widget.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.hilt.android.testing)
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:anatomy")) // tap muscles at their real map coordinates
    kspTest(libs.hilt.compiler)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
