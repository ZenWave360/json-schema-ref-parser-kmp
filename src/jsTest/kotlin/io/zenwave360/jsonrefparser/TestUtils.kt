package io.zenwave360.jsonrefparser

// Test resources are read through Node modules resolved at call time, so the test bundle
// carries no static Node import and can also be loaded by the browser test run.
// Tests that read resources this way only run on Node (see build.gradle.kts, jsBrowserTest).

private val nodeFs: dynamic get() = js("globalThis.process.getBuiltinModule('node:fs')")
private val nodePath: dynamic get() = js("globalThis.process.getBuiltinModule('node:path')")
private val nodeUrl: dynamic get() = js("globalThis.process.getBuiltinModule('node:url')")

actual fun readTestFile(path: String): String =
    nodeFs.readFileSync(resolveTestResourcePath(path), "utf8") as String

actual fun testResourceUri(path: String): String {
    val resolved = resolveTestResourcePath(path).replace('\\', '/')
    return if (resolved.startsWith("/")) "file://$resolved" else "file:///$resolved"
}

private fun resolveTestResourcePath(path: String): String {
    val moduleFilePath = nodeUrl.fileURLToPath(js("import.meta.url") as String) as String
    val moduleDir = nodePath.dirname(moduleFilePath) as String
    return nodePath.resolve(moduleDir, path) as String
}
