package io.zenwave360.jsonrefparser.io

import io.zenwave360.jsonrefparser.model.AuthenticationValue

/**
 * Strategy for loading the raw text of a schema document from a given URI.
 */
interface DocumentLoader {
    /** Return true if this loader can handle the given URI scheme / path format. */
    fun canLoad(uri: String): Boolean

    /** Load and return the text content of the document at [uri]. */
    suspend fun load(uri: String): String
}

/**
 * Platform-specific default set of loaders.
 * - JVM: [ClasspathLoader], [FileLoader], [HttpLoader]
 * - JS/Node.js: [NodeFsLoader], [FetchLoader]
 */
expect fun defaultLoaders(auth: List<AuthenticationValue> = emptyList()): List<DocumentLoader>
