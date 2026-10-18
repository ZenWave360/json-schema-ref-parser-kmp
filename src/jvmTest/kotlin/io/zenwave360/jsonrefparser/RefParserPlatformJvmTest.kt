package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.platform.CapabilityUnavailableException
import io.zenwave360.jsonrefparser.platform.RefParserPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RefParserPlatformJvmTest {

    @Test
    fun jvmReportsEveryCapability() {
        assertEquals("jvm", RefParserPlatform.name)
        assertEquals(
            setOf(RefParserPlatform.FILESYSTEM, RefParserPlatform.CLASSPATH, RefParserPlatform.HTTP),
            RefParserPlatform.capabilities(),
        )
        assertTrue(RefParserPlatform.isAvailable(RefParserPlatform.CLASSPATH))
        assertFalse(RefParserPlatform.isAvailable("jsonrefparser.unknown"))
    }

    @Test
    fun capabilityUnavailableIsAnUnsupportedOperation() {
        val error: RuntimeException = CapabilityUnavailableException(RefParserPlatform.FILESYSTEM, "browser")
        assertIs<UnsupportedOperationException>(error)
        assertEquals("Capability 'jsonrefparser.filesystem' is not available on browser", error.message)
    }

    @Test
    fun capabilityForUri() {
        assertEquals(RefParserPlatform.CLASSPATH, RefParserPlatform.capabilityFor("classpath:/a.json"))
        assertEquals(RefParserPlatform.FILESYSTEM, RefParserPlatform.capabilityFor("file:///a.json"))
        assertEquals(RefParserPlatform.FILESYSTEM, RefParserPlatform.capabilityFor("a.json"))
        assertEquals(RefParserPlatform.HTTP, RefParserPlatform.capabilityFor("https://x/a.json"))
        assertEquals(null, RefParserPlatform.capabilityFor("memory://a.json"))
        assertEquals(null, RefParserPlatform.unavailableFor("classpath:/a.json"))
    }
}
