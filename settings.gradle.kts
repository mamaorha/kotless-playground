rootProject.name = "kotless-playground"

include(
    ":kotless:utilities:common",
    ":kotless:utilities:auth",
    ":kotless:utilities:rest",
    ":kotless:utilities:storage",
    ":kotless:utilities:dao",
    ":kotless:utilities:cache",
    ":kotless:utilities:mail",
    ":kotless:utilities:gamelift",
    ":kotless:playground"
)

pluginManagement {
    resolutionStrategy {
        this.eachPlugin {
            if (requested.id.id == "io.kotless") {
                useModule("io.kotless:gradle:${this.requested.version}")
            }
        }
    }

    repositories {
        mavenLocal()
        maven(url = uri("https://packages.jetbrains.team/maven/p/ktls/maven"))
        gradlePluginPortal()
        mavenCentral()
    }
}
