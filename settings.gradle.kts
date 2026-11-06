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
        maven { url = uri("https://api.xposed.info") }
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
        maven { url = uri("https://api.xposed.info") }
        // Optional local override for libxposed builds-from-source.
        // Maven Central is the primary source; mavenLocal is consulted only
        // when a developer publishes a custom snapshot locally.
        mavenLocal {
            content {
                includeGroup("io.github.libxposed")
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PayCardEditor"
include(":app")
 
