plugins {
    alias(libs.plugins.printmaster.android.feature)
}

android {
    namespace = "com.teraper.printmaster.feature.reports"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
