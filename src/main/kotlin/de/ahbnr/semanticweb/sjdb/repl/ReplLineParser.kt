package de.ahbnr.semanticweb.sjdb.repl

class ReplLineParser {
    private sealed class State {
        object Normal : State()

        class ArgParse() : State() {
            val builder = StringBuilder()
        }

        class StringParse(val delimiter: Char, val builder: StringBuilder, val nextState: State) : State()
    }

    private val stringDelimiters = setOf('\'', '"')

    fun parse(line: String): List<String> {
        val argv = mutableListOf<String>()
        var state: State = State.Normal
        var escaping = false

        fun processChar(c: Char) {
            if (!escaping && c == '\\') {
                escaping = true
                return
            }

            var nextState: State = state
            when (val state = state) {
                is State.Normal -> {
                    if (!c.isWhitespace()) {
                        if (!escaping && stringDelimiters.contains(c)) {
                            val argParseState = State.ArgParse()
                            nextState = State.StringParse(c, argParseState.builder, argParseState)
                        } else {
                            nextState = State.ArgParse()
                            nextState.builder.append(c)
                        }
                    }
                }

                is State.ArgParse -> {
                    if (!escaping && c.isWhitespace()) {
                        argv.add(state.builder.toString())
                        nextState = State.Normal
                    } else if (!escaping && stringDelimiters.contains(c)) {
                        nextState = State.StringParse(c, state.builder, state)
                    } else {
                        state.builder.append(c)
                    }
                }

                is State.StringParse -> {
                    if (!escaping && c == state.delimiter) {
                        nextState = state.nextState
                    } else {
                        state.builder.append(c)
                    }
                }
            }

            escaping = false
            state = nextState
        }

        for (c in line) {
            processChar(c)
        }

        when (val state = state) {
            is State.ArgParse -> {
                argv.add(state.builder.toString())
            }

            is State.StringParse -> {
                argv.add(state.builder.toString())
            }

            else -> Unit
        }

        return argv
    }
}