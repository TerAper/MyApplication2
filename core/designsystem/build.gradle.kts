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
    // Google's barcode scanner: its own camera screen, no camera permission for the app.
    implementation(libs.play.services.code.scanner)
    // Where the phone is now (address from my location), and OpenStreetMap maps (no key needed).
    implementation(libs.play.services.location)
    implementation(libs.osmdroid.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
