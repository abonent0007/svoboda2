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

rootProject.name = "Svoboda2"
include(":app")
// AmneziaWG (Apache-2.0): Go-ядро + Config/Backend для нашего VpnController.
include(":tunnel")
project(":tunnel").projectDir = file("third_party/amneziawg-android/tunnel")
