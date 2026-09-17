package io.zenwave360.jsonrefparser.platform

import kotlinx.coroutines.await
import kotlin.js.Promise

// No module-level import of any Node.js module may appear in jsMain: a static import is
// resolved when the module graph loads, so it would stop this library from loading in a
// browser even for callers that never touch the filesystem. Node modules are resolved at
// call time instead, and the build's browser test fails if a static import comes back.

internal fun isNodeRuntime(): Boolean =
    js("typeof globalThis.process === 'object' && globalThis.process !== null && typeof (globalThis.process.versions || {}).node === 'string'") as Boolean

private fun hasGlobalFetch(): Boolean = js("typeof globalThis.fetch === 'function'") as Boolean

internal actual fun currentPlatformName(): String = if (isNodeRuntime()) "node" else "browser"

internal actual fun currentPlatformCapabilities(): Set<String> = buildSet {
    if (isNodeRuntime()) add(RefParserPlatform.FILESYSTEM)
    if (hasGlobalFetch()) add(RefParserPlatform.HTTP)
}

private var cachedNodeFs: dynamic = null

/**
 * Resolves Node's `fs` module when first needed.
 *
 * Uses `process.getBuiltinModule` (Node 20.16+/22.3+) when present, otherwise a dynamic
 * `import()` hidden behind `new Function` so bundlers never see a `node:fs` specifier.
 * Outside Node it throws [CapabilityUnavailableException] for [RefParserPlatform.FILESYSTEM].
 */
internal suspend fun nodeFs(): dynamic {
    if (cachedNodeFs == null) cachedNodeFs = resolveNodeFs(preferBuiltinModule = true)
    return cachedNodeFs
}

/** Uncached resolution; [preferBuiltinModule] = false exercises the dynamic-import fallback in tests. */
internal suspend fun resolveNodeFs(preferBuiltinModule: Boolean): dynamic {
    if (!isNodeRuntime()) {
        throw CapabilityUnavailableException(RefParserPlatform.FILESYSTEM, currentPlatformName())
    }
    val process: dynamic = js("globalThis.process")
    if (preferBuiltinModule && jsTypeOf(process.getBuiltinModule) == "function") {
        return process.getBuiltinModule("node:fs")
    }
    val dynamicImport: dynamic = js("new Function('s', 'return import(s)')")
    val module: dynamic = (dynamicImport("node:fs") as Promise<dynamic>).await()
    return if (jsTypeOf(module.readFileSync) == "function") module else module.default
}
