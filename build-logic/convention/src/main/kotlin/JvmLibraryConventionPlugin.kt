import com.teraper.printmaster.buildlogic.JAVA_VERSION
import com.teraper.printmaster.buildlogic.configureKotlinCompile
import com.teraper.printmaster.buildlogic.lib
import com.teraper.printmaster.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Plain Kotlin module with no Android dependency (models, pure logic). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            configureKotlinCompile()

            dependencies {
                add("testImplementation", libs.lib("junit"))
            }
        }
    }
}
