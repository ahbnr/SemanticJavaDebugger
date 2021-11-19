package de.ahbnr.semanticweb.java_debugger.rdf.mapping

// Maybe load some of these from the ontology files?
data class Namespaces(
    val rdf: String,
    val rdfs: String,
    val owl: String,
    // the language domain model
    val java: String,
    // the static internal program domain model (User defined classes etc.)
    val prog: String,
    // the runtime program domain model (Java objects etc.)
    val run: String
)

fun genDefaultNs(): Namespaces {
    // FIXME: to give the runtime namespace "run" of the program instance currently being debugged,
    //   we somehow need to identify
    //   ...the computer this program is running on
    //   ...the pid of the program instance
    //   ...the id of the stopped thread if we handle multi-threading
    //   ...the time when the program was stopped
    //   for now, we can approximate with the time when the program was stopped

    return Namespaces(
        rdf = "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
        rdfs = "http://www.w3.org/2000/01/rdf-schema#",
        owl = "http://www.w3.org/2002/07/owl#",
        // FIXME: Check if these are really appropriate:
        java = "https://github.com/ahbnr/SemanticJavaDebugger/Java#",
        prog = "https://github.com/ahbnr/SemanticJavaDebugger/Program#",
        run = "https://github.com/ahbnr/SemanticJavaDebugger/Run${System.nanoTime()}#"
    )
}