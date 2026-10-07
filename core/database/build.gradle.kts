plugins {
    alias(libs.plugins.printmaster.android.library)
    alias(libs.plugins.printmaster.android.hilt)
    alias(libs.plugins.printmaster.android.room)
}

android {
    namespace = "com.teraper.printmaster.core.database"
}

dependencies {
    api(projects.core.model)
}
