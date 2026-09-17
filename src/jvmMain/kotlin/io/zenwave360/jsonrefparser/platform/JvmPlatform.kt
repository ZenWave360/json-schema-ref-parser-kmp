package io.zenwave360.jsonrefparser.platform

internal actual fun currentPlatformName(): String = "jvm"

internal actual fun currentPlatformCapabilities(): Set<String> =
    setOf(RefParserPlatform.FILESYSTEM, RefParserPlatform.CLASSPATH, RefParserPlatform.HTTP)
