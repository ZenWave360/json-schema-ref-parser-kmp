package io.zenwave360.jsonrefparser.io

import node.buffer.BufferEncoding
import node.fs.readFileSync

/**
 * Loads schema documents from the local filesystem using the Node.js `fs` module.
 * Handles `file://` URIs and bare filesystem paths.
 */
class NodeFsLoader : DocumentLoader {

    override fun canLoad(uri: String): Boolean =
        uri.startsWith("file://") || (!uri.contains("://") && !uri.startsWith("classpath:"))

    override suspend fun load(uri: String): String {
        val filePath = when {
            uri.startsWith("file:///") -> uri.removePrefix("file://")  // keeps leading /
            uri.startsWith("file://")  -> uri.removePrefix("file://")
            else                       -> uri
        }
        return readFileSync(filePath, BufferEncoding.utf8)
    }
}
