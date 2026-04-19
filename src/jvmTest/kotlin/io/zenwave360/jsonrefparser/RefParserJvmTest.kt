package io.zenwave360.jsonrefparser

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-specific tests: classpath loading and blocking extension functions.
 */
class RefParserJvmTest {

    @Test
    fun `classpath loader resolves schema`() = runTest {
        val doc = RefParser("classpath:openapi/openapi-petstore.yml")
            .parse()
            .getParsedDocument()

        assertNotNull(doc.schema)
        assertTrue(doc.locations.isNotEmpty())
    }

    @Test
    fun `parseBlocking works on JVM`() {
        val doc = RefParser("classpath:asyncapi/multiple-allOf.yml")
            .parseBlocking()
            .getParsedDocument()

        assertNotNull(doc.schema["asyncapi"])
    }

    @Test
    fun `dereferenceBlocking works on JVM`() {
        val doc = RefParser("classpath:asyncapi/multiple-allOf.yml")
            .dereferenceBlocking()
            .getParsedDocument()

        assertNotNull(doc.schema)
    }
}
