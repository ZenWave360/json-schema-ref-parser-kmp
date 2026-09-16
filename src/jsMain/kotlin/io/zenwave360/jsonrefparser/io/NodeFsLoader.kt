package io.zenwave360.jsonrefparser.io

import io.zenwave360.jsonrefparser.platform.nodeFs

/**
 * Loads schema documents from the local filesystem using the Node.js `fs` module.
 * Handles `file://` URIs and bare filesystem paths.
 *
 * The `fs` module is resolved on the first [load], never when this library is loaded, so
 * constructing this loader is safe in a browser. In a browser or Web Worker [load] throws
 * [io.zenwave360.jsonrefparser.platform.CapabilityUnavailableException].
 */
class NodeFsLoader : DocumentLoader {

    override fun canLoad(uri: String): Boolean =
        uri.startsWith("file://") || (!uri.contains("://") && !uri.startsWith("classpath:"))

    override suspend fun load(uri: String): String {
        val normalizedUri = uri.substringBefore('#')
        val filePath = when {
            normalizedUri.matches(Regex("""^file:///[A-Za-z]:/.*$""")) -> normalizedUri.removePrefix("file:///")
            normalizedUri.startsWith("file:///") -> normalizedUri.removePrefix("file://")  // keeps leading /
            normalizedUri.startsWith("file://")  -> normalizedUri.removePrefix("file://")
            else                                 -> normalizedUri
        }
        return readUtf8File(filePath)
    }

    private suspend fun readUtf8File(filePath: String): String =
        nodeFs().readFileSync(filePath, "utf8") as String
}
