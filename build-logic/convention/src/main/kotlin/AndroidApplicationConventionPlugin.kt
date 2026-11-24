import com.android.build.api.dsl.ApplicationExtension
import com.fitnex.buildlogic.FitnexBuild
import com.fitnex.buildlogic.configureKotlinJvm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = FitnexBuild.COMPILE_SDK
            defaultConfig {
                minSdk = FitnexBuild.MIN_SDK
                targetSdk = FitnexBuild.TARGET_SDK
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
    }
}
