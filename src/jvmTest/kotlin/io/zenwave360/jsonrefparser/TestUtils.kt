package io.zenwave360.jsonrefparser

actual fun readTestFile(path: String): String {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
        ?: throw IllegalArgumentException("Test resource not found: $path")
    return stream.use { it.readBytes().toString(Charsets.UTF_8) }
}

actual fun testResourceUri(path: String): String {
    val url = Thread.currentThread().contextClassLoader.getResource(path)
        ?: throw IllegalArgumentException("Test resource not found: $path")
    return url.toURI().toString()
}
