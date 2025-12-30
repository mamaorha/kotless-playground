import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

group = "kotless.playground"
version = "0.0.1"

plugins {
    kotlin("jvm") version "1.9.21" apply false
}

subprojects {
    apply {
        plugin("kotlin")
    }

    repositories {
        mavenLocal()
        maven(url = uri("https://packages.jetbrains.team/maven/p/ktls/maven"))
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(arrayOf("--release", "21"))
    }

    tasks.withType<KotlinJvmCompile> {
        kotlinOptions {
            jvmTarget = "21"
            languageVersion = "2.1"
            apiVersion = "2.1"
        }
    }

    configurations.all {
        resolutionStrategy {
            force("com.amazonaws:aws-lambda-java-core:1.2.3")
            force("commons-codec:commons-codec:1.15")
            force("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")
            force("com.fasterxml.jackson:jackson-bom:2.15.3")
            force("com.fasterxml.jackson.core:jackson-annotations:2.15.3")
            force("com.fasterxml.jackson.core:jackson-databind:2.15.3")
            force("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.15.3")
            force("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
            force("com.fasterxml.jackson.module:jackson-module-afterburner:2.15.3")
            force("org.springframework:spring-web:6.1.1")
            force("org.springframework:spring-webmvc:6.1.1")
            exclude("commons-logging", "commons-logging")
        }
    }
}
