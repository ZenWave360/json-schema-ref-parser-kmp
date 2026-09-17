package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.jsonrefparser.io.FetchLoader
import io.zenwave360.jsonrefparser.io.NodeFsLoader
import io.zenwave360.jsonrefparser.io.defaultLoaders
import io.zenwave360.jsonrefparser.platform.CapabilityUnavailableException
import io.zenwave360.jsonrefparser.platform.RefParserPlatform
import io.zenwave360.jsonrefparser.platform.resolveNodeFs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs on Node (jsNodeTest) and in a headless browser (jsBrowserTest).
 * Must not read fixtures through the filesystem.
 */
class JsRuntimeTest {

    private val onNode: Boolean = js("typeof globalThis.process === 'object' && globalThis.process !== null && typeof (globalThis.process.versions || {}).node === 'string'") as Boolean

    @Test
    fun reportsPlatformAndCapabilitiesWithoutThrowing() {
        assertEquals(if (onNode) "node" else "browser", RefParserPlatform.name)
        assertEquals(onNode, RefParserPlatform.isAvailable(RefParserPlatform.FILESYSTEM))
        assertFalse(RefParserPlatform.isAvailable(RefParserPlatform.CLASSPATH))
        assertTrue(RefParserPlatform.isAvailable(RefParserPlatform.HTTP))
        assertFalse(RefParserPlatform.isAvailable("jsonrefparser.unknown"))
    }

    @Test
    fun exportsPlatformQueryToJavaScript() {
        val platform: dynamic = refParserPlatform()
        assertEquals(RefParserPlatform.name, platform.name as String)
        val capabilities = (platform.capabilities as Array<String>).toSet()
        assertEquals(RefParserPlatform.capabilities(), capabilities)
    }

    @Test
    fun defaultLoadersAreTheSameOnEveryJsRuntime() {
        val loaders = defaultLoaders()
        assertEquals(2, loaders.size)
        assertTrue(loaders[0] is NodeFsLoader)
        assertTrue(loaders[1] is FetchLoader)
    }

    @Test
    fun readsDocumentsThroughACallerSuppliedReader() = runTest {
        val documents = mapOf(
            "workspace://specs/root.yaml" to """
                type: object
                properties:
                  address:
                    ${'$'}ref: 'common.yaml#/Address'
            """.trimIndent(),
            "workspace://specs/common.yaml" to """
                Address:
                  type: object
                  properties:
                    street:
                      type: string
            """.trimIndent(),
        )
        val requested = mutableListOf<String>()
        val reader = object : DocumentLoader {
            override fun canLoad(uri: String) = uri.startsWith("workspace://")
            override suspend fun load(uri: String): String {
                requested += uri
                return documents.getValue(uri.substringBefore('#'))
            }
        }

        val doc = RefParser("workspace://specs/root.yaml").withLoaders(reader).dereference().getParsedDocument()

        @Suppress("UNCHECKED_CAST")
        val address = (doc.schema["properties"] as Map<String, Any?>)["address"] as Map<String, Any?>
        assertEquals("object", address["type"])
        assertEquals(listOf("workspace://specs/root.yaml", "workspace://specs/common.yaml"), requested)
    }

    @Test
    fun classpathIsReportedAsUnavailable() = runTest {
        val error = assertFailsWith<CapabilityUnavailableException> {
            RefParser("classpath:/schema.json").parse()
        }
        assertEquals(RefParserPlatform.CLASSPATH, error.capability)
        assertEquals(RefParserPlatform.name, error.platform)
    }

    @Test
    fun filesystemIsReportedAsUnavailableInABrowser() = runTest {
        if (onNode) return@runTest
        val direct = assertFailsWith<CapabilityUnavailableException> {
            RefParser("file:///schemas/root.json").parse()
        }
        assertEquals(RefParserPlatform.FILESYSTEM, direct.capability)
        assertEquals("browser", direct.platform)

        val viaRef = assertFailsWith<CapabilityUnavailableException> {
            RefParser.fromText("{\"a\": {\"\$ref\": \"file:///schemas/other.json\"}}", baseUri = "memory://root.json")
                .dereference()
        }
        assertEquals(RefParserPlatform.FILESYSTEM, viaRef.capability)
    }

    @Test
    fun fetchLoaderResolvesCrossDocumentRefsInABrowser() = runTest {
        if (onNode) return@runTest
        val origin = js("globalThis.location.origin") as String
        val rootUri = "$origin/base/kotlin/GH-36/root.json"

        val doc = RefParser(rootUri).dereference().mergeAllOf().getParsedDocument()

        @Suppress("UNCHECKED_CAST")
        val properties = doc.schema["properties"] as Map<String, Any?>
        assertTrue(properties.containsKey("a"))
        assertTrue(properties.containsKey("ingressDomain"))
        assertTrue(doc.resolvedRefs.any { it.targetUri?.endsWith("/GH-36/common.schema.json") == true })
    }

    @Test
    fun nodeFsResolvesWithAndWithoutGetBuiltinModule() = runTest {
        for (preferBuiltinModule in listOf(true, false)) {
            if (onNode) {
                val fs: dynamic = resolveNodeFs(preferBuiltinModule)
                assertEquals("function", jsTypeOf(fs.readFileSync))
            } else {
                val error = assertFailsWith<CapabilityUnavailableException> { resolveNodeFs(preferBuiltinModule) }
                assertEquals(RefParserPlatform.FILESYSTEM, error.capability)
            }
        }
    }

    @Test
    fun nodeFilesystemLoaderReadsFilesOnNode() = runTest {
        if (!onNode) return@runTest
        val doc = RefParser(testResourceUri("GH-36/root.json")).dereference().getParsedDocument()
        assertTrue(doc.schema.containsKey("properties"))
    }
}
