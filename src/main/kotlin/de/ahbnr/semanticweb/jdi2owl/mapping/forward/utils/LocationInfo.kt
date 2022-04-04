package de.ahbnr.semanticweb.jdi2owl.mapping.forward.utils

import com.sun.jdi.AbsentInformationException
import com.sun.jdi.Location
import spoon.reflect.cu.SourcePosition

class LocationInfo private constructor(val id: String, val sourcePath: String, val line: Int) {
    companion object {
        fun fromSourcePosition(position: SourcePosition): LocationInfo {
            // This is often an absolute path.
            // A relative path would be nicer, but is not well-defined if there are multiple project roots
            val filePath = position.file.path

            return LocationInfo(
                "${filePath}_${position.sourceStart}",
                filePath,
                position.line
            )
        }

        fun fromJdiLocation(location: Location): LocationInfo? {
            val line = location.lineNumber()

            return try {
                if (line >= 0) {
                    LocationInfo(
                        "${location.hashCode()}",
                        location.sourcePath(),
                        line
                    )
                } else null
            } catch (e: AbsentInformationException) {
                null
            }
        }
    }
}