group = rootProject.group
version = rootProject.version

dependencies {
    implementation("io.kotless", "spring-boot-lang", "0.3.4")
    api(project(":kotless:utilities:auth"))

    implementation("mysql", "mysql-connector-java", "8.0.21")
}