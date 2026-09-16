package io.zenwave360.jsonrefparser.platform

/**
 * Thrown when a caller reaches a capability that the current platform cannot provide
 * (for example reading the local filesystem from a browser or a Web Worker).
 *
 * It reports an absent platform facility, not a defect: use [RefParserPlatform.isAvailable]
 * to find out beforehand. Libraries built on top of this one throw the same type for their
 * own capability ids.
 *
 * @param capability the capability id, e.g. [RefParserPlatform.FILESYSTEM]
 * @param platform   the platform name, `"jvm"`, `"node"` or `"browser"`
 */
class CapabilityUnavailableException(
    val capability: String,
    val platform: String,
    message: String = "Capability '$capability' is not available on $platform",
) : UnsupportedOperationException(message)

/**
 * Side-effect-free query of what this library can do on the platform it is running on.
 * Asking never throws and never loads a platform module.
 *
 * - JVM: `{FILESYSTEM, CLASSPATH, HTTP}`
 * - JS on Node.js: `{FILESYSTEM, HTTP}`
 * - JS in a browser or Web Worker: `{HTTP}` (when a global `fetch` exists)
 */
object RefParserPlatform {
    const val FILESYSTEM = "jsonrefparser.filesystem"
    const val CLASSPATH = "jsonrefparser.classpath"
    const val HTTP = "jsonrefparser.http"

    /** `"jvm"`, `"node"` or `"browser"`; computed when read. */
    val name: String
        get() = currentPlatformName()

    fun capabilities(): Set<String> = currentPlatformCapabilities()

    /** `false` for unknown capability ids. */
    fun isAvailable(capability: String): Boolean = capability in capabilities()

    /** The capability a normalized [uri] needs, or `null` when it needs no platform facility. */
    internal fun capabilityFor(uri: String): String? = when {
        uri.startsWith("classpath:") -> CLASSPATH
        uri.startsWith("file:") -> FILESYSTEM
        uri.startsWith("http://") || uri.startsWith("https://") -> HTTP
        !uri.contains("://") -> FILESYSTEM
        else -> null
    }

    /** An exception to throw when [uri] needs a capability this platform lacks, otherwise `null`. */
    internal fun unavailableFor(uri: String): CapabilityUnavailableException? {
        val capability = capabilityFor(uri) ?: return null
        return if (isAvailable(capability)) null else CapabilityUnavailableException(capability, name)
    }
}

internal expect fun currentPlatformName(): String

internal expect fun currentPlatformCapabilities(): Set<String>
