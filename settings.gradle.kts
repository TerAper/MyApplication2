pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PrintMaster"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:model")
include(":core:database")
include(":core:designsystem")
include(":core:data")
include(":core:testing")

include(":feature:today")
include(":feature:clients")
include(":feature:orders")
include(":feature:payments")
include(":feature:more")
include(":feature:catalog")
include(":feature:account")
include(":feature:pricelist")
include(":feature:settings")
include(":feature:reports")
include(":feature:calls")
include(":feature:imports")
include(":feature:team")
