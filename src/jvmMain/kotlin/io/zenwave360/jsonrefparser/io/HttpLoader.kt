package io.zenwave360.jsonrefparser.io

import io.zenwave360.jsonrefparser.model.AuthenticationValue
import io.zenwave360.jsonrefparser.model.AuthenticationType
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Loads schema documents over HTTP/HTTPS.
 * Supports [AuthenticationValue] header injection with URL matching.
 */
class HttpLoader(
    private val auth: List<AuthenticationValue> = emptyList(),
) : DocumentLoader {

    override fun canLoad(uri: String): Boolean =
        uri.startsWith("http://") || uri.startsWith("https://")

    override suspend fun load(uri: String): String {
        var currentUri = applyQueryAuth(cleanUrl(uri))
        var redirectCount = 0

        while (redirectCount < 10) {
            val url = URI.create(currentUri).toURL()
            val conn = url.openConnection() as HttpURLConnection

            // Inject matching auth headers
            for (a in auth) {
                if (a.type == AuthenticationType.HEADER && a.matches(currentUri)) {
                    conn.setRequestProperty(a.key, a.value)
                }
            }
            conn.setRequestProperty("Accept", "application/json, application/yaml, */*")
            conn.setRequestProperty("User-Agent", "json-schema-ref-parser-kmp")

            conn.connect()
            val status = conn.responseCode
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                // Query-param auth is currently applied only to the initial URL before the redirect loop.
                // If we later need redirect-aware query auth parity, re-apply applyQueryAuth(...) here.
                currentUri = conn.getHeaderField("Location") ?: break
                redirectCount++
                conn.disconnect()
                continue
            }
            if (status == 404) throw FileNotFoundException("HTTP 404: $currentUri")
            if (status >= 400) throw RuntimeException("HTTP $status loading: $currentUri")

            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        throw RuntimeException("Too many redirects for: $uri")
    }

    private fun cleanUrl(url: String): String =
        url.replace("{", "%7B").replace("}", "%7D").replace(" ", "%20")

    private fun applyQueryAuth(url: String): String {
        val params = auth
            .filter { it.type == AuthenticationType.QUERY && it.matches(url) }
            .map { "${encode(it.key)}=${encode(it.value)}" }
        if (params.isEmpty()) return url
        val separator = if (url.contains("?")) "&" else "?"
        return url + separator + params.joinToString("&")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
