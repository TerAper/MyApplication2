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

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
