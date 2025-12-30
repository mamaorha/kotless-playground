group = rootProject.group
version = rootProject.version

dependencies {
    api(project(":kotless:utilities:cache"))
    implementation("io.kotless", "spring-boot-lang", "0.3.4")
    api("software.amazon.awssdk", "gamelift", "2.23.15")
}