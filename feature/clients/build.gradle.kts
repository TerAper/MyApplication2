plugins {
    alias(libs.plugins.printmaster.android.feature)
}

android {
    namespace = "com.teraper.printmaster.feature.clients"
}

dependencies {
    // Google's barcode scanner: its own camera screen, no camera permission for the app.
    implementation(libs.play.services.code.scanner)
    implementation(libs.kotlinx.coroutines.play.services)
}
