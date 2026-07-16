package io.zenwave360.jsonrefparser

import node.buffer.BufferEncoding
import node.fs.readFileSync
import kotlin.js.JsModule

actual fun readTestFile(path: String): String {
    val resolved = resolveTestResourcePath(path)
    return readFileSync(resolved, BufferEncoding.utf8)
}

actual fun testResourceUri(path: String): String {
    val resolved = resolveTestResourcePath(path).replace('\\', '/')
    return if (resolved.startsWith("/")) "file://$resolved" else "file:///$resolved"
}

private fun resolveTestResourcePath(path: String): String {
    val moduleFilePath = NodeUrlModule.fileURLToPath(js("import.meta.url") as String)
    val moduleDir = NodePathModule.dirname(moduleFilePath)
    return NodePathModule.resolve(moduleDir, path)
}

@JsModule("node:path")
private external object NodePathModule {
    fun dirname(path: String): String
    fun resolve(vararg path: String): String
}

@JsModule("node:url")
private external object NodeUrlModule {
    fun fileURLToPath(url: String): String
}
