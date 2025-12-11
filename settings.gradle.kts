pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
    }
    plugins {
        id("dev.architectury.loom") version "1.13-SNAPSHOT"
        id("io.github.goooler.shadow") version "8.1.7"
        id("org.spongepowered.gradle.plugin") version "2.2.0"
    }
}

rootProject.name = "extrachunky"

sequenceOf(
    "common",
    "paper",
    "folia",
    "bukkit",
    "fabric",
    "forge",
    "neoforge",
    "sponge"
).forEach {
    include("${rootProject.name}-$it")
    project(":${rootProject.name}-$it").projectDir = file(it)
}
