group = rootProject.group
version = rootProject.version

dependencies {
    implementation("io.kotless", "spring-boot-lang", "0.3.4")
    api("software.amazon.awssdk", "dynamodb", "2.23.15")
    api(project(":kotless:utilities:auth"))
    api(project(":kotless:utilities:common"))
}