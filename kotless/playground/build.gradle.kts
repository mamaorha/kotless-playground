import io.kotless.plugin.gradle.dsl.KotlessGradleConfig.Optimization.Autowarm
import io.kotless.plugin.gradle.dsl.kotless
import io.kotless.resource.Lambda.Config.Runtime.GraalVM
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val dev = false
val suffix = if (dev) "-dev" else ""

group = rootProject.group
version = rootProject.version

plugins {
    id("io.kotless") version "0.3.4" apply true
    id("org.hidetake.swagger.generator") version "2.19.2" apply true
}

dependencies {
    swaggerCodegen("io.swagger.codegen.v3:swagger-codegen-cli:3.0.51")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.19")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("javax.validation", "validation-api", "2.0.1.Final")
    implementation("javax.xml.bind", "jaxb-api", "2.1")
    implementation("io.kotless", "spring-boot-lang", "0.3.4")
    api(project(":kotless:utilities:auth"))
    api(project(":kotless:utilities:cache"))
    api(project(":kotless:utilities:rest"))
    api(project(":kotless:utilities:storage"))
    api(project(":kotless:utilities:gamelift"))
    api(project(":kotless:utilities:dao"))

    testImplementation(kotlin("test"))
}

kotless {
    config {
        aws {
            prefix = "playground$suffix"

            storage {
                bucket = "CHANGE_ME-kotless-playground$suffix"
            }

            profile = "default"
            region = "CHANGE_ME"
        }

        optimization {
            autowarm = Autowarm(enable = false)
        }
    }

    webapp {
        dns("playground$suffix", "CHANGE_ME.com")

        lambda {
            kotless {
                packages = setOf("kotless.playground")
            }

            runtime = GraalVM
            memoryMb = 2048
        }

        graal {
            buildImageAdditionalBinds = listOf(
                file("../../swaggers")
            )

            apiPackages = listOf(
                "kotless.playground.api"
            )

            modelPackages = listOf(
                "kotless.playground.model",
                "kotless.playground.data"
            )
        }

        deployment {
            name = project.name + suffix
        }

        cors {
            enabled = true
        }
    }
    extensions {
        local {
            port = 9090
            debugPort = 6000
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

swaggerSources {
    register("playground") {
        //val validationTask = validation

        setInputFile(file("../../swaggers/playground.yaml"))

        code(delegateClosureOf<org.hidetake.gradle.swagger.generator.GenerateSwaggerCode> {
            language = "spring"
            components = listOf("models", "apis")
            additionalProperties = hashMapOf(
                "useTags" to "true",
                "apiPackage" to "kotless.playground.api",
                "modelPackage" to "kotless.playground.model",
                "interfaceOnly" to "true"
            )
            //dependsOn(validationTask)
        })
    }
}

tasks.withType<KotlinJvmCompile> {
    dependsOn(tasks.named("generateSwaggerCode"))
}

sourceSets {
    val main by getting
    val playground by swaggerSources.getting
    main.java.srcDir("${playground.code.outputDir}/src/main/java")
}