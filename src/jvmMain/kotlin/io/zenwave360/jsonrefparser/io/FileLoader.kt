package io.zenwave360.jsonrefparser.io

import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Loads schema documents from the local filesystem.
 * Handles `file://` URIs and bare filesystem paths.
 */
class FileLoader : DocumentLoader {

    override fun canLoad(uri: String): Boolean =
        uri.startsWith("file:") || (!uri.contains("://") && !uri.startsWith("classpath:"))

    override suspend fun load(uri: String): String {
        val path = if (uri.startsWith("file:")) {
            Paths.get(URI.create(uri))
        } else {
            Paths.get(uri)
        }
        return try {
            Files.readAllBytes(path).toString(Charsets.UTF_8)
        } catch (e: java.nio.file.NoSuchFileException) {
            throw java.io.FileNotFoundException("File not found: $uri")
        }
    }
}
