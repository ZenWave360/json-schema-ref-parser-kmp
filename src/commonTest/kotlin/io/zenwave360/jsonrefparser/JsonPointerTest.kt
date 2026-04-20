package io.zenwave360.jsonrefparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonPointerTest {

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    @Test
    fun parseEmptyStringReturnsRoot() {
        val p = JsonPointer.parse("")
        assertEquals(emptyList(), p.tokens)
        assertEquals("", p.toString())
    }

    @Test
    fun parseSimplePointer() {
        val p = JsonPointer.parse("/foo/bar")
        assertEquals(listOf("foo", "bar"), p.tokens)
        assertEquals("/foo/bar", p.toString())
    }

    @Test
    fun parsePointerWithTildeEscapes() {
        val p = JsonPointer.parse("/a~1b/c~0d")
        assertEquals(listOf("a/b", "c~d"), p.tokens)
    }

    @Test
    fun parseFragmentForm() {
        val p = JsonPointer.parseFragment("#/components/schemas/Foo")
        assertEquals(listOf("components", "schemas", "Foo"), p.tokens)
    }

    @Test
    fun parseFragmentWithOnlyHash() {
        val p = JsonPointer.parseFragment("#")
        assertEquals(JsonPointer.ROOT, p)
    }

    // -----------------------------------------------------------------------
    // toString
    // -----------------------------------------------------------------------

    @Test
    fun toStringRoundTripsParsing() {
        val input = "/components/schemas/Foo"
        assertEquals(input, JsonPointer.parse(input).toString())
    }

    @Test
    fun childWithKeyEscapesSpecialChars() {
        val p = JsonPointer.ROOT.child("a/b").child("c~d")
        assertEquals("/a~1b/c~0d", p.toString())
    }

    @Test
    fun childWithIndex() {
        val p = JsonPointer.ROOT.child("items").child(2)
        assertEquals("/items/2", p.toString())
    }

    // -----------------------------------------------------------------------
    // Resolve
    // -----------------------------------------------------------------------

    @Test
    fun resolveNavigatesNestedMap() {
        val doc = mapOf(
            "components" to mapOf(
                "schemas" to mapOf(
                    "Foo" to mapOf("type" to "string")
                )
            )
        )
        val result = JsonPointer.parse("/components/schemas/Foo").resolve(doc)
        assertEquals(mapOf("type" to "string"), result)
    }

    @Test
    fun resolveNavigatesListByIndex() {
        val doc = mapOf("items" to listOf("a", "b", "c"))
        assertEquals("b", JsonPointer.parse("/items/1").resolve(doc))
    }

    @Test
    fun resolveReturnsNullForMissingKey() {
        val doc = mapOf("foo" to "bar")
        assertNull(JsonPointer.parse("/missing").resolve(doc))
    }

    @Test
    fun rootResolveReturnsDocument() {
        val doc = mapOf("x" to 1)
        assertEquals(doc, JsonPointer.ROOT.resolve(doc))
    }
}
