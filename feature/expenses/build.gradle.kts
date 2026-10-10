plugins {
    alias(libs.plugins.printmaster.android.feature)
}

android {
    namespace = "com.teraper.printmaster.feature.expenses"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
