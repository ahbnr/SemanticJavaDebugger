import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    application
}

group = "de.ahbnr.semanticweb"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.jena:apache-jena-libs:4.2.0")
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("org.slf4j:slf4j-simple:1.7.32")

    testImplementation(kotlin("test"))
}

tasks.test {
    useTestNG()
}

tasks.withType<JavaCompile>() {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}


tasks.withType<KotlinCompile>() {
    sourceCompatibility = "11"
    targetCompatibility = "11"


    kotlinOptions.jvmTarget = "11"
}

application {
    mainClassName = "MainKt"
}