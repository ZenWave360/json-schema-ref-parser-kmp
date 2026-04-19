package io.zenwave360.jsonrefparser

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergeAllOfTest {

    @Test
    fun `mergeAllOf removes allOf key`() = runTest {
        val text = readTestFile("asyncapi/multiple-allOf.yml")
        val doc = RefParser.fromText(text, testResourceUri("asyncapi/multiple-allOf.yml"))
            .dereference()
            .mergeAllOf()
            .getParsedDocument()

        fun hasAllOf(obj: Any?): Boolean = when (obj) {
            is Map<*, *> -> obj.containsKey("allOf") || obj.values.any { hasAllOf(it) }
            is List<*>   -> obj.any { hasAllOf(it) }
            else         -> false
        }
        assertFalse(hasAllOf(doc.schema), "No allOf keys should remain after mergeAllOf")
    }

    @Test
    fun `mergeAllOf merges properties from all allOf entries`() = runTest {
        val text = readTestFile("asyncapi/multiple-allOf.yml")
        val doc = RefParser.fromText(text, testResourceUri("asyncapi/multiple-allOf.yml"))
            .dereference()
            .mergeAllOf()
            .getParsedDocument()

        @Suppress("UNCHECKED_CAST")
        val schemas = (doc.schema["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>
        val test = schemas["Test"] as Map<*, *>
        val properties = test["properties"] as Map<*, *>

        assertTrue(properties.containsKey("test1a"))
        assertTrue(properties.containsKey("test1b"))
        assertTrue(properties.containsKey("test2a"))
        assertTrue(properties.containsKey("test2b"))
    }

    @Test
    fun `mergeAllOf at root level`() = runTest {
        val text = readTestFile("gh-32-mergeAllOf-root.yml")
        val doc = RefParser.fromText(text, testResourceUri("gh-32-mergeAllOf-root.yml"))
            .dereference()
            .mergeAllOf()
            .getParsedDocument()

        assertFalse(doc.schema.containsKey("allOf"), "root allOf should be removed")
        assertTrue(doc.schema.containsKey("properties"), "merged properties should be at root")
    }

    @Test
    fun `mergeAllOf concatenates required arrays`() = runTest {
        val yaml = """
            allOf:
              - type: object
                required: [id]
                properties:
                  id: {type: string}
              - type: object
                required: [name]
                properties:
                  name: {type: string}
        """.trimIndent()
        val doc = RefParser.fromText(yaml).dereference().mergeAllOf().getParsedDocument()

        val required = doc.schema["required"] as List<*>
        assertTrue(required.contains("id"))
        assertTrue(required.contains("name"))
    }
}
