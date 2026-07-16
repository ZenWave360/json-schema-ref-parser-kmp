package io.zenwave360.jsonrefparser.merge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class JsonMergePatchTest {
    @Test
    fun appliesRfc7396AppendixAExamples() {
        val cases = listOf(
            Triple(mapOf("a" to "b"), mapOf("a" to "c"), mapOf("a" to "c")),
            Triple(mapOf("a" to "b"), mapOf("b" to "c"), mapOf("a" to "b", "b" to "c")),
            Triple(mapOf("a" to "b"), mapOf("a" to null), emptyMap<String, Any?>()),
            Triple(mapOf("a" to "b", "b" to "c"), mapOf("a" to null), mapOf("b" to "c")),
            Triple(listOf("a", "b"), listOf("c", "d"), listOf("c", "d")),
            Triple(mapOf("a" to "b"), listOf("c"), listOf("c")),
            Triple(mapOf("a" to listOf("b")), mapOf("a" to "c"), mapOf("a" to "c")),
            Triple(mapOf("a" to "c"), mapOf("a" to listOf("b")), mapOf("a" to listOf("b"))),
            Triple(mapOf("a" to mapOf("b" to "c")), mapOf("a" to mapOf("b" to "d", "c" to null)), mapOf("a" to mapOf("b" to "d"))),
            Triple(mapOf("a" to listOf(mapOf("b" to "c"))), mapOf("a" to listOf(1)), mapOf("a" to listOf(1))),
            Triple(listOf("a", "b"), mapOf("a" to "b", "c" to null), mapOf("a" to "b")),
            Triple(mapOf("a" to "foo"), null, null),
            Triple(mapOf("a" to "foo"), "bar", "bar"),
            Triple(mapOf("e" to null), mapOf("a" to 1), mapOf("e" to null, "a" to 1)),
            Triple(listOf(1, 2), mapOf("a" to "b", "c" to null), mapOf("a" to "b")),
            Triple(mapOf<String, Any?>(), mapOf("a" to mapOf("bb" to mapOf("ccc" to null))), mapOf("a" to mapOf("bb" to emptyMap<String, Any?>()))),
        )
        cases.forEach { (target, patch, expected) ->
            assertEquals(expected, JsonMergePatch.apply(target, patch))
        }
    }

    @Test
    fun doesNotMutateOrAliasInputs() {
        val targetChild = linkedMapOf<String, Any?>("kept" to true)
        val patchArray = mutableListOf<Any?>(1L, 2L)
        val target = linkedMapOf<String, Any?>("child" to targetChild)
        val patch = linkedMapOf<String, Any?>("array" to patchArray)

        @Suppress("UNCHECKED_CAST")
        val result = JsonMergePatch.apply(target, patch) as MutableMap<String, Any?>
        assertNotSame(target, result)
        assertNotSame(targetChild, result["child"])
        assertNotSame(patchArray, result["array"])

        (result["child"] as MutableMap<String, Any?>)["added"] = true
        (result["array"] as MutableList<Any?>).add(3L)
        assertEquals(mapOf<String, Any?>("kept" to true), targetChild)
        assertEquals(listOf<Any?>(1L, 2L), patchArray)
    }

    @Test
    fun preservesSharedAndCircularGraphsWithoutRecursingForever() {
        val target = linkedMapOf<String, Any?>()
        target["self"] = target
        val copied = JsonMergePatch.apply(target, emptyMap<String, Any?>()) as Map<String, Any?>
        assertNotSame(target, copied)
        assertSame(copied, copied["self"])

        val patch = linkedMapOf<String, Any?>()
        patch["self"] = patch
        val patched = JsonMergePatch.apply(null, patch) as Map<String, Any?>
        assertSame(patched, patched["self"])
    }

    @Test
    fun rootNullAndUnsupportedTypesAreExplicit() {
        assertNull(jsonMergePatch(mapOf("a" to 1), null))
        assertFailsWith<IllegalArgumentException> {
            JsonMergePatch.apply(null, mapOf("bad" to Any()))
        }
        assertFailsWith<IllegalArgumentException> {
            JsonMergePatch.apply(null, mapOf(1 to "bad"))
        }
    }
}
