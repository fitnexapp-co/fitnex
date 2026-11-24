import androidx.room.gradle.RoomExtension
import com.fitnex.buildlogic.lib
import com.fitnex.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room + KSP, with exported JSON schemas so every migration can be verified in tests. */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("androidx.room")
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        dependencies {
            "implementation"(libs.lib("room-runtime"))
            "implementation"(libs.lib("room-ktx"))
            "ksp"(libs.lib("room-compiler"))
            "testImplementation"(libs.lib("room-testing"))
        }
    }
}
