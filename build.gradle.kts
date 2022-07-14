import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.martmists.commons.*
import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.CurseRelation
import groovy.lang.Closure
import net.fabricmc.loom.task.RemapJarTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("fabric-loom") version "0.11.34"
    kotlin("jvm") version "1.7.10"
    kotlin("kapt") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.matthewprenger.cursegradle") version "1.4.0"
    `maven-publish`
}

val modVersion = "1.0.0"
val minecraftVersion = "1.18.2"
val gunpowderVersion = "2.0.0"
val javaVersion = JavaVersion.VERSION_17
project.version = "${modVersion}+${minecraftVersion}"
project.group = "io.github.gunpowder"

repositories {
    mavenCentral()
    martmists()
    maven("https://maven.nucleoid.xyz/")
}

val shade by configurations.creating

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$minecraftVersion+build.3:v2")
    modApi("io.github.gunpowder:gunpowder:$gunpowderVersion+$minecraftVersion")
    modRuntimeOnly("io.github.gunpowder:gunpowder:$gunpowderVersion+$minecraftVersion:runtime")

    // Automatically collect all mixins
    kapt("io.github.gunpowder:gunpowder-processor:$gunpowderVersion")
}

kapt {
    useBuildCache = false

    arguments {
        arg("mixin.package", "io.github.gunpowder.mixin")
        arg("mixin.name", "template")
        arg("mixin.plugin", "false")
    }
}

java {
    withSourcesJar()
}

sourceSets {
    val main by getting {
        resources.srcDirs("${project.buildDir.absolutePath}/generated/source/kapt/main/resources")
    }
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = javaVersion.toString()
            freeCompilerArgs += listOf("-Xcontext-receivers")
        }
    }

    named<ProcessResources>("processResources") {
        dependsOn("kaptKotlin")

        filesMatching("fabric.mod.json") {
            expand(
                "version" to version,
                "gunpowder" to "$gunpowderVersion+$minecraftVersion",
            )
        }
    }

    named<Jar>("jar") {
        enabled = false
    }

    val shadowJar by named<ShadowJar>("shadowJar") {
        configurations = listOf(shade)
        destinationDirectory.set(file("${project.buildDir.absolutePath}/devlibs/"))
    }

    val remapJar by named<RemapJarTask>("remapJar") {
        inputFile.set(shadowJar.archiveFile)
    }

    named("build") {
        dependsOn(remapJar)
    }
}

val publishEnabled: String? by project
if ((publishEnabled ?: "false").toBoolean()) {
    val publishUser: String by project
    val publishPassword: String by project
    val publishSnapshot: String by project
    val publishVersion: String? by project

    publishing {
        repositories {
            martmistsPublish(publishUser, publishPassword, publishSnapshot.toBoolean())
        }

        publications {
            create<MavenPublication>("jvm") {
                groupId = project.group as String
                artifactId = rootProject.name
                version = publishVersion ?: project.version as String

                artifact(tasks.named("sourcesJar")) {
                    classifier = "sources"
                }

                artifact(tasks.named("remapJar")) {
                    classifier = ""
                }
            }
        }
    }

    val curseToken: String? by project
    val curseId: String by project

    if (curseToken != null) {
        curseforge {
            apiKey = curseToken
            curseProjects.add(CurseProject().apply {
                apiKey = this@curseforge.apiKey
                id = curseId
                releaseType = if (publishSnapshot.toBoolean()) "alpha" else "release"
                changelogType = "markdown"
                changelog = rootProject.file("CHANGELOG.md").readText().split("---").first()
                addGameVersion(minecraftVersion)
                addGameVersion("Fabric")
                addGameVersion("Java $javaVersion")

                curseRelations = mutableSetOf<Closure<*>>(
                    closureOf<CurseRelation> {
                        requiredDependency("gunpowder-mc")
                    }
                )

                mainArtifact(tasks.getByName<RemapJarTask>("remapJar").archiveFile)
            })

            curseGradleOptions.apply {
                forgeGradleIntegration = false
                javaIntegration = false
            }
        }
    }
}
