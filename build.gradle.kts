import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.github.johnrengelman.shadow") version "7.1.0"
    kotlin("jvm") version "1.5.31"
    application
}

group = "de.ahbnr.semanticweb"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Apache Jena
    implementation("org.apache.jena:apache-jena-libs:4.2.0")

    // Extended OWL support
    implementation("com.github.owlcs:ontapi:2.1.0")

    // Logging library used by Apache Jena
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("org.slf4j:slf4j-simple:1.7.32")

    // For handling CLI parameters
    implementation("com.github.ajalt.clikt:clikt:3.3.0")

    // For handling interactive input (REPL)
    implementation("org.jline:jline:3.21.0")

    // Dependency injection
    implementation("io.insert-koin:koin-core:3.1.3")
    testImplementation("io.insert-koin:koin-test:3.1.3")

    testImplementation(kotlin("test"))
}

// Allow reading from stdin when running the program
val run: JavaExec by tasks
run.standardInput = System.`in`


tasks.test {
    useTestNG()
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("SemanticJavaDebugger")
        mergeServiceFiles()

        manifest {
            attributes["Main-Class"] = "de.ahbnr.semanticweb.java_debugger.MainKt"
        }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
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
    // applicationDefaultJvmArgs = listOf(
    //     "--add-exports java.base/kotlin.collections=ALL-UNNAMED",
    // )

    mainClassName = "MainKt"
}