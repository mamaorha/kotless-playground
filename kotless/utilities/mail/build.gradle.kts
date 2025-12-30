group = rootProject.group
version = rootProject.version

dependencies {
    implementation("io.kotless", "spring-boot-lang", "0.3.4")
    api(project(":kotless:utilities:auth"))

    implementation("com.sun.mail", "javax.mail", "1.6.2")
}