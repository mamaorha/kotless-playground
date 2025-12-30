group = rootProject.group
version = rootProject.version

dependencies {
    implementation("io.kotless", "spring-boot-lang", "0.3.4")
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", "2.14.2")
    implementation("io.github.openfeign:feign-spring4:12.2")
    api(project(":kotless:utilities:auth"))
}