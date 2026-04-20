package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.model.CircularReferenceException
import io.zenwave360.jsonrefparser.model.OnCircular
import io.zenwave360.jsonrefparser.model.OnMissing
import io.zenwave360.jsonrefparser.model.RefParserOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CircularAndMissingRefTest {

    @Test
    fun circularRefWithResolveDoesNotThrow() = runTest {
        val text = readTestFile("recursive/recursive-simplest.yml")
        val doc = RefParser.fromText(text, testResourceUri("recursive/recursive-simplest.yml"))
            .withOptions(RefParserOptions(onCircular = OnCircular.RESOLVE))
            .dereference()
            .getParsedDocument()

        assertNotNull(doc.schema)
    }

    @Test
    fun circularRefWithSkipDoesNotThrow() = runTest {
        val text = readTestFile("recursive/recursive-simplest.yml")
        val doc = RefParser.fromText(text, testResourceUri("recursive/recursive-simplest.yml"))
            .withOptions(RefParserOptions(onCircular = OnCircular.SKIP))
            .dereference()
            .getParsedDocument()

        assertNotNull(doc.schema)
        assertTrue(containsRef(doc.schema), "circular ref should remain unresolved when SKIP is used")
        assertAcyclic(doc.schema)
    }

    @Test
    fun circularRefWithFailThrowsCircularReferenceException() = runTest {
        val text = readTestFile("recursive/recursive-simplest.yml")
        assertFailsWith<CircularReferenceException> {
            RefParser.fromText(text, testResourceUri("recursive/recursive-simplest.yml"))
                .withOptions(RefParserOptions(onCircular = OnCircular.FAIL))
                .dereference()
        }
    }

    @Test
    fun missingRefWithFailThrowsException() = runTest {
        val yaml = """
            schema:
              ${'$'}ref: 'does-not-exist.yaml'
        """.trimIndent()
        assertFailsWith<Exception> {
            RefParser.fromText(yaml)
                .withOptions(RefParserOptions(onMissing = OnMissing.FAIL))
                .dereference()
        }
    }

    @Test
    fun missingRefWithSkipLeavesRefInPlace() = runTest {
        val yaml = """
            schema:
              ${'$'}ref: 'does-not-exist.yaml'
        """.trimIndent()
        val doc = RefParser.fromText(yaml)
            .withOptions(RefParserOptions(onMissing = OnMissing.SKIP))
            .dereference()
            .getParsedDocument()

        @Suppress("UNCHECKED_CAST")
        val schema = doc.schema["schema"] as Map<*, *>
        assertTrue(schema.containsKey("\$ref"), "\$ref should remain when SKIP and missing")
    }
}
