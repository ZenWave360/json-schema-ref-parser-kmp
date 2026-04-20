package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.model.ResolvedRef
import kotlin.test.assertFalse

internal fun assertAcyclic(root: Any?) {
    fun visit(node: Any?, path: String, active: MutableList<Any?>) {
        when (node) {
            is Map<*, *> -> {
                assertFalse(active.any { it === node }, "Cycle detected at $path")
                active.add(node)
                node.forEach { (key, value) ->
                    visit(value, "$path/${key ?: "<null>"}", active)
                }
                active.removeAt(active.lastIndex)
            }
            is List<*> -> {
                assertFalse(active.any { it === node }, "Cycle detected at $path")
                active.add(node)
                node.forEachIndexed { index, value ->
                    visit(value, "$path/$index", active)
                }
                active.removeAt(active.lastIndex)
            }
        }
    }

    visit(root, "$", mutableListOf())
}

internal fun containsRef(root: Any?): Boolean = when (root) {
    is Map<*, *> -> root.containsKey("\$ref") || root.values.any { containsRef(it) }
    is List<*> -> root.any { containsRef(it) }
    else -> false
}

internal fun annotateResolvedRefs(schema: MutableMap<String, Any?>, resolvedRefs: List<ResolvedRef>) {
    resolvedRefs.forEach { entry ->
        val target = entry.resolvedTo
        if (target is MutableMap<*, *>) {
            @Suppress("UNCHECKED_CAST")
            (target as MutableMap<String, Any?>)["x--original-\$ref"] = entry.refString
        }
    }

    @Suppress("UNCHECKED_CAST")
    val components = schema["components"] as? Map<String, Any?> ?: return
    val schemas = components["schemas"] as? Map<String, Any?> ?: return
    schemas.forEach { (name, value) ->
        if (value is MutableMap<*, *>) {
            @Suppress("UNCHECKED_CAST")
            (value as MutableMap<String, Any?>).apply {
                if (!containsKey("x--schema-name")) {
                    this["x--schema-name"] = name
                }
            }
        }
    }
}

internal fun normalizeSchemaFormat(schemaFormat: String?): String? = when {
    schemaFormat == null -> "asyncapi"
    Regex("""application\/vnd\.aai\.asyncapi(\+json|\+yaml)*;version=[\d.]+""").matches(schemaFormat) -> "asyncapi"
    Regex("""application\/vnd\.oai\.openapi(\+json|\+yaml)*;version=[\d.]+""").matches(schemaFormat) -> "openapi"
    Regex("""application\/schema(\+json|\+yaml)*;version=draft-\d+""").matches(schemaFormat) -> "jsonSchema"
    Regex("""application\/vnd\.apache\.avro(\+json|\+yaml)*;version=[\d.]+""").matches(schemaFormat) -> "avro"
    else -> null
}

internal fun messageJavaType(message: Map<String, Any?>): String? {
    val schemaFormat = normalizeSchemaFormat(message["schemaFormat"] as? String)
    val payload = message["payload"] as? Map<String, Any?>
    return when (schemaFormat) {
        "avro" -> {
            val name = payload?.get("name") as? String
            val namespace = payload?.get("namespace") as? String
            if (name != null && namespace != null) "$namespace.$name" else null
        }
        "jsonSchema" -> {
            val nestedSchema = payload?.get("schema") as? Map<String, Any?>
            nestedSchema?.get("javaType") as? String ?: payload?.get("javaType") as? String
        }
        "asyncapi", "openapi" -> {
            val nestedSchema = payload?.get("schema") as? Map<String, Any?>
            val schemaName = nestedSchema?.get("x--schema-name") as? String
                ?: payload?.get("x--schema-name") as? String
            schemaName
                ?: message["x-javaType"] as? String
                ?: message["messageId"] as? String
                ?: message["name"] as? String
        }
        else -> null
    }
}
