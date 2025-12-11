plugins {
    id("dev.architectury.loom")
    id("io.github.goooler.shadow")
}

val shade: Configuration by configurations.creating

repositories {
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    minecraft(group = "com.mojang", name = "minecraft", version = "1.21.11")
    mappings(loom.officialMojangMappings())
    modImplementation(group = "net.fabricmc", name = "fabric-loader", version = "0.18.1")
    modImplementation(group = "net.fabricmc.fabric-api", name = "fabric-api", version = "0.139.4+1.21.11")
    compileOnly(group = "org.popcraft", name = "chunky-common", version = "1.4.55.0")
    compileOnly(group = "org.popcraft", name = "chunky-fabric", version = "1.4.55.0")
    implementation(project(":extrachunky-common"))
    shade(project(":extrachunky-common"))
}

tasks {
    processResources {
        filesMatching("fabric.mod.json") {
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
        archiveFileName.set("${project.property("artifactName")}-Fabric-${project.version}.jar")
    }
}
