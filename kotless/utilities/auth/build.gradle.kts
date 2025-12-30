group = rootProject.group
version = rootProject.version

dependencies {
    implementation("io.kotless", "spring-boot-lang", "0.3.4")
    api(project(":kotless:utilities:common"))

    api("com.auth0", "jwks-rsa", "0.22.1")
    api("com.auth0", "java-jwt", "4.4.0")
    implementation("software.amazon.awssdk", "cognitoidentityprovider", "2.23.15")
    implementation("software.amazon.awssdk", "secretsmanager", "2.23.15")
}