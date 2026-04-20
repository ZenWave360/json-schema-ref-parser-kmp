package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.io.ClasspathLoader
import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.jsonrefparser.io.FileLoader
import io.zenwave360.jsonrefparser.io.HttpLoader
import io.zenwave360.jsonrefparser.model.OnCircular
import io.zenwave360.jsonrefparser.model.OnMissing
import io.zenwave360.jsonrefparser.model.OriginalAllOf
import io.zenwave360.jsonrefparser.model.ParsedDocument
import io.zenwave360.jsonrefparser.model.AuthenticationType as CoreAuthenticationType
import io.zenwave360.jsonrefparser.model.RefParserOptions
import io.zenwave360.jsonrefparser.model.ResolvedRef
import io.zenwave360.jsonrefparser.model.SourceLocation
import io.zenwave360.jsonrefparser.resolver.RefFormat
import java.io.File
import java.net.URI
import java.net.URL
import java.util.AbstractMap
import java.util.IdentityHashMap
import java.util.function.Predicate
import kotlin.jvm.JvmField

class AuthenticationValue @JvmOverloads constructor(
    var key: String? = null,
    var value: String? = null,
    var type: AuthenticationType = AuthenticationType.HEADER,
    private var urlMatcher: Predicate<URL>? = ANY_MATCH,
    private var urlPatterns: List<String> = listOf(".*"),
) {
    enum class AuthenticationType {
        QUERY,
        HEADER,
    }

    fun withHeader(headerName: String, headerValue: String): AuthenticationValue {
        this.key = headerName
        this.value = headerValue
        this.type = AuthenticationType.HEADER
        return this
    }

    fun withHeader(header: String): AuthenticationValue {
        val split = header.split(":", limit = 2)
        require(split.size == 2) { "Header must be in 'Name: value' format" }
        return withHeader(split[0].trim(), split[1].trim())
    }

    fun withQueryParam(key: String, value: String): AuthenticationValue {
        this.key = key
        this.value = value
        this.type = AuthenticationType.QUERY
        return this
    }

    fun withUrlMatcher(urlMatcher: Predicate<URL>): AuthenticationValue {
        this.urlMatcher = urlMatcher
        this.urlPatterns = emptyList()
        return this
    }

    fun withUrlPattern(urlPattern: String): AuthenticationValue {
        this.urlPatterns = listOf(urlPattern)
        this.urlMatcher = null
        return this
    }

    fun setUrlPattern(urlPattern: String) {
        withUrlPattern(urlPattern)
    }

    fun setUrlPatterns(urlPatterns: List<String>) {
        this.urlPatterns = urlPatterns
        this.urlMatcher = null
    }

    fun matches(url: URL): Boolean =
        when {
            urlMatcher != null -> urlMatcher!!.test(url)
            urlPatterns.isNotEmpty() -> urlPatterns.any { url.toString().matches(it.toRegex()) }
            else -> true
        }

    internal fun toModel(): io.zenwave360.jsonrefparser.model.AuthenticationValue {
        val name = requireNotNull(key) { "key must be provided" }
        val authValue = requireNotNull(value) { "value must be provided" }
        return io.zenwave360.jsonrefparser.model.AuthenticationValue(
            key = name,
            value = authValue,
            type = when (type) {
                AuthenticationType.QUERY -> CoreAuthenticationType.QUERY
                AuthenticationType.HEADER -> CoreAuthenticationType.HEADER
            },
            urlMatcher = if (urlMatcher != null) ({ url -> matches(URI(url).toURL()) }) else null,
            urlPatterns = urlPatterns,
        )
    }

    companion object {
        private val ANY_MATCH = Predicate<URL> { true }
    }
}

class `$Ref`(
    private val ref: String,
    private val uriString: String = ref,
) {
    fun getRef(): String = ref

    fun getRefFormat(): RefFormat = RefFormat.of(ref)

    fun getURI(): URI = URI.create(uriString)
}

class `$RefParserOptions`(
    var onCircular: OnCircular = OnCircular.RESOLVE,
    var onMissing: OnMissing = OnMissing.FAIL,
) {
    enum class OnCircular {
        RESOLVE,
        SKIP,
        FAIL,
    }

    enum class OnMissing {
        FAIL,
        SKIP,
    }

    fun withOnCircular(onCircular: OnCircular): `$RefParserOptions` {
        this.onCircular = onCircular
        return this
    }

    fun withOnMissing(onMissing: OnMissing): `$RefParserOptions` {
        this.onMissing = onMissing
        return this
    }

    internal fun toCore(): RefParserOptions = RefParserOptions(
        onCircular = when (onCircular) {
            OnCircular.RESOLVE -> io.zenwave360.jsonrefparser.model.OnCircular.RESOLVE
            OnCircular.SKIP -> io.zenwave360.jsonrefparser.model.OnCircular.SKIP
            OnCircular.FAIL -> io.zenwave360.jsonrefparser.model.OnCircular.FAIL
        },
        onMissing = when (onMissing) {
            OnMissing.FAIL -> io.zenwave360.jsonrefparser.model.OnMissing.FAIL
            OnMissing.SKIP -> io.zenwave360.jsonrefparser.model.OnMissing.SKIP
        },
    )
}

class `$JsonContext` internal constructor(private val value: Any?) {
    fun json(): Any? = value
}

class `$Refs`(
    private val root: Any?,
    private val schemaMap: Map<String, Any?>,
    private val locations: Map<String, SourceLocation>,
    private val documentLocations: Map<String, Map<String, SourceLocation>>,
    private val originalRefs: List<Map.Entry<`$Ref`, Any?>>,
    private val replacedRefs: List<Map.Entry<`$Ref`, Any?>>,
    private val originalRefsByObject: Map<Any, `$Ref`>,
    @JvmField val circular: Boolean = false,
) {
    @JvmField
    val jsonContext = `$JsonContext`(root)

    @Suppress("UNCHECKED_CAST")
    fun json(): Map<String, Any?> = schemaMap

    fun schema(): Any? = root

    fun getOriginalRef(value: Any?): `$Ref`? = if (value == null) null else originalRefsByObject[value]

    fun getOriginalRefsList(): List<Map.Entry<`$Ref`, Any?>> = originalRefs

    fun getReplacedRefsList(): List<Map.Entry<`$Ref`, Any?>> = replacedRefs

    fun getJsonLocationRange(jsonPath: String): Pair<JsonLocation, JsonLocation>? {
        val pointer = jsonPathToPointer(jsonPath) ?: return null
        val location = locations[pointer] ?: return null
        return Pair(
            JsonLocation(location.file, location.line, location.column),
            JsonLocation(location.file, location.endLine, location.endColumn),
        )
    }

    fun getJsonLocationRange(fileUri: URI, jsonPath: String): Pair<JsonLocation, JsonLocation>? {
        val pointer = jsonPathToPointer(jsonPath) ?: return null
        val normalizedFileUri = RefParser.normalizeUri(fileUri.toString())
        val location = documentLocations[normalizedFileUri]?.get(pointer)
            ?: documentLocations[fileUri.toString()]?.get(pointer)
            ?: return null
        return Pair(
            JsonLocation(location.file, location.line, location.column),
            JsonLocation(location.file, location.endLine, location.endColumn),
        )
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun from(
            document: ParsedDocument,
            root: Any? = document.schema,
        ): `$Refs` {
            val originalRefs = mutableListOf<Map.Entry<`$Ref`, Any?>>()
            val replacedRefs = mutableListOf<Map.Entry<`$Ref`, Any?>>()
            val refsByObject = IdentityHashMap<Any, `$Ref`>()
            document.resolvedRefs.forEach { resolvedRef ->
                val ref = `$Ref`(resolvedRef.refString, resolvedRef.targetUri ?: resolvedRef.refString)
                resolvedRef.resolvedTo?.let { refsByObject[it] = ref }
                resolvedRef.replacedValue?.let { refsByObject[it] = ref }
                if (shouldExposeInRefLists(ref, resolvedRef)) {
                    originalRefs.add(AbstractMap.SimpleEntry(ref, resolvedRef.resolvedTo))
                    replacedRefs.add(AbstractMap.SimpleEntry(ref, resolvedRef.replacedValue))
                }
            }
            return `$Refs`(root, document.schema, document.locations, document.documentLocations, originalRefs, replacedRefs, refsByObject, document.hasCircularRefs)
        }

        /**
         * Keep `getOriginalRef(value)` rich for compatibility, but avoid exposing
         * low-level AsyncAPI trait/binding helper refs in the public ref lists.
         *
         * Older JVM consumers such as zenwave-sdk expect `getOriginalRefsList()`
         * to mainly contain schema/message/channel-like refs they can annotate,
         * and they iterate the returned values assuming map entries are nested
         * objects rather than internal metadata helpers.
         */
        private fun shouldExposeInRefLists(ref: `$Ref`, resolvedRef: ResolvedRef): Boolean {
            val value = ref.getRef()
            val sourcePointer = resolvedRef.sourcePointer.orEmpty()
            return !sourcePointer.contains("/traits/") &&
                !value.contains("/components/messageTraits/") &&
                !value.contains("/components/operationTraits/") &&
                !value.contains("/components/channelBindings/") &&
                !value.contains("/components/x-error-topics/")
        }

        private fun jsonPathToPointer(jsonPath: String): String? {
            if (!jsonPath.startsWith("$")) return null
            if (jsonPath == "$") return ""
            val tokens = mutableListOf<String>()
            var i = 1
            while (i < jsonPath.length) {
                when (jsonPath[i]) {
                    '.' -> {
                        i++
                        val start = i
                        while (i < jsonPath.length && jsonPath[i] != '.' && jsonPath[i] != '[') i++
                        if (start == i) return null
                        tokens += jsonPath.substring(start, i)
                    }
                    '[' -> {
                        val end = jsonPath.indexOf(']', i)
                        if (end < 0) return null
                        val token = jsonPath.substring(i + 1, end).trim('\'', '"')
                        tokens += token
                        i = end + 1
                    }
                    else -> return null
                }
            }
            return if (tokens.isEmpty()) "" else "/" + tokens.joinToString("/") {
                it.replace("~", "~0").replace("/", "~1")
            }
        }
    }
}

data class JsonLocation(
    val file: String,
    val lineNr: Int,
    val columnNr: Int,
)

class `$RefParser`(
    private val uri: String,
) {
    private var resourceClassLoader: ClassLoader? = null
    private var compatibilityOptions = `$RefParserOptions`()
    private var authentication: List<io.zenwave360.jsonrefparser.model.AuthenticationValue> = emptyList()
    private var document: ParsedDocument? = null
    private var rootValue: Any? = linkedMapOf<String, Any?>()
    private var coreParser: RefParser? = null
    private var dereferenced = false

    constructor(uri: URI) : this(uri.toString())

    constructor(file: File) : this(file.toURI().toString())

    private fun normalizedCompatibilityUri(): String {
        if (uri.startsWith("classpath:")) {
            return if (uri.startsWith("classpath:/")) uri else uri.replaceFirst("classpath:", "classpath:/")
        }
        val parsed = runCatching { URI(uri) }.getOrNull()
        return if (parsed == null || parsed.scheme == null || parsed.scheme.length == 1) {
            File(uri).toURI().toString()
        } else {
            uri
        }
    }

    fun withOptions(options: `$RefParserOptions`): `$RefParser` {
        compatibilityOptions = options
        coreParser = null
        dereferenced = false
        return this
    }

    fun withResourceClassLoader(classLoader: ClassLoader?): `$RefParser` {
        resourceClassLoader = classLoader
        coreParser = null
        dereferenced = false
        return this
    }

    fun withAuthentication(vararg authentication: AuthenticationValue): `$RefParser` {
        this.authentication = authentication.map { it.toModel() }
        coreParser = null
        dereferenced = false
        return this
    }

    fun withAuthentication(authentication: List<*>): `$RefParser` {
        this.authentication = authentication.map { value ->
            when (value) {
                is AuthenticationValue -> value.toModel()
                is io.zenwave360.jsonrefparser.model.AuthenticationValue -> value
                else -> error("Unsupported AuthenticationValue type: ${value?.javaClass?.name}")
            }
        }
        coreParser = null
        dereferenced = false
        return this
    }

    private fun getOrCreateCoreParser(): RefParser =
        coreParser ?: RefParser(normalizedCompatibilityUri(), compatibilityOptions.toCore(), authentication, buildLoaders())
            .also { coreParser = it }

    fun parse(): `$RefParser` {
        syncFrom(getOrCreateCoreParser().parseBlocking())
        return this
    }

    fun dereference(): `$RefParser` {
        if (!dereferenced) {
            syncFrom(getOrCreateCoreParser().dereferenceBlocking())
            dereferenced = true
        }
        return this
    }

    fun mergeAllOf(): `$RefParser` {
        val parser = getOrCreateCoreParser()
        if (!dereferenced) {
            parser.dereferenceBlocking()
            dereferenced = true
        }
        syncFrom(parser.mergeAllOfBlocking())
        return this
    }

    fun getRefs(): `$Refs` {
        val parsed = document ?: ParsedDocument(
            schema = emptyMap(),
            locations = emptyMap(),
            documentLocations = emptyMap(),
            resolvedRefs = emptyList(),
            originalAllOfs = emptyList(),
            hasCircularRefs = false,
        )
        return `$Refs`.from(parsed, rootValue)
    }

    private fun syncFrom(parser: RefParser) {
        val parsed = parser.getParsedDocument()
        document = parsed
        rootValue = parser.rawRoot ?: parsed.schema
    }

    private fun buildLoaders(): List<DocumentLoader> = listOf(
        ClasspathLoader(resourceClassLoader ?: Thread.currentThread().contextClassLoader),
        FileLoader(),
        HttpLoader(authentication),
    )
}
