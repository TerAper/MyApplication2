plugins {
    alias(libs.plugins.printmaster.android.library)
    alias(libs.plugins.printmaster.android.compose)
}

android {
    namespace = "com.teraper.printmaster.core.designsystem"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
