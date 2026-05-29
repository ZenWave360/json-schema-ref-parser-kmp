package io.zenwave360.jsonrefparser

import com.sun.net.httpserver.HttpServer
import io.zenwave360.jsonrefparser.io.ClasspathLoader
import io.zenwave360.jsonrefparser.io.FileLoader
import io.zenwave360.jsonrefparser.io.HttpLoader
import io.zenwave360.jsonrefparser.model.AuthenticationType
import io.zenwave360.jsonrefparser.model.AuthenticationValue as CoreAuthenticationValue
import io.zenwave360.jsonrefparser.model.AuthenticationType as CoreAuthenticationType
import io.zenwave360.jsonrefparser.resolver.RefFormat
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoaderAndCompatibilityBranchTest {

    @Test
    fun `http loader applies query auth and preserves existing query string`() = runTest {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor = executor
        server.createContext("/schema.yml") { exchange ->
            if (exchange.requestURI.query != "existing=1&token=test-token") {
                exchange.sendResponseHeaders(401, -1)
            } else {
                val bytes = "type: object".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        try {
            val loader = HttpLoader(
                listOf(
                    CoreAuthenticationValue(
                        key = "token",
                        value = "test-token",
                        type = AuthenticationType.QUERY,
                        urlMatcher = { it.contains("/schema.yml") },
                    )
                )
            )

            val body = loader.load("http://127.0.0.1:${server.address.port}/schema.yml?existing=1")
            assertEquals("type: object", body)
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun `http loader returns 404 as file not found`() = runTest {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor = executor
        server.createContext("/missing.yml") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        try {
            val loader = HttpLoader()
            assertFailsWith<java.io.FileNotFoundException> {
                loader.load("http://127.0.0.1:${server.address.port}/missing.yml")
            }
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun `http loader follows redirects until success`() = runTest {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor = executor
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/final")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/final") { exchange ->
            val bytes = "type: object".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val loader = HttpLoader()
            assertEquals("type: object", loader.load("http://127.0.0.1:${server.address.port}/redirect"))
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun `http loader throws runtime exception for non 404 client errors`() = runTest {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor = executor
        server.createContext("/forbidden.yml") { exchange ->
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        server.start()
        try {
            val loader = HttpLoader()
            val error = assertFailsWith<RuntimeException> {
                loader.load("http://127.0.0.1:${server.address.port}/forbidden.yml")
            }
            assertTrue(error.message!!.contains("HTTP 403"))
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun `file loader accepts file uri and bare path and rejects classpath uri`() = runTest {
        val tempFile = Files.createTempFile("file-loader", ".yml")
        Files.writeString(tempFile, "type: object")
        val loader = FileLoader()

        assertTrue(loader.canLoad(tempFile.toString()))
        assertTrue(loader.canLoad(tempFile.toUri().toString()))
        assertFalse(loader.canLoad("classpath:schemas/foo.yml"))
        assertEquals("type: object", loader.load(tempFile.toString()))
        assertEquals("type: object", loader.load(tempFile.toUri().toString()))
    }

    @Test
    fun `file loader turns missing file into file not found`() = runTest {
        val loader = FileLoader()
        assertFailsWith<java.io.FileNotFoundException> {
            loader.load("does-not-exist.yaml")
        }
    }

    @Test
    fun `classpath loader normalizes classpath uri without slash and throws for missing resource`() = runTest {
        val loader = ClasspathLoader()

        assertTrue(loader.canLoad("classpath:openapi/openapi-petstore.yml"))
        val body = loader.load("classpath:openapi/openapi-petstore.yml")
        assertTrue(body.contains("openapi: 3.0.2"))

        assertFailsWith<java.io.FileNotFoundException> {
            loader.load("classpath:/not-found.yml")
        }
    }

    @Test
    fun `legacy refs return null for invalid json path and missing objects`() {
        val refs = `$RefParser`("classpath:openapi/openapi-petstore.yml")
            .parse()
            .getRefs()

        assertNull(refs.getJsonLocationRange("info.title"))
        assertNull(refs.getOriginalRef(null))
        assertNull(refs.getOriginalRef(Any()))
    }

    @Test
    fun `legacy parser rejects unsupported authentication list entries`() {
        val parser = `$RefParser`("classpath:openapi/openapi-petstore.yml")
        assertFailsWith<IllegalStateException> {
            parser.withAuthentication(listOf("not-an-authentication-value"))
        }
    }

    @Test
    fun `legacy authentication value supports header parsing patterns and validation`() {
        val auth = AuthenticationValue()
            .withHeader("Authorization: Bearer test-token")
            .withUrlPattern(".*example.com.*")

        assertTrue(auth.matches(URI.create("https://api.example.com/schema.yml").toURL()))
        auth.setUrlPatterns(listOf(".*other.com.*"))
        assertFalse(auth.matches(URI.create("https://api.example.com/schema.yml").toURL()))
        assertTrue(auth.matches(URI.create("https://api.other.com/schema.yml").toURL()))

        assertFailsWith<IllegalArgumentException> {
            AuthenticationValue().withHeader("invalid-header-format")
        }
    }

    @Test
    fun `legacy authentication value toModel validates required fields and query auth`() {
        val auth = AuthenticationValue()
            .withQueryParam("token", "secret")
            .withUrlMatcher { it.host.contains("example.com") }

        val model = auth.toModel()
        assertEquals(CoreAuthenticationType.QUERY, model.type)
        assertTrue(model.matches("https://api.example.com/schema.yml"))

        assertFailsWith<IllegalArgumentException> {
            AuthenticationValue(value = "missing-key").toModel()
        }
        assertFailsWith<IllegalArgumentException> {
            AuthenticationValue(key = "missingValue").toModel()
        }
    }

    @Test
    fun `legacy refs file specific location lookup returns null for missing path in existing document`() {
        val rootUri = testResourceUri("asyncapi/original-ref/asyncapi-original-ref.yml")
        val externalUri = URI.create(rootUri.substringBeforeLast("/") + "/schema.yaml")
        val refs = `$RefParser`(rootUri)
            .mergeAllOf()
            .getRefs()

        val range = refs.getJsonLocationRange(externalUri, "$.DoesNotExist")
        assertNull(range)
    }

    @Test
    fun `ref format classifies supported uri styles`() {
        assertEquals(RefFormat.FILE, RefFormat.of("file:///tmp/schema.yml"))
        assertEquals(RefFormat.CLASSPATH, RefFormat.of("classpath:/schemas/foo.yml"))
        assertEquals(RefFormat.URL, RefFormat.of("https://example.com/schema.yml"))
        assertEquals(RefFormat.INTERNAL, RefFormat.of("#/components/schemas/Pet"))
        assertEquals(RefFormat.INTERNAL, RefFormat.of("#"))
        assertEquals(RefFormat.RELATIVE, RefFormat.of("./other.yml"))
        assertTrue(RefFormat.URL.isAnExternalRefFormat())
        assertFalse(RefFormat.FILE.isAnExternalRefFormat())
    }
}
