import com.android.build.api.dsl.LibraryExtension
import com.fitnex.buildlogic.FitnexBuild
import com.fitnex.buildlogic.configureKotlinJvm
import com.fitnex.buildlogic.lib
import com.fitnex.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = FitnexBuild.COMPILE_SDK
            defaultConfig {
                minSdk = FitnexBuild.MIN_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = FitnexBuild.JAVA_VERSION
                targetCompatibility = FitnexBuild.JAVA_VERSION
            }
            testOptions {
                unitTests.isIncludeAndroidResources = true
            }
        }
        configureKotlinJvm()

        dependencies {
            "testImplementation"(libs.lib("junit"))
            "testImplementation"(libs.lib("kotlinx-coroutines-test"))
            "testImplementation"(libs.lib("turbine"))
        }
    }
}
