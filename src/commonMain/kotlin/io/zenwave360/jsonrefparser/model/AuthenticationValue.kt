package io.zenwave360.jsonrefparser.model

enum class AuthenticationType {
    QUERY,
    HEADER,
}

/**
 * An HTTP auth value to inject when loading URLs that match [urlMatcher] or [urlPatterns].
 */
data class AuthenticationValue(
    val key: String,
    val value: String,
    val type: AuthenticationType = AuthenticationType.HEADER,
    val urlMatcher: ((String) -> Boolean)? = null,
    val urlPatterns: List<String> = listOf(".*"),
) {
    fun matches(url: String): Boolean =
        when {
            urlMatcher != null -> urlMatcher.invoke(url)
            urlPatterns.isNotEmpty() -> urlPatterns.any { Regex(it).matches(url) }
            else -> true
        }
}
