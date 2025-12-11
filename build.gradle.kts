plugins {
    id("java-library")
    id("maven-publish")
    id("io.github.goooler.shadow") version "8.1.7" apply false
}

subprojects {
    plugins.apply("java-library")
    plugins.apply("maven-publish")

    group = "${project.property("group")}"
    version = "${project.property("version")}"

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.codemc.io/repository/maven-releases/")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release = 21
        }
        jar {
            archiveClassifier.set("noshade")
        }
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "${project.group}"
                artifactId = project.name
                version = "${project.version}"
                from(components["java"])
            }
        }
    }
}
