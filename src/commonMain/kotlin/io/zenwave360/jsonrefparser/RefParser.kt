package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.jsonrefparser.io.InMemoryLoader
import io.zenwave360.jsonrefparser.io.defaultLoaders
import io.zenwave360.jsonrefparser.model.AuthenticationValue
import io.zenwave360.jsonrefparser.model.OriginalAllOf
import io.zenwave360.jsonrefparser.model.ParsedDocument
import io.zenwave360.jsonrefparser.model.RefParserOptions
import io.zenwave360.jsonrefparser.model.ResolvedRef
import io.zenwave360.jsonrefparser.model.SourceLocation
import io.zenwave360.jsonrefparser.parser.parseText
import io.zenwave360.jsonrefparser.resolver.ResolvingContext
import io.zenwave360.jsonrefparser.resolver.mergeAllOf
import io.zenwave360.jsonrefparser.resolver.resolveDocument

/**
 * Entry point for parsing, dereferencing, and merging JSON Schema documents.
 *
 * All pipeline steps are `suspend` and can be chained:
 * ```kotlin
 * val doc = RefParser("path/to/schema.yaml")
 *     .parse()
 *     .dereference()
 *     .mergeAllOf()
 *     .getParsedDocument()
 * ```
 *
 * @param uri     URI of the root schema document (file path, file://, http://, classpath:).
 * @param options Behaviour options for circular and missing references.
 * @param auth    HTTP authentication headers to inject for matching URLs.
 * @param loaders Document loader chain.  Defaults to the platform loaders.
 */
class RefParser(
    val uri: String,
    private val options: RefParserOptions = RefParserOptions(),
    private val auth: List<AuthenticationValue> = emptyList(),
    private val loaders: List<DocumentLoader> = defaultLoaders(),
) {
    private val normalizedUri: String = normalizeUri(uri)

    // Mutable pipeline state
    private var schema: MutableMap<String, Any?> = linkedMapOf()
    private var locations: MutableMap<String, SourceLocation> = mutableMapOf()
    private var documentLocations: MutableMap<String, MutableMap<String, SourceLocation>> = mutableMapOf()
    private var resolvedRefEntries: List<ResolvedRef> = emptyList()
    private var originalAllOfEntries: MutableList<OriginalAllOf> = mutableListOf()
    private var hasCircularRefs: Boolean = false
    /** The actual parsed root value — may be a List for top-level-array documents (e.g. AVSC). */
    internal var rawRoot: Any? = null
        private set

    // -----------------------------------------------------------------------
    // Builder-style configuration
    // -----------------------------------------------------------------------

    fun withOptions(options: RefParserOptions): RefParser =
        RefParser(uri, options, auth, loaders)

    fun withAuthentication(vararg auth: AuthenticationValue): RefParser =
        RefParser(uri, options, this.auth + auth.toList(), loaders)

    // -----------------------------------------------------------------------
    // Pipeline steps
    // -----------------------------------------------------------------------

    /**
     * Load and parse the root document.  Must be called before [dereference].
     */
    suspend fun parse(): RefParser {
        val loader = loaders.firstOrNull { it.canLoad(normalizedUri) }
            ?: throw IllegalStateException("No loader available for URI: $normalizedUri")
        val text = loader.load(normalizedUri)
        val raw = parseText(text, normalizedUri)
        schema = raw.map
        locations = raw.locations
        documentLocations = mutableMapOf(raw.uri to raw.locations.toMutableMap())
        rawRoot = raw.root
        return this
    }

    /**
     * Resolve all `$ref` nodes in the parsed document (including cross-file refs).
     * Calls [parse] implicitly if not already called.
     */
    suspend fun dereference(): RefParser {
        if (schema.isEmpty() && locations.isEmpty()) parse()
        val ctx = ResolvingContext(options, loaders, auth, locations)
        val raw = io.zenwave360.jsonrefparser.parser.RawDocument(normalizedUri, schema, schema, locations)
        schema = resolveDocument(raw, ctx)
        resolvedRefEntries = ctx.resolvedRefEntries
        documentLocations = ctx.documentLocations
        hasCircularRefs = ctx.hasCircular
        return this
    }

    /**
     * Merge every `allOf` array into its parent map.
     * Should be called after [dereference].
     */
    suspend fun mergeAllOf(): RefParser {
        originalAllOfEntries = mutableListOf()
        mergeAllOf(schema, collector = originalAllOfEntries)
        return this
    }

    // -----------------------------------------------------------------------
    // Output
    // -----------------------------------------------------------------------

    fun getParsedDocument(): ParsedDocument = ParsedDocument(
        schema = schema,
        locations = locations,
        documentLocations = documentLocations,
        resolvedRefs = resolvedRefEntries,
        originalAllOfs = originalAllOfEntries,
        hasCircularRefs = hasCircularRefs,
    )

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    companion object {
        /**
         * Build a [RefParser] from an already-loaded text string.
         *
         * Useful in tests and when the caller has already fetched the document.
         * Relative `$ref` values inside the document are resolved against [baseUri].
         *
         * @param text    Raw YAML/JSON text.
         * @param baseUri Base URI for relative-ref resolution. Defaults to `memory://anonymous`.
         */
        fun fromText(
            text: String,
            baseUri: String = "memory://anonymous",
            options: RefParserOptions = RefParserOptions(),
            auth: List<AuthenticationValue> = emptyList(),
            loaders: List<DocumentLoader>? = null,
        ): RefParser {
            val memLoader = InMemoryLoader(baseUri, text)
            val resolvedLoaders = loaders ?: defaultLoaders(auth)
            return RefParser(baseUri, options, auth, listOf(memLoader) + resolvedLoaders)
        }

        /**
         * Normalise the user-supplied URI string:
         * - Bare filesystem path (no scheme) → `file:///...`
         * - Windows drive letter (e.g. `C:\...`) → `file:///C:/...`
         * - `classpath:` without leading `/` → `classpath:/...`
         */
        fun normalizeUri(rawUri: String): String {
            if (rawUri.startsWith("classpath:") && !rawUri.startsWith("classpath:/")) {
                return "classpath:/" + rawUri.removePrefix("classpath:")
            }
            val colonIdx = rawUri.indexOf(':')
            if (colonIdx < 0) {
                // No scheme – treat as filesystem path
                val normalized = rawUri.replace('\\', '/')
                return if (normalized.startsWith("/")) "file://$normalized"
                else "file:///$normalized"
            }
            if (colonIdx == 1) {
                // Windows drive letter like C:\path\to\file.yaml
                val normalized = rawUri.replace('\\', '/')
                return "file:///$normalized"
            }
            return rawUri
        }
    }
}
