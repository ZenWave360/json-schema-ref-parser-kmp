package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.jsonrefparser.model.AuthenticationType
import io.zenwave360.jsonrefparser.model.AuthenticationValue
import io.zenwave360.jsonrefparser.model.OnCircular
import io.zenwave360.jsonrefparser.model.OnMissing
import io.zenwave360.jsonrefparser.model.RefParserOptions
import io.zenwave360.jsonrefparser.resolver.SchemaRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RefParserBranchCoverageTest {

    @Test
    fun withOptionsReturnsANewParserWithoutMutatingOriginal() {
        val original = RefParser("memory://schema.yml")
        val updated = original.withOptions(RefParserOptions(OnCircular.SKIP, OnMissing.SKIP))

        assertNotSame(original, updated)
        assertEquals("memory://schema.yml", original.uri)
        assertEquals("memory://schema.yml", updated.uri)
    }

    @Test
    fun withAuthenticationReturnsANewParserWithoutMutatingOriginal() {
        val original = RefParser("memory://schema.yml")
        val auth = AuthenticationValue("Authorization", "Bearer token")

        val updated = original.withAuthentication(auth)

        assertNotSame(original, updated)
        assertEquals("memory://schema.yml", updated.uri)
    }

    @Test
    fun dereferenceImplicitlyParsesWhenParseWasNotCalled() = runTest {
        val yaml = """
            definitions:
              Foo:
                type: string
            schema:
              ${'$'}ref: '#/definitions/Foo'
        """.trimIndent()

        val doc = RefParser.fromText(yaml)
            .dereference()
            .getParsedDocument()

        assertEquals("string", ((doc.schema["schema"] as Map<*, *>)["type"]))
        assertTrue(doc.locations.isNotEmpty())
    }

    @Test
    fun dereferenceReusesParsedStateWhenParseWasAlreadyCalled() = runTest {
        val yaml = """
            definitions:
              Foo:
                type: string
            schema:
              ${'$'}ref: '#/definitions/Foo'
        """.trimIndent()

        val parser = RefParser.fromText(yaml).parse()
        val doc = parser
            .dereference()
            .getParsedDocument()

        assertEquals("string", ((doc.schema["schema"] as Map<*, *>)["type"]))
    }

    @Test
    fun fromTextUsesDefaultBaseUriWhenNotProvided() = runTest {
        val doc = RefParser.fromText("type: object")
            .parse()
            .getParsedDocument()

        assertEquals("memory://anonymous", doc.locations.getValue("").file)
    }

    @Test
    fun parseFailsWhenNoLoaderCanHandleTheUri() = runTest {
        val loader = object : DocumentLoader {
            override fun canLoad(uri: String): Boolean = false
            override suspend fun load(uri: String): String = error("should not be called")
        }

        val parser = RefParser("memory://schema.yml", loaders = listOf(loader))

        assertFailsWith<IllegalStateException> {
            parser.parse()
        }
    }

    @Test
    fun withDefaultLoadersPreservesInMemoryRootLoaderFromFromText() = runTest {
        val extraLoader = object : DocumentLoader {
            override fun canLoad(uri: String): Boolean = false
            override suspend fun load(uri: String): String = error("should not be called")
        }

        val doc = RefParser.fromText("type: object")
            .withDefaultLoaders(extraLoader)
            .parse()
            .getParsedDocument()

        assertEquals("object", doc.schema["type"])
    }

    @Test
    fun authenticationValuePrefersUrlMatcherWhenPresent() {
        val auth = AuthenticationValue(
            key = "Authorization",
            value = "Bearer token",
            urlMatcher = { it.contains("allowed.example") },
            urlPatterns = listOf(".*denied.*"),
        )

        assertTrue(auth.matches("https://allowed.example/schema.yml"))
        assertFalse(auth.matches("https://denied.example/schema.yml"))
    }

    @Test
    fun authenticationValueFallsBackToRegexPatterns() {
        val auth = AuthenticationValue(
            key = "token",
            value = "secret",
            type = AuthenticationType.QUERY,
            urlPatterns = listOf(".*example.com.*"),
        )

        assertTrue(auth.matches("https://api.example.com/schema.yml"))
        assertFalse(auth.matches("https://api.other.com/schema.yml"))
    }

    @Test
    fun authenticationValueWithoutMatcherOrPatternsMatchesAnyUrl() {
        val auth = AuthenticationValue(
            key = "token",
            value = "secret",
            urlPatterns = emptyList(),
        )

        assertTrue(auth.matches("https://any.example/schema.yml"))
    }

    @Test
    fun schemaRefParseRecognizesSpecialCases() {
        val internalRoot = SchemaRef.parse("#", "file:///tmp/root.yml")
        assertEquals("#/", internalRoot.pointer)
        assertEquals(null, internalRoot.fileUri)

        val classpathRef = SchemaRef.parse("classpath:/schemas/common.yml#/Pet", "file:///tmp/root.yml")
        assertEquals("classpath:/schemas/common.yml", classpathRef.fileUri)
        assertEquals("#/Pet", classpathRef.pointer)

        val bareFilename = SchemaRef.parse("other.yml", "file:///home/user/api/root.yml")
        assertEquals("file:///home/user/api/other.yml", bareFilename.fileUri)
    }

    @Test
    fun schemaRefParseRecognizesAbsoluteHttpUriAndInternalFragment() {
        val urlRef = SchemaRef.parse("https://example.com/common.yml#/Pet", "file:///tmp/root.yml")
        assertEquals("https://example.com/common.yml", urlRef.fileUri)
        assertEquals("#/Pet", urlRef.pointer)
    }

    @Test
    fun schemaRefResolveUriReturnsBaseForEmptyRelativeUri() {
        assertEquals("file:///home/user/root.yml", SchemaRef.resolveUri("file:///home/user/root.yml", ""))
    }

    @Test
    fun normalizeUriHandlesWindowsDriveLetters() {
        assertEquals("file:///C:/work/schema.yml", RefParser.normalizeUri("C:\\work\\schema.yml"))
    }
}
