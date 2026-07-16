package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.io.ClasspathLoader
import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.jsonrefparser.model.AuthenticationValue as CoreAuthenticationValue
import io.zenwave360.jsonrefparser.model.ParsedDocument
import io.zenwave360.jsonrefparser.model.RefParserOptions
import java.io.File
import java.net.URI

/**
 * JVM-only blocking facade for Java and Kotlin/JVM callers.
 *
 * This is a thin wrapper over [RefParser] that exposes a fluent blocking surface
 * without requiring coroutine interop helpers.
 */
class JavaRefParser internal constructor(
    private val refParser: RefParser,
) {

    fun withOptions(options: RefParserOptions): JavaRefParser =
        JavaRefParser(refParser.withOptions(options))

    fun withAuthentication(vararg auth: AuthenticationValue): JavaRefParser =
        JavaRefParser(refParser.withAuthentication(*auth.map { it.toModel() }.toTypedArray()))

    fun withAuthenticationValues(vararg auth: CoreAuthenticationValue): JavaRefParser =
        JavaRefParser(refParser.withAuthentication(*auth))

    fun withLoaders(vararg loaders: DocumentLoader): JavaRefParser =
        JavaRefParser(refParser.withLoaders(*loaders))

    fun withLoaders(loaders: List<DocumentLoader>): JavaRefParser =
        JavaRefParser(refParser.withLoaders(loaders))

    fun withDefaultLoaders(vararg loaders: DocumentLoader): JavaRefParser =
        JavaRefParser(refParser.withDefaultLoaders(*loaders))

    fun withDefaultLoaders(loaders: List<DocumentLoader>): JavaRefParser =
        JavaRefParser(refParser.withDefaultLoaders(loaders))

    fun withResourceClassLoader(classLoader: ClassLoader?): JavaRefParser =
        JavaRefParser(refParser.withDefaultLoaders(ClasspathLoader(classLoader)))

    fun parse(): JavaRefParser {
        refParser.parseBlocking()
        return this
    }

    fun dereference(): JavaRefParser {
        refParser.dereferenceBlocking()
        return this
    }

    fun mergeAllOf(): JavaRefParser {
        refParser.mergeAllOfBlocking()
        return this
    }

    fun getRoot(): Any? = refParser.getRoot()

    fun getParsedDocument(): ParsedDocument = refParser.getParsedDocument()

    internal fun toRefParser(): RefParser = refParser

    companion object {
        @JvmStatic
        fun from(file: File): JavaRefParser = JavaRefParser(RefParser(file.toURI().toString()))

        @JvmStatic
        fun from(uri: URI): JavaRefParser = JavaRefParser(RefParser(uri.toString()))

        @JvmStatic
        fun from(uri: String): JavaRefParser = JavaRefParser(RefParser(uri))

        @JvmStatic
        @JvmOverloads
        fun fromText(text: String, baseUri: String = "memory://anonymous"): JavaRefParser =
            JavaRefParser(RefParser.fromText(text, baseUri))
    }
}
