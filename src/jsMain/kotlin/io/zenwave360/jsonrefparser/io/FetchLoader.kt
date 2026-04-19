package io.zenwave360.jsonrefparser.io

import io.zenwave360.jsonrefparser.model.AuthenticationValue
import io.zenwave360.jsonrefparser.model.AuthenticationType
import kotlinx.coroutines.await

// External declarations for the Node.js 18+ built-in `fetch` API
@JsName("fetch")
private external fun nativeFetch(url: String, init: dynamic = definedExternally): kotlin.js.Promise<dynamic>

@JsName("encodeURIComponent")
private external fun nativeEncodeURIComponent(value: String): String

/**
 * Loads schema documents over HTTP/HTTPS using the Node.js `fetch` API.
 * Supports [AuthenticationValue] header injection with URL matching.
 */
class FetchLoader(
    private val auth: List<AuthenticationValue> = emptyList(),
) : DocumentLoader {

    override fun canLoad(uri: String): Boolean =
        uri.startsWith("http://") || uri.startsWith("https://")

    override suspend fun load(uri: String): String {
        // Query-param auth is currently applied once to the request URL before fetch().
        // If redirect-aware query auth parity is needed later, this loader will need explicit redirect handling.
        val requestUri = applyQueryAuth(uri)
        val headers: dynamic = js("{}")
        headers["Accept"] = "application/json, application/yaml, */*"
        for (a in auth) {
            if (a.type == AuthenticationType.HEADER && a.matches(requestUri)) {
                headers[a.key] = a.value
            }
        }

        val init: dynamic = js("{}")
        init["headers"] = headers

        val response = nativeFetch(requestUri, init).await()
        val status = response.status as Int
        if (status >= 400) throw RuntimeException("HTTP $status loading: $requestUri")

        return (response.text() as kotlin.js.Promise<String>).await()
    }

    private fun applyQueryAuth(url: String): String {
        val params = auth
            .filter { it.type == AuthenticationType.QUERY && it.matches(url) }
            .map { "${encode(it.key)}=${encode(it.value)}" }
        if (params.isEmpty()) return url
        val separator = if (url.contains("?")) "&" else "?"
        return url + separator + params.joinToString("&")
    }

    private fun encode(value: String): String = nativeEncodeURIComponent(value)
}
