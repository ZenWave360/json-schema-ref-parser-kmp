package io.zenwave360.jsonrefparser

import node.buffer.BufferEncoding
import node.fs.readFileSync

@JsName("require")
private external fun nodeRequire(module: String): dynamic

private val nodePath: dynamic = nodeRequire("path")
private val nodeProcess: dynamic = js("process")

actual fun readTestFile(path: String): String {
    val resolved = nodePath.resolve(path) as String
    return readFileSync(resolved, BufferEncoding.utf8)
}

actual fun testResourceUri(path: String): String {
    val resolved = (nodePath.resolve(path) as String).replace('\\', '/')
    return if (resolved.startsWith("/")) "file://$resolved" else "file:///$resolved"
}
