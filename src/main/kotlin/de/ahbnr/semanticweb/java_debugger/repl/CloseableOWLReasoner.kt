package de.ahbnr.semanticweb.java_debugger.repl

import org.semanticweb.owlapi.reasoner.OWLReasoner

interface CloseableOWLReasoner : OWLReasoner, AutoCloseable {
    override fun close() {
        this.dispose()
    }
}

fun OWLReasoner.asCloseable(): CloseableOWLReasoner =
    object : OWLReasoner by this, CloseableOWLReasoner {}