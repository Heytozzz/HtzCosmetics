// settings.gradle.kts — the first file Gradle reads.
// It declares WHERE to download build plugins from and WHICH Minecraft versions we build.

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        // Added later for the NeoForge port:
        // maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    // Stonecutter: one source tree, many Minecraft versions / loaders.
    id("dev.kikugie.stonecutter") version "0.10"

    // Picks the right Loom variant per version (remapping Loom for <=1.21.11, plain Loom for 26.1+).
    id("dev.kikugie.loom-back-compat") version "0.4.3"

    // Lets Gradle auto-download the exact JDK each version needs (e.g. Java 21 for 1.21.8).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /**
         * Creates one node per (Minecraft version, loader): `versions/{version}-{loader}`.
         * Each loader has its own build script: `build.{loader}.gradle.kts`.
         * To add NeoForge/Forge later we only add entries here plus a new build script.
         */
        fun match(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        // 1.21.9 is intentionally not supported. Later: 1.20.1 / 1.21.1 (forge, neoforge).
        match("1.21.8", "fabric")
        match("1.21.10", "fabric")
        match("1.21.11", "fabric")
        match("26.1", "fabric")   // also runs on 26.1.1 and 26.1.2
        match("26.2", "fabric")
        match("26.3", "fabric")

        // The version whose code is "plain" (uncommented) in src/ when committed to git.
        vcsVersion = "1.21.8-fabric"
    }
}

rootProject.name = "HtzCosmetics"

// Offline owner tools (key generation, Blockbench conversion, manifest signing). Never shipped in the mod.
include("tools")

// HtzCosmetics Studio: desktop app for the owner that wraps the tools above. Never shipped in the mod.
include("studio")
