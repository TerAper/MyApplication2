plugins {
    alias(libs.plugins.printmaster.android.library)
}

android {
    namespace = "com.teraper.printmaster.core.testing"
}

// Shared fakes and test rules for ViewModel unit tests.
dependencies {
    api(projects.core.model)
    api(projects.core.data)
    api(libs.kotlinx.coroutines.test)
    api(libs.junit)
}
