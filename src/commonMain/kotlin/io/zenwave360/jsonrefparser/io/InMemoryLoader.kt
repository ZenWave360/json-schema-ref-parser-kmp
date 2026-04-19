package io.zenwave360.jsonrefparser.io

/**
 * A [DocumentLoader] backed by a pre-supplied text string.
 * Used in tests and for inline-content parsing.
 */
class InMemoryLoader(private val uri: String, private val content: String) : DocumentLoader {
    override fun canLoad(uri: String): Boolean = uri == this.uri
    override suspend fun load(uri: String): String = content
}
