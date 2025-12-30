group = rootProject.group
version = rootProject.version

dependencies {
    implementation("software.amazon.awssdk", "s3", "2.23.15")
    implementation("io.kotless", "dsl-common-aws", "0.3.4")
    api(project(":kotless:utilities:common"))
}