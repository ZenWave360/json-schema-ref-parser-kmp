package io.zenwave360.jsonrefparser.model

enum class OnCircular {
    /** Replace circular $ref with a reference to the already-resolved object. */
    RESOLVE,
    /** Leave the $ref map node unresolved in place. */
    SKIP,
    /** Throw a CircularReferenceException. */
    FAIL,
}

enum class OnMissing {
    /** Throw a MissingRefException when a $ref target cannot be loaded. */
    FAIL,
    /** Leave the $ref node unresolved and continue. */
    SKIP,
}

data class RefParserOptions(
    val onCircular: OnCircular = OnCircular.RESOLVE,
    val onMissing: OnMissing = OnMissing.FAIL,
)

class CircularReferenceException(message: String) : RuntimeException(message)
class MissingRefException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
