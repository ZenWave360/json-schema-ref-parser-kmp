package io.zenwave360.jsonrefparser

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompatibilityJvmTest {

    @Test
    fun `legacy parser supports avsc top level array through jsonContext`() {
        val uri = testResourceUri("asyncapi/sdk-javaType/avros/all_cart_entities.avsc")
        val result = `$RefParser`(uri).parse().getRefs().jsonContext.json()

        assertTrue(result is List<*>)
        assertEquals(3, result.size)
        val second = result[1] as Map<*, *>
        assertEquals("LinesAddedEvent", second["name"])
    }

    @Test
    fun `legacy parser exposes original and replaced refs for zenwave sdk style processing`() {
        val uri = testResourceUri("asyncapi/sdk-javaType/asyncapi-javaType.yml")
        val refs = `$RefParser`(uri)
            .withOptions(`$RefParserOptions`().withOnCircular(`$RefParserOptions`.OnCircular.SKIP))
            .mergeAllOf()
            .getRefs()

        assertTrue(refs.getOriginalRefsList().isNotEmpty())
        assertEquals(refs.getOriginalRefsList().size, refs.getReplacedRefsList().size)
        val first = refs.getOriginalRefsList().first()
        assertTrue(first.key.getRef().contains("/components/messages/") || first.key.getRef().contains("/components/schemas/") || first.key.getRef().contains("avros/"))
        assertNotNull(refs.getOriginalRef(first.value))
    }

    @Test
    fun `legacy parser supports classpath and file loading`() {
        val classpathRefs = `$RefParser`("classpath:asyncapi/multiple-allOf.yml")
            .mergeAllOf()
            .getRefs()
        assertNotNull((classpathRefs.json() as Map<*, *>)["asyncapi"])

        val file = File(URI.create(testResourceUri("asyncapi/multiple-allOf.yml")))
        val fileRefs = `$RefParser`(file)
            .mergeAllOf()
            .getRefs()
        assertNotNull((fileRefs.schema() as Map<*, *>)["components"])
    }

    @Test
    fun `legacy parser supports relative file path strings`() {
        val refs = `$RefParser`("src/commonTest/resources/asyncapi/multiple-allOf.yml")
            .mergeAllOf()
            .getRefs()

        assertNotNull((refs.json() as Map<*, *>)["asyncapi"])
    }

    @Test
    fun `legacy parser supports http loading with authentication`() {
        val body = """
            type: object
            properties:
              id:
                type: string
        """.trimIndent()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor = executor
        server.createContext("/schema.yml") { exchange ->
            val auth = exchange.requestHeaders.getFirst("Authorization")
            if (auth != "Bearer test-token") {
                exchange.sendResponseHeaders(401, -1)
            } else {
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/yaml")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        try {
            val uri = "http://127.0.0.1:${server.address.port}/schema.yml"
            val auth = AuthenticationValue()
                .withHeader("Authorization", "Bearer test-token")
                .withUrlMatcher { it.toString().contains("/schema.yml") }

            val refs = `$RefParser`(uri)
                .withAuthentication(auth)
                .parse()
                .getRefs()

            val json = refs.json() as Map<*, *>
            assertEquals("object", json["type"])
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun `legacy refs expose source locations`() = runTest {
        val refs = `$RefParser`("classpath:openapi/openapi-petstore.yml")
            .parse()
            .getRefs()

        val range = refs.getJsonLocationRange("$.info")
        assertNotNull(range)
        assertTrue(range.first.lineNr >= 0)
        assertTrue(range.second.lineNr >= range.first.lineNr)
    }

    @Test
    fun `legacy refs expose file specific source locations for external documents`() {
        val rootUri = testResourceUri("asyncapi/original-ref/asyncapi-original-ref.yml")
        val externalUri = URI.create(rootUri.substringBeforeLast("/") + "/schema.yaml")
        val refs = `$RefParser`(rootUri)
            .mergeAllOf()
            .getRefs()

        val externalRange = refs.getJsonLocationRange(externalUri, "$.BusinessEventMetadata")
        assertNotNull(externalRange)
        assertEquals(externalUri.toString(), externalRange.first.file)
    }

    @Test
    fun `legacy parser supports query param authentication`() {
        val body = "type: object"
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor = executor
        server.createContext("/schema.yml") { exchange ->
            if (exchange.requestURI.query != "token=test-token") {
                exchange.sendResponseHeaders(401, -1)
            } else {
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        try {
            val uri = "http://127.0.0.1:${server.address.port}/schema.yml"
            val auth = AuthenticationValue()
                .withQueryParam("token", "test-token")
                .withUrlMatcher { it.toString().contains("/schema.yml") }

            val refs = `$RefParser`(uri)
                .withAuthentication(auth)
                .parse()
                .getRefs()

            assertEquals("object", refs.json()["type"])
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun `legacy ref uri resolves external classpath targets to absolute classpath uri`() {
        val refs = `$RefParser`("classpath:asyncapi/sdk-javaType/asyncapi-javaType.yml")
            .mergeAllOf()
            .getRefs()

        val externalRef = refs.getOriginalRefsList()
            .first { it.key.getRef().contains("transport.schema") }
            .key

        assertEquals("classpath", externalRef.getURI().scheme)
        assertTrue(externalRef.getURI().isAbsolute)
        assertTrue(externalRef.getURI().toString().endsWith("/asyncapi/sdk-javaType/json-schemas/transport.schema"))
    }

    @Test
    fun `legacy replaced refs track merged sibling objects`() {
        val refs = `$RefParser`("classpath:asyncapi/compatibility/repeated-enum.yml")
            .mergeAllOf()
            .getRefs()

        val original = refs.getOriginalRefsList()
            .first { it.key.getRef() == "#/components/schemas/Email" }
            .value as Map<*, *>
        val replaced = refs.getReplacedRefsList()
            .first { it.key.getRef() == "#/components/schemas/Email" }
            .value as Map<*, *>

        assertFalse(original.containsKey("description"))
        assertEquals("Email of the user", replaced["description"])
        assertEquals("#/components/schemas/Email", refs.getOriginalRef(replaced)?.getRef())
    }
}
