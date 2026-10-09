plugins {
    alias(libs.plugins.printmaster.android.feature)
}

android {
    namespace = "com.teraper.printmaster.feature.settings"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
