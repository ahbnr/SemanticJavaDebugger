package de.ahbnr.semanticweb.sjdb.utils

import org.apache.jena.atlas.lib.IRILib

/**
 * Class whose preprocess() method pre-processes user input strings to provide usability features.
 *
 * Currently implemented features:
 *
 * * `...` enclosed strings are IRI encoded
 */
class UsabilityPreprocessor {
    private sealed class ParserState {
        object Normal: ParserState()

        class IRIEncodeString(val builder: StringBuilder = StringBuilder()): ParserState()
    }

    companion object {
        fun preprocess(input: String): String {
            val outputBuilder = StringBuilder()
            var state: ParserState = ParserState.Normal
            var isEscaping = false

            for (c in input) {
                if (!isEscaping && c == '\\') {
                    isEscaping = true
                    continue
                }

                when (state) {
                    is ParserState.Normal ->
                        if (!isEscaping && c == '`')
                            state = ParserState.IRIEncodeString()
                        else outputBuilder.append(c)

                    is ParserState.IRIEncodeString ->
                        if (!isEscaping && c == '`') {
                            outputBuilder.append(
                                IRILib.encodeUriComponent(
                                    state.builder.toString()
                                )
                            )
                            state = ParserState.Normal
                        }
                        else state.builder.append(c)
                }

                isEscaping = false
            }

            if (state is ParserState.IRIEncodeString)
                throw java.lang.IllegalArgumentException("Expected string enclosed in `...` but found no closing ` mark.")

            return outputBuilder.toString()
        }
    }
}