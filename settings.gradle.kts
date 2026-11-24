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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fitnex"

include(":app")

include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":core:anatomy")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:auth")
include(":core:data")
include(":core:testing")

include(":feature:progress")
include(":feature:nutrition")
include(":feature:workout")
include(":feature:ai")
include(":feature:calendar")
include(":feature:profile")
include(":feature:auth")
