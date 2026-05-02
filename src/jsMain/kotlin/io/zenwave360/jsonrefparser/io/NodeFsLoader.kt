package io.zenwave360.jsonrefparser.io

import kotlin.js.JsModule

/**
 * Loads schema documents from the local filesystem using the Node.js `fs` module.
 * Handles `file://` URIs and bare filesystem paths.
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

    private fun readUtf8File(filePath: String): String =
        NodeFsModule.readFileSync(filePath, "utf8")
}

@JsModule("node:fs")
private external object NodeFsModule {
    fun readFileSync(filePath: String, encoding: String): String
}
