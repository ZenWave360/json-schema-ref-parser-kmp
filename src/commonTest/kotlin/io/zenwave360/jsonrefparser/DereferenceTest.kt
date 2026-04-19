package io.zenwave360.jsonrefparser

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DereferenceTest {

    @Test
    fun `dereference resolves internal $ref`() = runTest {
        val yaml = """
            definitions:
              Foo:
                type: string
            schema:
              ${'$'}ref: '#/definitions/Foo'
        """.trimIndent()
        val doc = RefParser.fromText(yaml).dereference().getParsedDocument()

        @Suppress("UNCHECKED_CAST")
        val schema = doc.schema["schema"] as Map<String, Any?>
        assertFalse(schema.containsKey("\$ref"), "\$ref should be resolved")
        assertEquals("string", schema["type"])
    }

    @Test
    fun `dereference resolves $ref in asyncapi file`() = runTest {
        val text = readTestFile("asyncapi/multiple-allOf.yml")
        val doc = RefParser.fromText(text, testResourceUri("asyncapi/multiple-allOf.yml"))
            .dereference()
            .getParsedDocument()

        @Suppress("UNCHECKED_CAST")
        val channels = doc.schema["channels"] as Map<String, Any?>
        val testTopic = channels["testTopic"] as Map<*, *>
        val messages = testTopic["messages"] as Map<*, *>
        val test = messages["test"] as Map<*, *>
        val payload = test["payload"] as Map<*, *>

        assertFalse(payload.containsKey("\$ref"), "payload should be dereferenced")
    }

    @Test
    fun `dereference resolves cross-file $ref (GH-36)`() = runTest {
        val uri = testResourceUri("GH-36/root.json")
        val doc = RefParser(uri).dereference().getParsedDocument()

        @Suppress("UNCHECKED_CAST")
        val allOf = doc.schema["allOf"] as? List<*>
        assertNotNull(allOf)
        val first = allOf.first() as Map<*, *>
        assertFalse(first.containsKey("\$ref"), "cross-file ref should be resolved")
        assertTrue(first.containsKey("properties") || first.containsKey("type"))
    }

    @Test
    fun `source location for external ref points to origin file`() = runTest {
        val uri = testResourceUri("GH-36/root.json")
        val doc = RefParser(uri).dereference().getParsedDocument()

        val commonUri = testResourceUri("GH-36/common.schema.json")
        val externalLocations = doc.locations.values.filter { it.file == commonUri }
        assertTrue(externalLocations.isNotEmpty(),
            "Dereferenced external nodes should retain origin file URI in SourceLocation")
    }

    @Test
    fun `dereference multi-file AVSC schema`() = runTest {
        val uri = testResourceUri("asyncapi/shoping-cart-multiple-files/shoping-cart-multiple-files.yml")
        val doc = RefParser(uri).dereference().getParsedDocument()

        @Suppress("UNCHECKED_CAST")
        val components = doc.schema["components"] as Map<String, Any?>
        val messages = components["messages"] as Map<*, *>
        val addMsg = messages["cart.lines.add"] as Map<*, *>
        assertFalse(addMsg.containsKey("\$ref"), "cart.lines.add should be dereferenced")
    }

    @Test
    fun `same $ref target returns same object instance`() = runTest {
        val yaml = """
            definitions:
              Shared:
                type: object
                properties:
                  id: {type: string}
            A:
              ${'$'}ref: '#/definitions/Shared'
            B:
              ${'$'}ref: '#/definitions/Shared'
        """.trimIndent()
        val doc = RefParser.fromText(yaml).dereference().getParsedDocument()

        val a = doc.schema["A"]
        val b = doc.schema["B"]

        assertNotNull(a)
        assertNotNull(b)
        assertSame(a, b, "same \$ref target must yield the same object instance")
    }

    @Test
    fun `ref with sibling keys merges correctly resolved wins`() = runTest {
        val yaml = """
            definitions:
              Base:
                type: object
                properties:
                  id: {type: string}
                description: "Base description"
            Schema:
              ${'$'}ref: '#/definitions/Base'
              description: "Overridden"
        """.trimIndent()
        val doc = RefParser.fromText(yaml).dereference().getParsedDocument()

        @Suppress("UNCHECKED_CAST")
        val schema = doc.schema["Schema"] as Map<String, Any?>
        assertNotNull(schema["properties"])
        assertEquals("Base description", schema["description"])
    }
}
