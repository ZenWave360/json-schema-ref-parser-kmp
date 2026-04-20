package io.zenwave360.jsonrefparser.model

/**
 * The result of a fully parsed (and optionally dereferenced) schema.
 *
 * @param schema       The fully resolved map-of-maps representation.
 * @param locations    JSON Pointer string → source location for every node.
 * @param resolvedRefs Every `$ref` that was resolved during dereferencing.
 * @param originalAllOfs Every `allOf` array before it was merged.
 */
data class ParsedDocument(
    val schema: Map<String, Any?>,
    val locations: Map<String, SourceLocation>,
    val documentLocations: Map<String, Map<String, SourceLocation>> = emptyMap(),
    val resolvedRefs: List<ResolvedRef> = emptyList(),
    val originalAllOfs: List<OriginalAllOf> = emptyList(),
    val hasCircularRefs: Boolean = false,
)

/**
 * Records that a `$ref` was resolved and what object it resolved to.
 * [resolvedTo] is the same instance stored in [ParsedDocument.schema]
 * (same-instance guarantee applies).
 */
data class ResolvedRef(
    /** The original `$ref` string, e.g. `"#/components/schemas/Foo"`. */
    val refString: String,
    val resolvedTo: Any?,
    /** The value that replaced the original `$ref` map in the parent document. */
    val replacedValue: Any? = resolvedTo,
    /** Absolute target file URI when the ref points to an external document. */
    val targetUri: String? = null,
    /** URI of the document that contained the original `$ref`. */
    val sourceUri: String? = null,
    /** JSON Pointer of the original `$ref` usage site within [sourceUri]. */
    val sourcePointer: String? = null,
)

/**
 * Records the original `allOf` array before it was merged into [mergedMap].
 * [mergedMap] is the same instance stored in [ParsedDocument.schema].
 */
data class OriginalAllOf(
    val mergedMap: Map<String, Any?>,
    val allOfItems: List<Any?>,
)

// ---------------------------------------------------------------------------
// Identity-based lookup helpers (mirror JVM $Refs behaviour)
// ---------------------------------------------------------------------------

/** Find the [ResolvedRef] whose [ResolvedRef.resolvedTo] is the same instance as [obj]. */
fun ParsedDocument.getOriginalRef(obj: Any?): ResolvedRef? =
    resolvedRefs.firstOrNull { it.resolvedTo === obj }

/** Find the original `allOf` items for a map that was produced by [mergeAllOf]. */
fun ParsedDocument.getOriginalAllOf(mergedMap: Map<String, Any?>): List<Any?>? =
    originalAllOfs.firstOrNull { it.mergedMap === mergedMap }?.allOfItems
