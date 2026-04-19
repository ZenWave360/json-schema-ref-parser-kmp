package io.zenwave360.jsonrefparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonPointerTest {

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    @Test
    fun `parse empty string returns ROOT`() {
        val p = JsonPointer.parse("")
        assertEquals(emptyList(), p.tokens)
        assertEquals("", p.toString())
    }

    @Test
    fun `parse simple pointer`() {
        val p = JsonPointer.parse("/foo/bar")
        assertEquals(listOf("foo", "bar"), p.tokens)
        assertEquals("/foo/bar", p.toString())
    }

    @Test
    fun `parse pointer with tilde escapes`() {
        val p = JsonPointer.parse("/a~1b/c~0d")
        assertEquals(listOf("a/b", "c~d"), p.tokens)
    }

    @Test
    fun `parse fragment form`() {
        val p = JsonPointer.parseFragment("#/components/schemas/Foo")
        assertEquals(listOf("components", "schemas", "Foo"), p.tokens)
    }

    @Test
    fun `parse fragment with only hash`() {
        val p = JsonPointer.parseFragment("#")
        assertEquals(JsonPointer.ROOT, p)
    }

    // -----------------------------------------------------------------------
    // toString
    // -----------------------------------------------------------------------

    @Test
    fun `toString round-trips parsing`() {
        val input = "/components/schemas/Foo"
        assertEquals(input, JsonPointer.parse(input).toString())
    }

    @Test
    fun `child with key escapes special chars`() {
        val p = JsonPointer.ROOT.child("a/b").child("c~d")
        assertEquals("/a~1b/c~0d", p.toString())
    }

    @Test
    fun `child with index`() {
        val p = JsonPointer.ROOT.child("items").child(2)
        assertEquals("/items/2", p.toString())
    }

    // -----------------------------------------------------------------------
    // Resolve
    // -----------------------------------------------------------------------

    @Test
    fun `resolve navigates nested map`() {
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
    fun `resolve navigates list by index`() {
        val doc = mapOf("items" to listOf("a", "b", "c"))
        assertEquals("b", JsonPointer.parse("/items/1").resolve(doc))
    }

    @Test
    fun `resolve returns null for missing key`() {
        val doc = mapOf("foo" to "bar")
        assertNull(JsonPointer.parse("/missing").resolve(doc))
    }

    @Test
    fun `ROOT resolve returns document`() {
        val doc = mapOf("x" to 1)
        assertEquals(doc, JsonPointer.ROOT.resolve(doc))
    }
}
