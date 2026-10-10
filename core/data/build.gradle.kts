plugins {
    alias(libs.plugins.printmaster.android.library)
    alias(libs.plugins.printmaster.android.hilt)
}

android {
    namespace = "com.teraper.printmaster.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.exifinterface)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.analytics)
    implementation(libs.kotlinx.coroutines.play.services)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
