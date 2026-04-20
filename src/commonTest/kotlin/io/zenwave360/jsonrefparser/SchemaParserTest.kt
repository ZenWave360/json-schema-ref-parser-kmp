package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.parser.parseText
import io.zenwave360.jsonrefparser.parser.scalarValue
import io.zenwave360.jsonrefparser.resolver.SchemaRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaParserTest {

    // -----------------------------------------------------------------------
    // YAML / JSON text parsing
    // -----------------------------------------------------------------------

    @Test
    fun parseSimpleYamlMap() {
        val yaml = """
            type: object
            title: MySchema
        """.trimIndent()
        val doc = parseText(yaml, "file:///test.yaml")
        assertEquals("object", doc.map["type"])
        assertEquals("MySchema", doc.map["title"])
    }

    @Test
    fun parseJsonYamlSuperset() {
        val json = """{"type":"string","minLength":1}"""
        val doc = parseText(json, "file:///test.json")
        assertEquals("string", doc.map["type"])
        assertEquals(1L, doc.map["minLength"])
    }

    @Test
    fun parseNestedMap() {
        val yaml = """
            components:
              schemas:
                Foo:
                  type: string
        """.trimIndent()
        val doc = parseText(yaml, "file:///test.yaml")
        @Suppress("UNCHECKED_CAST")
        val foo = (doc.map["components"] as Map<*, *>)
            .let { it["schemas"] as Map<*, *> }
            .let { it["Foo"] as Map<*, *> }
        assertEquals("string", foo["type"])
    }

    @Test
    fun parseSequence() {
        val yaml = """
            required:
              - name
              - age
        """.trimIndent()
        val doc = parseText(yaml, "file:///test.yaml")
        val required = doc.map["required"] as List<*>
        assertEquals(listOf("name", "age"), required)
    }

    @Test
    fun scalarTypesIntegerBecomesLong() {
        val yaml = "count: 42"
        val doc = parseText(yaml, "file:///t.yaml")
        assertEquals(42L, doc.map["count"])
    }

    @Test
    fun scalarTypesFloatBecomesDouble() {
        val yaml = "rate: 3.14"
        val doc = parseText(yaml, "file:///t.yaml")
        assertEquals(3.14, doc.map["rate"])
    }

    @Test
    fun scalarTypesBoolean() {
        val yaml = "enabled: true\ndisabled: false"
        val doc = parseText(yaml, "file:///t.yaml")
        assertEquals(true, doc.map["enabled"])
        assertEquals(false, doc.map["disabled"])
    }

    @Test
    fun scalarTypesNull() {
        val yaml = "value: null"
        val doc = parseText(yaml, "file:///t.yaml")
        assertNull(doc.map["value"])
    }

    @Test
    fun coreSchemaYesAndNoAreStringsNotBooleans() {
        // YAML 1.2 Core Schema: only true/false are booleans
        val yaml = "a: yes\nb: no\nc: on\nd: off"
        val doc = parseText(yaml, "file:///t.yaml")
        assertEquals("yes", doc.map["a"])
        assertEquals("no", doc.map["b"])
        assertEquals("on", doc.map["c"])
        assertEquals("off", doc.map["d"])
    }

    // -----------------------------------------------------------------------
    // Source location index
    // -----------------------------------------------------------------------

    @Test
    fun rootNodeHasSourceLocation() {
        val yaml = "type: string"
        val doc = parseText(yaml, "file:///test.yaml")
        assertNotNull(doc.locations[""])   // ROOT pointer is ""
    }

    @Test
    fun everyScalarNodeHasALocationEntry() {
        val yaml = """
            type: object
            properties:
              name:
                type: string
        """.trimIndent()
        val doc = parseText(yaml, "file:///test.yaml")
        assertNotNull(doc.locations["/type"])
        assertNotNull(doc.locations["/properties"])
        assertNotNull(doc.locations["/properties/name"])
        assertNotNull(doc.locations["/properties/name/type"])
    }

    @Test
    fun sourceLocationFileMatchesTheSuppliedUri() {
        val uri = "file:///schemas/my.yaml"
        val doc = parseText("type: string", uri)
        assertTrue(doc.locations.values.all { it.file == uri })
    }

    @Test
    fun sequenceItemLocationsAreIndexedByNumber() {
        val yaml = "items:\n  - name: a\n  - name: b"
        val doc = parseText(yaml, "file:///t.yaml")
        assertNotNull(doc.locations["/items/0"])
        assertNotNull(doc.locations["/items/1"])
    }

    // -----------------------------------------------------------------------
    // AVSC (plain JSON)
    // -----------------------------------------------------------------------

    @Test
    fun avscParsesAsPlainJson() {
        val avsc = """{
            "type": "record",
            "name": "CartLine",
            "fields": [
                {"name": "productId", "type": "string"}
            ]
        }"""
        val doc = parseText(avsc, "file:///cart.avsc")
        assertEquals("record", doc.map["type"])
        assertEquals("CartLine", doc.map["name"])
        val fields = doc.map["fields"] as List<*>
        assertEquals(1, fields.size)
    }

    // -----------------------------------------------------------------------
    // URI resolution helper
    // -----------------------------------------------------------------------

    @Test
    fun resolveUriHandlesRelativeSameDirectoryFile() {
        val base = "file:///home/user/api/root.yaml"
        val result = SchemaRef.resolveUri(base, "./other.yaml")
        assertEquals("file:///home/user/api/other.yaml", result)
    }

    @Test
    fun resolveUriHandlesParentDirectoryTraversal() {
        val base = "file:///home/user/api/root.yaml"
        val result = SchemaRef.resolveUri(base, "../common/types.yaml")
        assertEquals("file:///home/user/common/types.yaml", result)
    }

    @Test
    fun resolveUriHandlesBareFilenameNoDotPrefix() {
        val base = "file:///home/user/api/root.yaml"
        // 'other.yaml' is munged to './other.yaml' by SchemaRef.parse
        val result = SchemaRef.resolveUri(base, "./other.yaml")
        assertEquals("file:///home/user/api/other.yaml", result)
    }
}
