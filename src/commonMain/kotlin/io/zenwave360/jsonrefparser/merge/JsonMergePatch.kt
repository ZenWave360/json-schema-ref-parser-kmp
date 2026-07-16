package io.zenwave360.jsonrefparser.merge

import kotlin.jvm.JvmStatic

/**
 * RFC 7396 JSON Merge Patch for the map/list/scalar graph produced by this parser.
 *
 * Both inputs are treated as immutable. The returned graph never aliases a mutable
 * map or list from either input and circular/shared graphs are copied safely.
 */
object JsonMergePatch {
    @JvmStatic
    fun apply(target: Any?, patch: Any?): Any? {
        val targetCopy = GraphCopier().copy(target)
        return MergeContext().merge(targetCopy, patch)
    }
}

fun jsonMergePatch(target: Any?, patch: Any?): Any? = JsonMergePatch.apply(target, patch)

private class MergeContext {
    private val patchCopies = GraphCopier()
    private val activeObjectPatches = mutableListOf<ActivePatch>()

    @Suppress("UNCHECKED_CAST")
    fun merge(target: Any?, patch: Any?): Any? {
        if (patch !is Map<*, *>) return patchCopies.copy(patch)

        validateObject(patch)
        activeObjectPatches.lastOrNull { it.patch === patch }?.let { return it.output }

        val output = if (target is MutableMap<*, *>) {
            target as MutableMap<String, Any?>
        } else {
            linkedMapOf()
        }
        activeObjectPatches += ActivePatch(patch, output)
        try {
            patch.forEach { (rawKey, patchValue) ->
                val key = rawKey as String
                if (patchValue == null) {
                    output.remove(key)
                } else {
                    output[key] = merge(output[key], patchValue)
                }
            }
        } finally {
            activeObjectPatches.removeAt(activeObjectPatches.lastIndex)
        }
        return output
    }

    private fun validateObject(value: Map<*, *>) {
        val invalidKey = value.keys.firstOrNull { it !is String }
        require(invalidKey == null) {
            "JSON object keys must be strings; unsupported key: ${invalidKey?.let { it::class.simpleName }}"
        }
    }

    private data class ActivePatch(
        val patch: Map<*, *>,
        val output: MutableMap<String, Any?>,
    )
}

private class GraphCopier {
    private val copies = mutableListOf<CopyEntry>()

    fun copy(value: Any?): Any? = when (value) {
        null, is String, is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double -> value
        is Map<*, *> -> copyMap(value)
        is List<*> -> copyList(value)
        else -> throw IllegalArgumentException(
            "Unsupported JSON runtime type: ${value::class.simpleName ?: value::class.toString()}",
        )
    }

    private fun copyMap(source: Map<*, *>): MutableMap<String, Any?> {
        existing(source)?.let { @Suppress("UNCHECKED_CAST") return it as MutableMap<String, Any?> }
        val output = linkedMapOf<String, Any?>()
        copies += CopyEntry(source, output)
        source.forEach { (rawKey, child) ->
            require(rawKey is String) {
                "JSON object keys must be strings; unsupported key: ${rawKey?.let { it::class.simpleName }}"
            }
            output[rawKey] = copy(child)
        }
        return output
    }

    private fun copyList(source: List<*>): MutableList<Any?> {
        existing(source)?.let { @Suppress("UNCHECKED_CAST") return it as MutableList<Any?> }
        val output = mutableListOf<Any?>()
        copies += CopyEntry(source, output)
        source.forEach { output += copy(it) }
        return output
    }

    private fun existing(source: Any): Any? =
        copies.firstOrNull { it.source === source }?.copy

    private data class CopyEntry(val source: Any, val copy: Any)
}
