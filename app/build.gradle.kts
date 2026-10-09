plugins {
    alias(libs.plugins.printmaster.android.application)
    alias(libs.plugins.printmaster.android.compose)
    alias(libs.plugins.printmaster.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.teraper.printmaster"

    defaultConfig {
        applicationId = "com.teraper.printmaster"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.database)
    implementation(projects.core.data)

    implementation(projects.feature.today)
    implementation(projects.feature.clients)
    implementation(projects.feature.orders)
    implementation(projects.feature.payments)
    implementation(projects.feature.more)
    implementation(projects.feature.catalog)
    implementation(projects.feature.account)
    implementation(projects.feature.pricelist)
    implementation(projects.feature.settings)
    implementation(projects.feature.reports)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
