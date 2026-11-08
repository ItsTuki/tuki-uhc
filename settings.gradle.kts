pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "tuki-uhc"

// Edición Legacy: Spigot/Paper 1.8.8 – 26.3 (compilada para Java 8).
include("legacy")
