package io.zenwave360.jsonrefparser

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserBasicTest {

    @Test
    fun `parse inline YAML`() = runTest {
        val yaml = """
            type: object
            properties:
              name:
                type: string
        """.trimIndent()
        val doc = RefParser.fromText(yaml).parse().getParsedDocument()

        assertEquals("object", doc.schema["type"])
        @Suppress("UNCHECKED_CAST")
        val props = doc.schema["properties"] as Map<String, Any?>
        assertTrue(props.containsKey("name"))
        assertTrue(doc.locations.isNotEmpty())
    }

    @Test
    fun `parse JSON file via readTestFile`() = runTest {
        val text = readTestFile("GH-18.json")
        val doc = RefParser.fromText(text, "memory://GH-18.json").parse().getParsedDocument()

        assertEquals("myDummySchema", doc.schema["\$id"])
        assertNotNull(doc.locations[""])
        assertNotNull(doc.locations["/properties"])
    }

    @Test
    fun `parse YAML file via readTestFile`() = runTest {
        val text = readTestFile("asyncapi/multiple-allOf.yml")
        val doc = RefParser.fromText(text, "memory://multiple-allOf.yml").parse().getParsedDocument()

        assertNotNull(doc.schema["asyncapi"])
        assertTrue(doc.locations.isNotEmpty())
    }

    @Test
    fun `parse AVSC file via readTestFile`() = runTest {
        val text = readTestFile("asyncapi/shoping-cart-avro-array/all_cart_entities.avsc")
        val doc = RefParser.fromText(text).parse().getParsedDocument()

        assertNotNull(doc.schema)
        assertTrue(doc.locations.isNotEmpty())
    }

    @Test
    fun `every map node has a source location`() = runTest {
        val text = readTestFile("openapi/openapi-petstore.yml")
        val doc = RefParser.fromText(text).parse().getParsedDocument()

        assertNotNull(doc.locations[""])
        assertTrue(doc.locations.size > 5)
    }

    @Test
    fun `source location lines are 0-based`() = runTest {
        val yaml = "type: object\ntitle: Foo"
        val doc = RefParser.fromText(yaml).parse().getParsedDocument()

        val rootLoc = doc.locations[""]
        assertNotNull(rootLoc)
        assertEquals(0, rootLoc.line)
    }
}
