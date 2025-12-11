import org.spongepowered.gradle.plugin.config.PluginLoaders
import org.spongepowered.plugin.metadata.model.PluginDependency

plugins {
    id("org.spongepowered.gradle.plugin") version "2.2.0"
    id("io.github.goooler.shadow")
}

repositories {
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly(group = "org.popcraft", name = "chunky-common", version = "1.4.55.0")
    compileOnly(group = "org.popcraft", name = "chunky-sponge", version = "1.4.55.0")
    implementation(group = "org.bstats", name = "bstats-sponge", version = "3.1.0")
    implementation(project(":extrachunky-common"))
}

sponge {
    apiVersion("11.0.0")
    loader {
        name(PluginLoaders.JAVA_PLAIN)
        version("1.0")
    }
    license("GPL-3.0")
    plugin(rootProject.name) {
        displayName("${project.property("artifactName")}")
        version("${project.version}")
        entrypoint("${project.group}.extrachunky.ExtraChunkySponge")
        description("${project.property("description")}")
        dependency("spongeapi") {
            loadOrder(PluginDependency.LoadOrder.AFTER)
            optional(false)
        }
        dependency("chunky") {
            loadOrder(PluginDependency.LoadOrder.AFTER)
            optional(false)
            version("*")
        }
    }
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("org.bstats", "${project.group}.extrachunky.lib.bstats")
        archiveFileName.set("${project.property("artifactName")}-Sponge-${project.version}.jar")
    }
    build {
        dependsOn(shadowJar)
    }
}
