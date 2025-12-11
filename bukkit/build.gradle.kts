plugins {
    id("io.github.goooler.shadow")
}

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly(group = "org.spigotmc", name = "spigot-api", version = "1.21.2-R0.1-SNAPSHOT")
    compileOnly(group = "org.popcraft", name = "chunky-common", version = "1.4.55.0")
    compileOnly(group = "org.popcraft", name = "chunky-bukkit", version = "1.4.55.0")
    compileOnly(group = "org.jetbrains", name = "annotations", version = "26.0.1")
    implementation(group = "org.bstats", name = "bstats-bukkit", version = "3.0.2")
    implementation(project(":extrachunky-common"))
    implementation(project(":extrachunky-paper"))
    implementation(project(":extrachunky-folia"))
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(
                "name" to (project.property("artifactName") ?: ""),
                "version" to project.version,
                "group" to project.group,
                "author" to (project.property("author") ?: ""),
                "description" to (project.property("description") ?: ""),
            )
        }
    }
    shadowJar {
        archiveClassifier.set("")
        minimize {
            exclude(project(":extrachunky-common"))
            exclude(project(":extrachunky-paper"))
            exclude(project(":extrachunky-folia"))
        }
        relocate("org.bstats", "${project.group}.extrachunky.lib.bstats")
        archiveFileName.set("${project.property("artifactName")}-Bukkit-${project.version}.jar")
    }
    build {
        dependsOn(shadowJar)
    }
}
