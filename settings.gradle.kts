pluginManagement {
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

rootProject.name = "MOVA"

// -Pmova.desktopOnly=true: compila só o desktop (não exige o Android SDK).
if (providers.gradleProperty("mova.desktopOnly").orNull != "true") {
    include(":app")
}
include(":core")
include(":desktop")
