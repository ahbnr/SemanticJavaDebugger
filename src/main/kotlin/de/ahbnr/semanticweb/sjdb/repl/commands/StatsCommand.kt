@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE")

package de.ahbnr.semanticweb.sjdb.repl.commands

import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import de.ahbnr.semanticweb.jdi2owl.utils.Statistics
import org.koin.core.component.KoinComponent

class StatsCommand : REPLCommand(name = "stats"), KoinComponent {
    private val dumpJson by option().file(canBeFile = true, canBeDir = false)

    override fun run() {
        val knowledgeBase = tryGetKnowledgeBase()

        val statistics = Statistics(knowledgeBase.ontology)

        for ((_, metric) in statistics.allMetrics) {
            logger.log("${metric.name}: ${metric.value}")
        }

        dumpJson?.let { out ->
            class StatisticsTypeAdapter: TypeAdapter<Statistics>() {
                override fun write(out: JsonWriter, value: Statistics) {
                    out.beginObject()

                    for ((fieldName, metric) in value.allMetrics) {
                        out.name(fieldName)

                        when (val metricValue = metric.value) {
                            is Int -> out.value(metricValue)
                            is Long -> out.value(metricValue)
                            else -> TODO("JSON serialization only supports int and long values as of now.")
                        }
                    }

                    out.endObject()
                }

                override fun read(`in`: JsonReader): Statistics = TODO("Not yet implemented")
            }
            val gson = GsonBuilder()
                .registerTypeAdapter(Statistics::class.java, StatisticsTypeAdapter())
                .create()

            out.writeText(gson.toJson(statistics))
        }
    }
}