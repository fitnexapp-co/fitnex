import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.fitnex.buildlogic.lib
import com.fitnex.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Enables Jetpack Compose (Kotlin 2 compose compiler) on an app or library module. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
        }
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> { buildFeatures.compose = true }
        }

        dependencies {
            val bom = libs.lib("androidx-compose-bom")
            "implementation"(platform(bom))
            "androidTestImplementation"(platform(bom))
            "testImplementation"(platform(bom))
            "implementation"(libs.lib("androidx-compose-ui-tooling-preview"))
            "debugImplementation"(libs.lib("androidx-compose-ui-tooling"))
        }
    }
}
