package io.zenwave360.jsonrefparser.io

import java.net.URI

/**
 * Loads schema documents from the JVM classpath.
 * Handles the `classpath:` URI scheme.
 */
class ClasspathLoader(
    private val classLoader: ClassLoader? = null,
) : DocumentLoader {

    override fun canLoad(uri: String): Boolean = uri.startsWith("classpath:")

    override suspend fun load(uri: String): String {
        var normalizedUri = uri
        if (normalizedUri.startsWith("classpath:") && !normalizedUri.startsWith("classpath:/")) {
            normalizedUri = normalizedUri.replace("classpath:", "classpath:/")
        }
        val resourcePath = URI.create(normalizedUri).path.trimStart('/')
        val stream = candidateClassLoaders()
            .asSequence()
            .mapNotNull { it.getResourceAsStream(resourcePath) }
            .firstOrNull()
            ?: throw java.io.FileNotFoundException("Classpath resource not found: $resourcePath")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun candidateClassLoaders(): List<ClassLoader> = buildList {
        classLoader?.let(::add)
        Thread.currentThread().contextClassLoader?.let(::add)
        ClasspathLoader::class.java.classLoader?.let(::add)
        ClassLoader.getSystemClassLoader()?.let(::add)
    }.distinct()
}
