plugins {
    id("dev.architectury.loom")
    id("io.github.goooler.shadow")
}

val shade: Configuration by configurations.creating

repositories {
    maven("https://maven.neoforged.net/releases/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    minecraft(group = "com.mojang", name = "minecraft", version = "1.21.11")
    mappings(loom.officialMojangMappings())
    neoForge(group = "net.neoforged", name = "neoforge", version = "21.11.0-beta")
    compileOnly(group = "org.popcraft", name = "chunky-common", version = "1.4.55.0")
    compileOnly(group = "org.popcraft", name = "chunky-neoforge", version = "1.4.55.0")
    implementation(project(":extrachunky-common"))
    shade(project(":extrachunky-common"))
}

tasks {
    processResources {
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(
                "id" to rootProject.name,
                "version" to project.version,
                "name" to (project.property("artifactName") ?: ""),
                "description" to (project.property("description") ?: ""),
                "author" to (project.property("author") ?: "")
            )
        }
    }
    shadowJar {
        configurations = listOf(shade)
        archiveClassifier.set("dev")
        archiveFileName.set(null as String?)
    }
    remapJar {
        inputFile.set(shadowJar.get().archiveFile)
        archiveFileName.set("${project.property("artifactName")}-NeoForge-${project.version}.jar")
    }
}
