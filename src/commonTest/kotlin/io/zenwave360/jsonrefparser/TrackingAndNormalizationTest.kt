package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.model.getOriginalAllOf
import io.zenwave360.jsonrefparser.model.getOriginalRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrackingAndNormalizationTest {

    @Test
    fun `resolvedRefs is populated after dereference`() = runTest {
        val yaml = """
            definitions:
              Foo:
                type: string
            schema:
              ${'$'}ref: '#/definitions/Foo'
        """.trimIndent()
        val doc = RefParser.fromText(yaml).dereference().getParsedDocument()

        assertTrue(doc.resolvedRefs.isNotEmpty(), "resolvedRefs should be populated")
        val entry = doc.resolvedRefs.first()
        assertEquals("#/definitions/Foo", entry.refString)
        assertNotNull(entry.resolvedTo)
    }

    @Test
    fun `getOriginalRef returns the $ref for a resolved object`() = runTest {
        val yaml = """
            definitions:
              Foo:
                type: string
            schema:
              ${'$'}ref: '#/definitions/Foo'
        """.trimIndent()
        val doc = RefParser.fromText(yaml).dereference().getParsedDocument()

        @Suppress("UNCHECKED_CAST")
        val resolved = doc.schema["schema"] as? Map<String, Any?>
        assertNotNull(resolved)
        val ref = doc.getOriginalRef(resolved)
        assertNotNull(ref, "getOriginalRef should find the entry")
        assertEquals("#/definitions/Foo", ref.refString)
    }

    @Test
    fun `same $ref target appears in resolvedRefs for every occurrence`() = runTest {
        val yaml = """
            definitions:
              Shared: {type: string}
            A:
              ${'$'}ref: '#/definitions/Shared'
            B:
              ${'$'}ref: '#/definitions/Shared'
        """.trimIndent()
        val doc = RefParser.fromText(yaml).dereference().getParsedDocument()

        val count = doc.resolvedRefs.count { it.refString == "#/definitions/Shared" }
        assertEquals(2, count, "Both A and B refs should be recorded")
    }

    @Test
    fun `originalAllOfs is populated after mergeAllOf`() = runTest {
        val yaml = """
            allOf:
              - type: object
                properties:
                  id: {type: string}
              - type: object
                properties:
                  name: {type: string}
        """.trimIndent()
        val doc = RefParser.fromText(yaml).dereference().mergeAllOf().getParsedDocument()

        assertTrue(doc.originalAllOfs.isNotEmpty(), "originalAllOfs should be populated")
        val entry = doc.originalAllOfs.first()
        assertEquals(2, entry.allOfItems.size, "should record both allOf entries")
    }

    @Test
    fun `getOriginalAllOf returns items for a merged map`() = runTest {
        val yaml = """
            allOf:
              - type: object
                properties:
                  id: {type: string}
              - type: object
                properties:
                  name: {type: string}
        """.trimIndent()
        val doc = RefParser.fromText(yaml).dereference().mergeAllOf().getParsedDocument()

        val items = doc.getOriginalAllOf(doc.schema)
        assertNotNull(items, "getOriginalAllOf should find the root entry")
        assertEquals(2, items.size)
    }

    @Test
    fun `normalizeUri bare path gets file scheme`() {
        assertTrue(RefParser.normalizeUri("/home/user/schema.yaml").startsWith("file://"))
    }

    @Test
    fun `normalizeUri classpath without slash gets slash`() {
        assertEquals("classpath:/schemas/foo.yaml", RefParser.normalizeUri("classpath:schemas/foo.yaml"))
    }

    @Test
    fun `normalizeUri already-normalized URIs are unchanged`() {
        val uri = "file:///home/user/schema.yaml"
        assertEquals(uri, RefParser.normalizeUri(uri))
    }
}
