plugins {
    id("dev.architectury.loom")
    id("io.github.goooler.shadow")
}

val shade: Configuration by configurations.creating

repositories {
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    minecraft(group = "com.mojang", name = "minecraft", version = "1.21.9")
    mappings(loom.officialMojangMappings())
    forge(group = "net.minecraftforge", name = "forge", version = "1.21.9-59.0.0")
    compileOnly(group = "org.popcraft", name = "chunky-common", version = "1.4.55.0")
    compileOnly(group = "org.popcraft", name = "chunky-forge", version = "1.4.55.0")
    implementation(project(":extrachunky-common"))
    shade(project(":extrachunky-common"))
}

tasks {
    processResources {
        filesMatching("META-INF/mods.toml") {
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
        archiveClassifier.set("")
        archiveFileName.set("${project.property("artifactName")}-Forge-${project.version}.jar")
    }
    remapJar {
        enabled = false
    }
    build {
        dependsOn(shadowJar)
    }
}
