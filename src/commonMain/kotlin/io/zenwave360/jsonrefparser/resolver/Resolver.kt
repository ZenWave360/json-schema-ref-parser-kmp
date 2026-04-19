package io.zenwave360.jsonrefparser.resolver

import io.zenwave360.jsonrefparser.JsonPointer
import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.jsonrefparser.model.AuthenticationValue
import io.zenwave360.jsonrefparser.model.CircularReferenceException
import io.zenwave360.jsonrefparser.model.MissingRefException
import io.zenwave360.jsonrefparser.model.OnCircular
import io.zenwave360.jsonrefparser.model.OnMissing
import io.zenwave360.jsonrefparser.model.OriginalAllOf
import io.zenwave360.jsonrefparser.model.RefParserOptions
import io.zenwave360.jsonrefparser.model.ResolvedRef
import io.zenwave360.jsonrefparser.model.SourceLocation
import io.zenwave360.jsonrefparser.parser.RawDocument
import io.zenwave360.jsonrefparser.parser.parseText

// ---------------------------------------------------------------------------
// Resolving context (mutable state for a single dereference run)
// ---------------------------------------------------------------------------

internal class ResolvingContext(
    val options: RefParserOptions,
    val loaders: List<DocumentLoader>,
    @Suppress("unused") val auth: List<AuthenticationValue>,
    val locationIndex: MutableMap<String, SourceLocation>,
) {
    /** URI → mutable root map for each loaded document. */
    val documentCache = mutableMapOf<String, Any?>()

    /** "uri#pointer" → the object that $ref resolved to (for same-instance guarantee). */
    val resolvedObjects = mutableMapOf<String, Any?>()

    /** Currently in-flight node keys (uri + pointer) – used for cycle detection. */
    val currentPath = mutableSetOf<String>()

    /** Already fully processed node keys – used to skip re-processing. */
    val visited = mutableSetOf<String>()

    var hasCircular = false

    /** Accumulates every resolved $ref for [ParsedDocument.resolvedRefs]. */
    val resolvedRefEntries = mutableListOf<ResolvedRef>()

    /** Accumulates original allOf arrays for [ParsedDocument.originalAllOfs]. */
    val originalAllOfEntries = mutableListOf<OriginalAllOf>()

    /** Per-document location maps for legacy compatibility and diagnostics. */
    val documentLocations = mutableMapOf<String, MutableMap<String, SourceLocation>>()
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

internal suspend fun resolveDocument(
    doc: RawDocument,
    ctx: ResolvingContext,
): MutableMap<String, Any?> {
    ctx.documentCache[doc.uri] = doc.root
    ctx.documentLocations[doc.uri] = doc.locations.toMutableMap()
    ctx.locationIndex.putAll(doc.locations)
    val resolvedRoot = resolveValue(doc.root, doc.uri, "", ctx)
    @Suppress("UNCHECKED_CAST")
    return resolvedRoot as? MutableMap<String, Any?> ?: linkedMapOf()
}

// ---------------------------------------------------------------------------
// Core recursive resolver
// ---------------------------------------------------------------------------

/**
 * Resolve $ref nodes inside [value] in place (updating parent map/list entries)
 * and return the (potentially replaced) value.
 *
 * @param value    The current node (Map, List, or scalar).
 * @param fileUri  The URI of the document that contains [value].
 * @param pointer  JSON Pointer path to [value] within [fileUri] (empty string = root).
 */
@Suppress("UNCHECKED_CAST")
internal suspend fun resolveValue(
    value: Any?,
    fileUri: String,
    pointer: String,
    ctx: ResolvingContext,
): Any? {
    val nodeKey = "$fileUri#$pointer"

    // Cycle detection
    if (nodeKey in ctx.currentPath) {
        ctx.hasCircular = true
        return when (ctx.options.onCircular) {
            OnCircular.FAIL    -> throw CircularReferenceException("Circular ${'$'}ref at $nodeKey")
            OnCircular.SKIP    -> value
            OnCircular.RESOLVE -> ctx.resolvedObjects[nodeKey] ?: value
        }
    }

    if (nodeKey in ctx.visited) return value

    ctx.currentPath.add(nodeKey)
    ctx.visited.add(nodeKey)

    try {
        return when {
            value is Map<*, *> && value.containsKey("\$ref") ->
                resolveRef(value as Map<String, Any?>, fileUri, pointer, ctx)

            value is MutableMap<*, *> -> {
                val map = value as MutableMap<String, Any?>
                for ((k, v) in map.entries.toList()) {
                    val resolved = resolveValue(v, fileUri, "$pointer/${escapeToken(k)}", ctx)
                    if (resolved !== v) map[k] = resolved
                }
                ctx.resolvedObjects[nodeKey] = map
                map
            }

            value is MutableList<*> -> {
                val list = value as MutableList<Any?>
                for (i in list.indices) {
                    val resolved = resolveValue(list[i], fileUri, "$pointer/$i", ctx)
                    if (resolved !== list[i]) list[i] = resolved
                }
                list
            }

            else -> value
        }
    } finally {
        ctx.currentPath.remove(nodeKey)
    }
}

// ---------------------------------------------------------------------------
// $ref resolution
// ---------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
private suspend fun resolveRef(
    refMap: Map<String, Any?>,
    fileUri: String,
    pointer: String,
    ctx: ResolvingContext,
): Any? {
    val refString = refMap["\$ref"] as? String
        ?: return refMap  // malformed $ref – leave in place

    val schemaRef = SchemaRef.parse(refString, fileUri)
    val targetFileUri = schemaRef.fileUri ?: fileUri
    val targetPointer = normalizePointer(schemaRef.pointer)
    val targetKey = "$targetFileUri#$targetPointer"

    if (targetKey in ctx.currentPath) {
        ctx.hasCircular = true
        return when (ctx.options.onCircular) {
            OnCircular.FAIL -> throw CircularReferenceException("Circular ${'$'}ref at $targetKey")
            OnCircular.SKIP -> refMap
            OnCircular.RESOLVE -> {
                val cached = ctx.resolvedObjects[targetKey]
                if (cached != null) mergeSiblings(refMap, cached) else refMap
            }
        }
    }

    // Load external document if needed
    if (schemaRef.format.isExternal && targetFileUri !in ctx.documentCache) {
        val text = loadFile(targetFileUri, ctx) ?: return when (ctx.options.onMissing) {
            OnMissing.SKIP -> refMap
            OnMissing.FAIL -> throw MissingRefException("Cannot load: $targetFileUri")
        }
        val raw = parseText(text, targetFileUri)
        ctx.documentCache[targetFileUri] = raw.root
        ctx.documentLocations[targetFileUri] = raw.locations.toMutableMap()
        // Merge external locations without overwriting existing entries so that
        // the root document's pointer "" (and any overlapping paths) retain their
        // original-file attribution.
        raw.locations.forEach { (key, loc) ->
            if (!ctx.locationIndex.containsKey(key)) ctx.locationIndex[key] = loc
        }
        // Recursively resolve the external document (before extracting the pointer)
        resolveValue(raw.root, targetFileUri, "", ctx)
    }

    val targetDoc = ctx.documentCache[targetFileUri]
        ?: return when (ctx.options.onMissing) {
            OnMissing.SKIP -> refMap
            OnMissing.FAIL -> throw MissingRefException("Document not in cache: $targetFileUri")
        }

    // Check same-instance cache
    if (targetKey in ctx.resolvedObjects) {
        val cached = ctx.resolvedObjects[targetKey]
        val replaced = mergeSiblings(refMap, cached)
        ctx.resolvedRefEntries.add(
            ResolvedRef(
                refString = refString,
                resolvedTo = cached,
                replacedValue = replaced,
                targetUri = schemaRef.fileUri,
            ),
        )
        return replaced
    }

    // Navigate to the pointer within the document
    val resolved: Any? = if (targetPointer.isEmpty()) {
        targetDoc
    } else {
        JsonPointer.parseFragment(targetPointer).resolve(targetDoc)
            ?: return when (ctx.options.onMissing) {
                OnMissing.SKIP -> refMap
                OnMissing.FAIL -> throw MissingRefException("Pointer $targetPointer not found in $targetFileUri")
            }
    }

    ctx.resolvedObjects[targetKey] = resolved

    // Recursively resolve the extracted node (it may itself contain $refs)
    val fullyResolved = resolveValue(resolved, targetFileUri, targetPointer, ctx)
    ctx.resolvedObjects[targetKey] = fullyResolved

    // Record the resolved ref for ParsedDocument.resolvedRefs
    val replaced = mergeSiblings(refMap, fullyResolved)
    ctx.resolvedRefEntries.add(
        ResolvedRef(
            refString = refString,
            resolvedTo = fullyResolved,
            replacedValue = replaced,
            targetUri = schemaRef.fileUri,
        ),
    )

    return replaced
}

/**
 * When a $ref map has sibling keys, merge them with the resolved object.
 * Resolved wins on key conflict.  If there are no siblings, return [resolved] as-is
 * (same object reference, which is what the same-instance guarantee relies on).
 */
@Suppress("UNCHECKED_CAST")
private fun mergeSiblings(refMap: Map<String, Any?>, resolved: Any?): Any? {
    val siblings = refMap.filterKeys { it != "\$ref" }
    if (siblings.isEmpty()) return resolved
    val merged = linkedMapOf<String, Any?>()
    merged.putAll(siblings)
    if (resolved is Map<*, *>) merged.putAll(resolved as Map<String, Any?>)
    return merged
}

// ---------------------------------------------------------------------------
// allOf merging
// ---------------------------------------------------------------------------

/** Identity-based stack to prevent infinite recursion for circular object graphs. */
internal class IdentityStack {
    private val stack = mutableListOf<Any?>()
    fun contains(obj: Any?): Boolean = stack.any { it === obj }
    fun push(obj: Any?) { stack.add(obj) }
    fun pop() { if (stack.isNotEmpty()) stack.removeLast() }
}

/**
 * Walk [value] and merge every `allOf` array into its parent map, replicating
 * the JVM implementation (smart properties/required merge, recursive nesting).
 *
 * @param collector Optional list to accumulate [OriginalAllOf] records.
 */
@Suppress("UNCHECKED_CAST")
internal fun mergeAllOf(
    value: Any?,
    identityStack: IdentityStack = IdentityStack(),
    collector: MutableList<OriginalAllOf>? = null,
) {
    if (value == null) return
    if (identityStack.contains(value)) return  // circular object graph – skip
    identityStack.push(value)
    try {
        when (value) {
            is MutableMap<*, *> -> {
                val map = value as MutableMap<String, Any?>
                // Process children first (depth-first)
                for ((k, v) in map.entries.toList()) {
                    if (k != "allOf") mergeAllOf(v, identityStack, collector)
                }
                // Now merge allOf at this level
                if (map.containsKey("allOf")) {
                    val allOf = map["allOf"]
                    if (allOf is List<*>) {
                        val acc = AllOfAccumulator()
                        collectAllOf(acc, map)
                        val merged = acc.build()
                        map.remove("allOf")
                        map.putAll(merged)
                        // Record original allOf for ParsedDocument.originalAllOfs
                        collector?.add(OriginalAllOf(map, allOf.toList()))
                    }
                }
            }
            is MutableList<*> -> {
                val list = value as MutableList<Any?>
                for (item in list) mergeAllOf(item, identityStack, collector)
            }
        }
    } finally {
        identityStack.pop()
    }
}

// ---------------------------------------------------------------------------
// allOf accumulator (mirrors JVM AllOfObject)
// ---------------------------------------------------------------------------

private class AllOfAccumulator {
    val combined = linkedMapOf<String, Any?>()        // everything except properties/required
    val properties = linkedMapOf<String, Any?>()      // merged properties (last-write-wins)
    val required = mutableListOf<Any?>()              // concatenated required arrays

    @Suppress("UNCHECKED_CAST")
    fun build(): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        result.putAll(combined)
        if (required.isNotEmpty()) result["required"] = required
        if (properties.isNotEmpty()) result["properties"] = properties
        return result
    }
}

/**
 * Collect all entries from [item] (which is a map that came from or contains allOf)
 * into [acc], handling nested allOf recursively.
 */
@Suppress("UNCHECKED_CAST")
private fun collectAllOf(acc: AllOfAccumulator, item: Map<String, Any?>) {
    if (item.size == 1 && item.containsKey("allOf")) {
        // Purely a nested allOf wrapper – recurse into its entries
        val inner = item["allOf"]
        if (inner is List<*>) {
            for (entry in inner) {
                if (entry is Map<*, *>) collectAllOf(acc, entry as Map<String, Any?>)
            }
        }
        return
    }

    for ((k, v) in item) {
        when (k) {
            "allOf" -> {
                if (v is List<*>) {
                    for (entry in v) {
                        if (entry is Map<*, *>) collectAllOf(acc, entry as Map<String, Any?>)
                    }
                }
            }
            else -> acc.combined[k] = v
        }
    }
    if (item.containsKey("properties")) {
        val props = item["properties"]
        if (props is Map<*, *>) acc.properties.putAll(props as Map<String, Any?>)
    }
    if (item.containsKey("required")) {
        val req = item["required"]
        if (req is List<*>) acc.required.addAll(req)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private suspend fun loadFile(uri: String, ctx: ResolvingContext): String? {
    val loader = ctx.loaders.firstOrNull { it.canLoad(uri) } ?: return null
    return try {
        loader.load(uri)
    } catch (e: Exception) {
        if (ctx.options.onMissing == OnMissing.SKIP) null
        else throw MissingRefException("Failed to load: $uri", e)
    }
}

/** Escape a JSON Pointer token (RFC 6901). */
private fun escapeToken(key: String): String = key.replace("~", "~0").replace("/", "~1")

private fun normalizePointer(pointer: String?): String = when (pointer) {
    null, "#/" -> ""
    else -> pointer.removePrefix("#")
}
