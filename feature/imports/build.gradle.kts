plugins {
    alias(libs.plugins.printmaster.android.feature)
}

android {
    namespace = "com.teraper.printmaster.feature.imports"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
