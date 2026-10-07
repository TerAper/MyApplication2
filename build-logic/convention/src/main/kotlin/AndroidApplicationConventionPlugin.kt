import com.android.build.api.dsl.ApplicationExtension
import com.teraper.printmaster.buildlogic.COMPILE_SDK
import com.teraper.printmaster.buildlogic.JAVA_VERSION
import com.teraper.printmaster.buildlogic.MIN_SDK
import com.teraper.printmaster.buildlogic.TARGET_SDK
import com.teraper.printmaster.buildlogic.configureKotlinCompile
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                compileSdk = COMPILE_SDK
                defaultConfig {
                    minSdk = MIN_SDK
                    targetSdk = TARGET_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
            }
            configureKotlinCompile()
        }
    }
}
