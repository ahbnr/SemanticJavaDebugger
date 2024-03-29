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
    implementation("com.google.guava:guava-testlib:31.1-jre")

    // local copy of jdi2owl mapper
    implementation("de.ahbnr.semanticweb:jdi2owl")

    // Apache Jena
    implementation("org.apache.jena:apache-jena-libs:4.2.0")

    // OWL-API 5
    implementation("net.sourceforge.owlapi:owlapi-api:5.1.19")
    implementation("net.sourceforge.owlapi:owlapi-parsers:5.1.19")
    implementation("net.sourceforge.owlapi:owlapi-distribution:5.1.19")

    // HermiT reasoner (OWLAPI 5 compatibility version)
    implementation("net.sourceforge.owlapi:org.semanticweb.hermit:1.4.5.519")

    // JFact reasoner (OWLAPI 5 compatibility version)
    implementation("net.sourceforge.owlapi:jfact:5.0.3")

    // Openllet reasoner
    // owl api support
    implementation("com.github.galigator.openllet:openllet-owlapi:2.6.5")
    // Jena support
    implementation("com.github.galigator.openllet:openllet-jena:2.6.5")
    // Openllet Explanation support
    implementation("com.github.galigator.openllet:openllet-explanation:2.6.5")
    // OWL and RDF linter
    implementation("com.github.galigator.openllet:openllet-pellint:2.6.5")

    // Extended OWL support
    implementation("com.github.owlcs:ontapi:3.0.0")

    // Generic explanation support
    implementation("net.sourceforge.owlapi:owlexplanation:5.0.0")

    // Logging library used by Apache Jena
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("org.slf4j:slf4j-simple:1.7.32")

    // Spoon for Java source code analysis
    implementation("fr.inria.gforge.spoon:spoon-core:10.0.1-beta-1")

    // For handling CLI parameters
    implementation("com.github.ajalt.clikt:clikt:3.3.0")

    // For handling interactive input (REPL)
    implementation("org.jline:jline:3.21.0")

    // Dependency injection
    implementation("io.insert-koin:koin-core:3.1.3")
    // testImplementation("io.insert-koin:koin-test:3.1.3")

    // Utilities for duration formatting etc
    implementation("org.apache.commons:commons-lang3:3.12.0")

    // Additional data structures / collections
    implementation("org.apache.commons:commons-collections4:4.4")

    // Query platform dependent special directories, e.g. XDG cache directory etc
    implementation("net.harawata:appdirs:1.2.1")

    // serialize Java objects to JSON
    implementation("com.google.code.gson:gson:2.9.0")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.8.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// Allow reading from stdin when running the program
val run: JavaExec by tasks
run.standardInput = System.`in`
run.jvmArgs = listOf(
    // We want to allow reflective accesses to some internals jdk.jdi
    "--add-opens", "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"
    // See also https://nipafx.dev/five-command-line-options-hack-java-module-system/
)

if (project.hasProperty("workingDir")) {
    run.workingDir = File(project.properties["workingDir"] as String)
}


tasks.test {
    useJUnitPlatform()
    jvmArgs = listOf(
        "--add-opens", "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"
    )

    // Compile the case studies for tests
    dependsOn(gradle.includedBuild("btrees").task(":classes"))
    dependsOn(gradle.includedBuild("PizzaSubscriptionService").task(":classes"))
    dependsOn(gradle.includedBuild("DoublyLinked").task(":classes"))

    // Parallelize tests
    maxParallelForks = Runtime.getRuntime().availableProcessors().div(2)
    if (maxParallelForks < 1) {
        maxParallelForks = 1
    }
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("sjdb")
        mergeServiceFiles()

        manifest {
            attributes["Main-Class"] = "de.ahbnr.semanticweb.sjdb.SemanticJavaDebuggerKt"
        }

        doLast {
            println("Created JAR at ${archiveFile.get().asFile.absolutePath}.")
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

    options.compilerArgs.addAll(
        listOf(
            // This should be supplied to javac to tell it that we may access internal package names at compile time.
            // We circumvent this by using the kotlinc compiler and telling it to ignore these errors by @Suppress annotations in the code
            //
            // Due to these two reasons, this line is probably unnecessary, but we add it for good measure
            "--add-exports", "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
            // See also https://nipafx.dev/five-command-line-options-hack-java-module-system/
        )
    )
}


tasks.withType<KotlinCompile>() {
    sourceCompatibility = "11"
    targetCompatibility = "11"

    kotlinOptions.jvmTarget = "11"

    // kotlinOptions.freeCompilerArgs += listOf("-Xjavac-arguments=--add-exports=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED")
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.time.ExperimentalTime -Xopt-in=kotlin.RequiresOptIn"
}

application {
    applicationDefaultJvmArgs = listOf(
        "--add-opens", "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
    )

    mainClass.set("de.ahbnr.semanticweb.sjdb.SemanticJavaDebuggerKt")
}


task("runInfo") {
    doFirst {
        val classpath = sourceSets.main.get().runtimeClasspath

        println("CLASSPATH=${classpath.joinToString(System.getProperty("path.separator"))}")
        println("MAINCLASS=${application.mainClass.get()}")
    }
}

