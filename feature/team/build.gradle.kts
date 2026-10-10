plugins {
    alias(libs.plugins.printmaster.android.feature)
}

android {
    namespace = "com.teraper.printmaster.feature.team"
}

dependencies {
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    implementation(libs.androidx.activity.compose)
}
