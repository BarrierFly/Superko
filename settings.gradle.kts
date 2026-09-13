pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        gradlePluginPortal()
    }
    plugins {
        id("dev.kikugie.stonecutter") version "0.9.8"
        // loom-remap is the plugin for pre-unobfuscated-distribution MC versions like 1.19.4
        id("net.fabricmc.fabric-loom-remap") version "1.17.20"
    }
}

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    create(rootProject) {
        versions("1.19.4")
    }
}

rootProject.name = "superko"
