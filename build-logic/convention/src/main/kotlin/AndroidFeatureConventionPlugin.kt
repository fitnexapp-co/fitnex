import com.fitnex.buildlogic.lib
import com.fitnex.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A FITNEX feature module = Android library + Compose + Hilt + type-safe navigation.
 * Features depend on core modules only, never on each other.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("fitnex.android.library")
        pluginManager.apply("fitnex.android.compose")
        pluginManager.apply("fitnex.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        dependencies {
            "implementation"(project(":core:common"))
            "implementation"(project(":core:model"))
            "implementation"(project(":core:designsystem"))

            "implementation"(libs.lib("androidx-navigation-compose"))
            "implementation"(libs.lib("androidx-hilt-navigation-compose"))
            "implementation"(libs.lib("androidx-lifecycle-runtime-compose"))
            "implementation"(libs.lib("androidx-lifecycle-viewmodel-compose"))
            "implementation"(libs.lib("kotlinx-serialization-json"))
        }
    }
}
