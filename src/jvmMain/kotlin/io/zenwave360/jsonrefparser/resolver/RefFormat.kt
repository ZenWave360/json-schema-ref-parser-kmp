package io.zenwave360.jsonrefparser.resolver

object RefConstants {
    const val REFERENCE_SEPARATOR: String = "#/"
}

enum class RefFormat {
    FILE,
    URL,
    CLASSPATH,
    RELATIVE,
    INTERNAL;

    fun isAnExternalRefFormat(): Boolean =
        this == URL || this == RELATIVE || this == CLASSPATH

    companion object {
        @JvmStatic
        fun of(ref: String): RefFormat = when {
            ref.startsWith("file:") -> FILE
            ref.startsWith("classpath:") -> CLASSPATH
            ref.startsWith("http://") || ref.startsWith("https://") -> URL
            ref.startsWith(RefConstants.REFERENCE_SEPARATOR) || ref == "#" -> INTERNAL
            else -> RELATIVE
        }
    }
}
