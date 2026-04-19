package io.zenwave360.jsonrefparser

/**
 * Full RFC 6901 JSON Pointer implementation.
 */
data class JsonPointer(val tokens: List<String>) {

    fun child(key: String): JsonPointer =
        JsonPointer(tokens + key.replace("~", "~0").replace("/", "~1"))

    fun child(index: Int): JsonPointer =
        JsonPointer(tokens + index.toString())

    fun resolve(document: Any?): Any? {
        var current: Any? = document
        for (token in tokens) {
            current = when (current) {
                is Map<*, *> -> current[token]
                is List<*>   -> current.getOrNull(token.toIntOrNull() ?: return null)
                else         -> return null
            }
        }
        return current
    }

    override fun toString(): String =
        if (tokens.isEmpty()) "" else "/" + tokens.joinToString("/")

    companion object {
        val ROOT = JsonPointer(emptyList())

        fun parse(pointer: String): JsonPointer {
            if (pointer.isEmpty()) return ROOT
            require(pointer.startsWith("/")) { "JSON Pointer must start with /: $pointer" }
            return JsonPointer(
                pointer.drop(1).split("/")
                    .map { it.replace("~1", "/").replace("~0", "~") }
            )
        }

        /** Parse a pointer that may start with #/ (fragment form). */
        fun parseFragment(fragment: String): JsonPointer {
            if (fragment.isEmpty() || fragment == "#" || fragment == "#/") return ROOT
            val pointer = if (fragment.startsWith("#")) fragment.drop(1) else fragment
            return parse(pointer)
        }
    }
}
