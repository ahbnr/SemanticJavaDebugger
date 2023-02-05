# A Semantic Debugger for Java :bug:
![Build and Test](https://github.com/ahbnr/SemanticJavaDebugger/actions/workflows/build.yml/badge.svg)

Semantic debugging, as introduced by Kamburjan et al. [(link)](https://doi.org/10.1007/978-3-030-77385-4_8), refers to the practice of applying technologies of the semantic web to query the run-time state of a program and combine it with external domain knowledge.

This repository provides the frontend implementation of a semantic debugging tool for the Java programming language, called the *Semantic Java Debugger* or `sjdb`.
The implementation of the backend has been factored out into a separate repository, see [jdi2owl](https://github.com/ahbnr/jdi2owl).

The sjdb tool provides an interactive, command line-based user interface through which users can

1. run Java programs and suspend their execution at user-defined breakpoints,
2. automatically extract RDF knowledge bases with description logic semantics that describe the current state of the program,
3. optionally supplement the knowledge base with external domain knowledge formalized in OWL,
4. run (semantic) queries on this extended knowledge base, and resolve the query results back to Java objects.

I developed this tool during my master's thesis. Please consult my thesis for detailed information on the tool and its formal foundations.
Link to my thesis: [Link](https://tuprints.ulb.tu-darmstadt.de/22143/)

## Prerequisites :clipboard:

This guide assumes, that all commands in the following sections are
executed in the `bash` shell of a linux system.

Please make sure that the following dependencies are available on your system:

* OpenJDK 11 (other Java implementations, like the Oracle JDK may not be compatible!)
* Git version 2.28.3

One of the core components of `sjdb` is `jdi2owl` which has been factored out into a separate repository.
It has been integrated into this repository as a git submodule and can be downloaded simply by running the following script:

```sh
./fetch-libs.sh
```

## Quickstart :rocket:

To run `sjdb` you can just execute the wrapper script `sjdb` in the root directory of this repository.

**It will take a long time to start for the first execution.**
This is because the project first needs to be compiled, which the script will do automatically, if it hasn't been done before.

## Usage :thinking:

The Semantic Java Debugger is meant to be used as an interactive, command-line debugging tool, similar to the Java debugger `jdb`.
Chapter 9 of the [thesis](https://tuprints.ulb.tu-darmstadt.de/22143/) gives a few examples on how it can be used.

However, `sjdb` also supports loading debugging commands from a `*.sjdb` script, instead of requiring them to be typed in manually, e.g.
```sh
./sjdb my-debugging-session.sjdb
```

You can find the implementations of the case studies of chapter 9 in the directory `casestudies`.
They also contain commented `.sjdb` files that can be used to execute the case studies fully automatically.
Furthermore, wrapper scripts are provided for compiling the debuggees and calling the `.sjdb` scripts.

Just call the following scripts to run the case studies for the different sections:

* Section 9.1.1: `casestudies/btrees/runStructureTest.sh`
* Section 9.1.1: `casestudies/btrees/runIteratorTest.sh`
* Section 9.2:
  * `casestudies/PizzaSubscriptionService/runSimple.sh`
  * `casestudies/PizzaSubscriptionService/runFuzzing.sh`

Moreover, many `.sjdb` scripts were implemented as part of of the automatic testing pipeline of this project.
These tests can also serve as usage examples.
You can run the tests by executing
```sh
./gradlew test
```

You can find the debugging scripts being executed by the tests in `src/test/resources/de/ahbnr/semanticweb/sjdb/tests/system/examples/tests/`.

## Building a redistributable JAR file :hammer_and_wrench:

While the `sjdb` wrapper script is convenient to get started quickly, you might want to build an executable JAR version of the semantic debugger that contains all dependencies and which can be easily distributed.

For this purpose, run
```sh
./gradlew shadowJar
```

The produced JAR file can now be found at `build/libs/sjdb-1.0-SNAPSHOT-all.jar`.

## License :balance_scale:

See [LICENSE.txt](./LICENSE.txt).
