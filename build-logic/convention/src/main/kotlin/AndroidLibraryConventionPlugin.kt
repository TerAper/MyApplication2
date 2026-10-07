import com.android.build.api.dsl.LibraryExtension
import com.teraper.printmaster.buildlogic.COMPILE_SDK
import com.teraper.printmaster.buildlogic.JAVA_VERSION
import com.teraper.printmaster.buildlogic.MIN_SDK
import com.teraper.printmaster.buildlogic.configureKotlinCompile
import com.teraper.printmaster.buildlogic.lib
import com.teraper.printmaster.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk = COMPILE_SDK
                defaultConfig {
                    minSdk = MIN_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
            }
            configureKotlinCompile()

            dependencies {
                add("testImplementation", libs.lib("junit"))
            }
        }
    }
}
