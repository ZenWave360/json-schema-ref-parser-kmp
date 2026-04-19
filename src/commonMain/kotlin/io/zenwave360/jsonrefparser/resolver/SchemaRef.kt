package io.zenwave360.jsonrefparser.resolver

private const val FRAGMENT_SEPARATOR = "#/"

internal enum class SchemaRefFormat {
    /** `#/foo/bar` — pointer into the current document. */
    INTERNAL,
    /** Relative path like `./other.yaml` or `../schemas/foo.yaml`. */
    RELATIVE,
    /** `file:///path/to/file.yaml` */
    FILE,
    /** `classpath:/path/to/resource.yaml` */
    CLASSPATH,
    /** `http://` or `https://` */
    URL;

    val isExternal: Boolean
        get() = this == RELATIVE || this == FILE || this == CLASSPATH || this == URL
}

/**
 * A parsed `$ref` value with its resolved file URI and JSON Pointer fragment.
 *
 * @param rawRef        The original `$ref` string.
 * @param format        Detected format.
 * @param fileUri       Resolved absolute URI of the target file (null for INTERNAL refs).
 * @param pointer       JSON Pointer fragment (null if no fragment; starts with `#/` when present).
 * @param referencingUri Base URI of the file that contained the `$ref`.
 */
internal data class SchemaRef(
    val rawRef: String,
    val format: SchemaRefFormat,
    val fileUri: String?,
    val pointer: String?,
    val referencingUri: String,
) {
    companion object {
        fun parse(refString: String, baseUri: String): SchemaRef {
            val ref = mungeRef(refString)
            val format = detectFormat(ref)

            if (format == SchemaRefFormat.INTERNAL) {
                return SchemaRef(ref, format, null, ref, baseUri)
            }

            // Split on first `#/` to separate file URI from pointer fragment
            val hashIdx = ref.indexOf(FRAGMENT_SEPARATOR)
            val filePart: String
            val pointer: String?
            if (hashIdx >= 0) {
                filePart = ref.substring(0, hashIdx)
                pointer = ref.substring(hashIdx) // includes the '#/'
            } else if (ref == "#") {
                return SchemaRef(ref, SchemaRefFormat.INTERNAL, null, FRAGMENT_SEPARATOR, baseUri)
            } else {
                filePart = ref
                pointer = null
            }

            val resolvedFileUri = when (format) {
                SchemaRefFormat.RELATIVE -> resolveUri(baseUri, filePart)
                else               -> filePart
            }

            return SchemaRef(ref, format, resolvedFileUri, pointer, baseUri)
        }

        /**
         * Normalise a raw ref string before further processing.
         * Mirrors the JVM `mungedRef` behaviour.
         */
        private fun mungeRef(ref: String): String {
            if (ref == "#") return FRAGMENT_SEPARATOR
            // Plain filename without scheme, not starting with # or / and not containing $
            // → prefix with ./ to make it explicitly relative
            if (!ref.contains(":") &&
                !ref.startsWith("#") &&
                !ref.startsWith("/") &&
                !ref.contains("\$") &&
                ref.indexOf('.') > 0
            ) {
                return "./$ref"
            }
            return ref
        }

        private fun detectFormat(ref: String): SchemaRefFormat = when {
            ref.startsWith("classpath:")     -> SchemaRefFormat.CLASSPATH
            ref.startsWith("http://") ||
            ref.startsWith("https://")       -> SchemaRefFormat.URL
            ref.startsWith("file://")        -> SchemaRefFormat.FILE
            ref.startsWith(FRAGMENT_SEPARATOR) ||
            ref == "#"                       -> SchemaRefFormat.INTERNAL
            else                             -> SchemaRefFormat.RELATIVE
        }

        /**
         * Resolve a relative URI against a base URI.
         * Handles `.` and `..` path segments.
         */
        internal fun resolveUri(base: String, relative: String): String {
            if (relative.isEmpty()) return base
            if (relative.startsWith("http://") || relative.startsWith("https://") ||
                relative.startsWith("file://") || relative.startsWith("classpath:")
            ) return relative

            // Replace backslashes (Windows paths) before processing
            val normalizedBase = base.replace('\\', '/')
            val normalizedRel = relative.replace('\\', '/')

            val baseDir = normalizedBase.substringBeforeLast("/") + "/"
            val combined = if (normalizedRel.startsWith("/")) normalizedRel else baseDir + normalizedRel

            val parts = combined.split("/")
            val result = mutableListOf<String>()
            for (part in parts) {
                when (part) {
                    "."  -> { /* skip */ }
                    ".." -> if (result.size > 3) result.removeLast() // don't go above scheme+authority
                    else -> result.add(part)
                }
            }
            return result.joinToString("/")
        }
    }
}
