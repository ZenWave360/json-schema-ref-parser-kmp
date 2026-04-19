@file:JsExport

package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.model.ParsedDocument
import io.zenwave360.jsonrefparser.model.ResolvedRef
import io.zenwave360.jsonrefparser.model.SourceLocation
import io.zenwave360.jsonrefparser.parser.parseText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExport
fun parseSchemaText(input: String, baseUri: String = "memory://anonymous"): Any? {
    val normalizedBaseUri = RefParser.normalizeUri(baseUri)
    val raw = parseText(input, normalizedBaseUri)
    return exportParsedDocument(
        ParsedDocument(
            schema = raw.map,
            locations = raw.locations,
            documentLocations = mapOf(raw.uri to raw.locations),
        ),
    )
}

@OptIn(ExperimentalJsExport::class, DelicateCoroutinesApi::class)
@JsExport
fun dereferenceSchema(uri: String, mergeAllOf: Boolean = false): Promise<Any?> = GlobalScope.promise {
    val parser = RefParser(uri).dereference()
    if (mergeAllOf) {
        parser.mergeAllOf()
    }
    exportParsedDocument(parser.getParsedDocument())
}

@OptIn(ExperimentalJsExport::class, DelicateCoroutinesApi::class)
@JsExport
fun dereferenceSchemaText(
    input: String,
    baseUri: String = "memory://anonymous",
    mergeAllOf: Boolean = false,
): Promise<Any?> = GlobalScope.promise {
    val parser = RefParser.fromText(input, baseUri = baseUri).dereference()
    if (mergeAllOf) {
        parser.mergeAllOf()
    }
    exportParsedDocument(parser.getParsedDocument())
}

private fun exportParsedDocument(document: ParsedDocument): Any? {
    val result = js("{}")
    result.schema = convertToPlain(document.schema)
    result.locations = convertToPlain(document.locations)
    result.documentLocations = convertToPlain(document.documentLocations)
    result.resolvedRefs = exportResolvedRefs(document.resolvedRefs)
    result.hasCircularRefs = document.hasCircularRefs
    return result
}

private fun exportResolvedRefs(resolvedRefs: List<ResolvedRef>): Any? {
    val result = js("[]")
    resolvedRefs.forEach { ref ->
        val item = js("{}")
        item.refString = ref.refString
        item.targetUri = ref.targetUri
        result.push(item)
    }
    return result
}

private fun convertToPlain(value: Any?): Any? {
    return when (value) {
        null -> null
        is SourceLocation -> exportSourceLocation(value)
        is Map<*, *> -> {
            val result = js("{}")
            value.forEach { (key, childValue) ->
                result[key.toString()] = convertToPlain(childValue)
            }
            result
        }
        is Collection<*> -> {
            val result = js("[]")
            value.forEach { item ->
                result.push(convertToPlain(item))
            }
            result
        }
        else -> value
    }
}

private fun exportSourceLocation(location: SourceLocation): Any? {
    val result = js("{}")
    result.file = location.file
    result.line = location.line
    result.column = location.column
    result.endLine = location.endLine
    result.endColumn = location.endColumn
    return result
}
