import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.4.20"
    id("org.jetbrains.intellij") version "0.4.22"
}

val projectGroup: String by project
val projectVersion: String by project
val platformVersion: String by project
val platformDownloadSources: String by project
val kotlinVersion: String by project
val spekVersion: String by project

group = projectGroup
version = projectVersion

intellij {
    version = platformVersion
    downloadSources = platformDownloadSources.toBoolean()
    updateSinceUntilBuild = true
}

configurations.implementation {
    exclude(group = "com.jetbrains", module = "ideaIC")
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("com.github.javaparser:javaparser-core:3.15.0")
    implementation("org.slf4j:slf4j-simple:1.7.29")
    implementation("io.github.microutils:kotlin-logging:1.12.0")

    testImplementation(kotlin("test"))
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
}

tasks {
    test {
        useJUnitPlatform()
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val fatJar = task("fatJar", type = Jar::class) {
    baseName = "${project.name}-all"
    manifest {
        attributes["Main-Class"] = "com.github.softwareAnalysisTeam.unitTestFuzzer.MainKt"
    }

    from(configurations
        .runtimeClasspath.get()
        .map { if (it.isDirectory) it else zipTree(it) })

    with(tasks["jar"] as CopySpec)
}

tasks {
    "build" {
        dependsOn(fatJar)
    }
}