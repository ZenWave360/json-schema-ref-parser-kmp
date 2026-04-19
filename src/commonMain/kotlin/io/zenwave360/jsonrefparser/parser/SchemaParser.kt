package io.zenwave360.jsonrefparser.parser

import io.zenwave360.jsonrefparser.JsonPointer
import io.zenwave360.jsonrefparser.model.SourceLocation
import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.api.lowlevel.Compose
import it.krzeminski.snakeyaml.engine.kmp.nodes.MappingNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.Node
import it.krzeminski.snakeyaml.engine.kmp.nodes.ScalarNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.SequenceNode

internal data class RawDocument(
    val uri: String,
    val root: Any?,
    val map: MutableMap<String, Any?>,
    val locations: MutableMap<String, SourceLocation>,
)

private val loadSettings: LoadSettings = LoadSettings.builder().build()

/** Parse YAML/JSON/AVSC text and return a [RawDocument] with map-of-maps and source locations. */
internal fun parseText(text: String, fileUri: String): RawDocument {
    val compose = Compose(loadSettings)
    @Suppress("DEPRECATION")
    val node: Node? = compose.composeString(text)
    if (node == null) return RawDocument(fileUri, linkedMapOf<String, Any?>(), linkedMapOf(), mutableMapOf())

    val locations = mutableMapOf<String, SourceLocation>()
    val value = buildNode(node, JsonPointer.ROOT, fileUri, locations)

    @Suppress("UNCHECKED_CAST")
    val map: MutableMap<String, Any?> = when (value) {
        is MutableMap<*, *> -> value as MutableMap<String, Any?>
        else -> linkedMapOf()
    }
    return RawDocument(fileUri, value, map, locations)
}

/**
 * Recursively build a [Map]/[List]/scalar from a snakeyaml [Node] while
 * simultaneously building the source-location index.
 */
internal fun buildNode(
    node: Node,
    pointer: JsonPointer,
    fileUri: String,
    locations: MutableMap<String, SourceLocation>,
): Any? {
    val start = node.startMark
    val end = node.endMark
    if (start != null && end != null) {
        locations[pointer.toString()] = SourceLocation(
            file = fileUri,
            line = start.line,
            column = start.column,
            endLine = end.line,
            endColumn = end.column,
        )
    }
    return when (node) {
        is MappingNode -> {
            val map = linkedMapOf<String, Any?>()
            node.value.forEach { tuple ->
                val key = (tuple.keyNode as? ScalarNode)?.value ?: return@forEach
                map[key] = buildNode(tuple.valueNode, pointer.child(key), fileUri, locations)
            }
            map
        }
        is SequenceNode -> {
            node.value.mapIndexed { i, child ->
                buildNode(child, pointer.child(i), fileUri, locations)
            }.toMutableList()
        }
        is ScalarNode -> scalarValue(node)
        else -> null
    }
}

/**
 * Convert a [ScalarNode] to the appropriate Kotlin type following the
 * YAML 1.2 Core Schema mapping.
 */
internal fun scalarValue(node: ScalarNode): Any? {
    val raw = node.value
    return when (node.tag.value) {
        "tag:yaml.org,2002:null"  -> null
        "tag:yaml.org,2002:bool"  -> raw.lowercase() == "true"
        "tag:yaml.org,2002:int"   -> raw.toLongOrNull()
            ?: raw.removePrefix("+").toLongOrNull()
            ?: raw  // fallback: keep as string if unparseable
        "tag:yaml.org,2002:float" -> when (raw.lowercase()) {
            ".inf", "+.inf" -> Double.POSITIVE_INFINITY
            "-.inf"         -> Double.NEGATIVE_INFINITY
            ".nan"          -> Double.NaN
            else            -> raw.toDoubleOrNull() ?: raw
        }
        else -> raw  // tag:yaml.org,2002:str and anything unrecognised
    }
}
