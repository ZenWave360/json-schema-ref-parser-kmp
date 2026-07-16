package io.zenwave360.jsonrefparser

import kotlinx.coroutines.test.runTest
import java.io.FileOutputStream
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.zenwave360.jsonrefparser.io.ClasspathLoader

/**
 * JVM-specific tests: classpath loading and blocking extension functions.
 */
class RefParserJvmTest {

    @Test
    fun `classpath loader resolves schema`() = runTest {
        val doc = RefParser("classpath:openapi/openapi-petstore.yml")
            .parse()
            .getParsedDocument()

        assertNotNull(doc.schema)
        assertTrue(doc.locations.isNotEmpty())
    }

    @Test
    fun `parseBlocking works on JVM`() {
        val doc = RefParser("classpath:asyncapi/multiple-allOf.yml")
            .parseBlocking()
            .getParsedDocument()

        assertNotNull(doc.schema["asyncapi"])
    }

    @Test
    fun `dereferenceBlocking works on JVM`() {
        val doc = RefParser("classpath:asyncapi/multiple-allOf.yml")
            .dereferenceBlocking()
            .getParsedDocument()

        assertNotNull(doc.schema)
    }

    @Test
    fun `classpath loader uses thread context classloader`() = runTest {
        val tempRoot = Files.createTempDirectory("classpath-loader-test")
        val resourceDir = Files.createDirectories(tempRoot.resolve("catalog/service"))
        Files.writeString(
            resourceDir.resolve("asyncapi.yml"),
            """
                asyncapi: 2.6.0
                info:
                  title: Context ClassLoader Test
                  version: 1.0.0
            """.trimIndent(),
        )

        val originalClassLoader = Thread.currentThread().contextClassLoader
        URLClassLoader(arrayOf(tempRoot.toUri().toURL()), null).use { contextLoader ->
            Thread.currentThread().contextClassLoader = contextLoader
            try {
                val doc = RefParser("classpath:catalog/service/asyncapi.yml")
                    .parse()
                    .getParsedDocument()

                assertEquals("2.6.0", doc.schema["asyncapi"])
            } finally {
                Thread.currentThread().contextClassLoader = originalClassLoader
            }
        }
    }

    @Test
    fun `legacy parser loads classpath resources from injected jar classloader`() {
        val tempRoot = Files.createTempDirectory("classpath-loader-jar-test")
        val jarFile = tempRoot.resolve("test-resources.jar").toFile()

        JarOutputStream(FileOutputStream(jarFile)).use { jar ->
            jar.putNextEntry(JarEntry("retail-domain-catalog/service/asyncapi.yml"))
            jar.write(
                """
                    asyncapi: 3.0.0
                    info:
                      title: JAR ClassLoader Test
                      version: 1.0.0
                    channels:
                      ping:
                        messages:
                          Ping:
                            payload:
                              schema:
                                ${'$'}ref: './avro/Ping.avsc'
                """.trimIndent().toByteArray()
            )
            jar.closeEntry()

            jar.putNextEntry(JarEntry("retail-domain-catalog/service/avro/Ping.avsc"))
            jar.write(
                """
                    {
                      "type": "record",
                      "name": "Ping",
                      "fields": [
                        {"name": "id", "type": "string"}
                      ]
                    }
                """.trimIndent().toByteArray()
            )
            jar.closeEntry()
        }

        URLClassLoader(arrayOf(jarFile.toURI().toURL()), null).use { projectLoader ->
            val refs = `$RefParser`(URI.create("classpath:retail-domain-catalog/service/asyncapi.yml"))
                .withResourceClassLoader(projectLoader)
                .dereference()
                .getRefs()

            val schema = refs.schema() as Map<*, *>
            assertEquals("3.0.0", schema["asyncapi"])

            val channels = schema["channels"] as Map<*, *>
            val ping = channels["ping"] as Map<*, *>
            val messages = ping["messages"] as Map<*, *>
            val pingMessage = messages["Ping"] as Map<*, *>
            val payload = pingMessage["payload"] as Map<*, *>
            val avroSchema = payload["schema"] as Map<*, *>
            assertEquals("record", avroSchema["type"])
            assertEquals("Ping", avroSchema["name"])
        }
    }

    @Test
    fun `legacy parser accepts json text with base uri constructor`() {
        val json = """
            {
              "openapi": "3.0.1",
              "info": {
                "title": "Inline Doc",
                "version": "1.0.0"
              }
            }
        """.trimIndent()

        val doc = `$RefParser`(json, URI.create("memory://inline/openapi.json"))
            .parse()
            .getRefs()

        val schema = doc.schema() as Map<*, *>
        assertEquals("3.0.1", schema["openapi"])
        assertEquals(
            "memory://inline/openapi.json",
            doc.getJsonLocationRange("$")!!.first.file,
        )
    }

    @Test
    fun `java ref parser loads classpath resources from injected jar classloader`() {
        val tempRoot = Files.createTempDirectory("java-ref-parser-classpath-loader-jar-test")
        val jarFile = tempRoot.resolve("test-resources.jar").toFile()

        JarOutputStream(FileOutputStream(jarFile)).use { jar ->
            jar.putNextEntry(JarEntry("retail-domain-catalog/service/asyncapi.yml"))
            jar.write(
                """
                    asyncapi: 3.0.0
                    info:
                      title: JAR ClassLoader Test
                      version: 1.0.0
                    channels:
                      ping:
                        messages:
                          Ping:
                            payload:
                              schema:
                                ${'$'}ref: './avro/Ping.avsc'
                """.trimIndent().toByteArray()
            )
            jar.closeEntry()

            jar.putNextEntry(JarEntry("retail-domain-catalog/service/avro/Ping.avsc"))
            jar.write(
                """
                    {
                      "type": "record",
                      "name": "Ping",
                      "fields": [
                        {"name": "id", "type": "string"}
                      ]
                    }
                """.trimIndent().toByteArray()
            )
            jar.closeEntry()
        }

        URLClassLoader(arrayOf(jarFile.toURI().toURL()), null).use { projectLoader ->
            val doc = JavaRefParser.from("classpath:retail-domain-catalog/service/asyncapi.yml")
                .withResourceClassLoader(projectLoader)
                .dereference()
                .getParsedDocument()

            assertEquals("3.0.0", doc.schema["asyncapi"])

            val channels = doc.schema["channels"] as Map<*, *>
            val ping = channels["ping"] as Map<*, *>
            val messages = ping["messages"] as Map<*, *>
            val pingMessage = messages["Ping"] as Map<*, *>
            val payload = pingMessage["payload"] as Map<*, *>
            val avroSchema = payload["schema"] as Map<*, *>
            assertEquals("record", avroSchema["type"])
            assertEquals("Ping", avroSchema["name"])
        }
    }

    @Test
    fun `ref parser with default loaders patches classpath loader without losing defaults`() = runTest {
        val tempRoot = Files.createTempDirectory("ref-parser-default-loaders-classpath-loader-test")
        val resourceDir = Files.createDirectories(tempRoot.resolve("catalog/service"))
        Files.writeString(
            resourceDir.resolve("asyncapi.yml"),
            """
                asyncapi: 2.6.0
                info:
                  title: Default Loaders Patch Test
                  version: 1.0.0
            """.trimIndent(),
        )

        URLClassLoader(arrayOf(tempRoot.toUri().toURL()), null).use { projectLoader ->
            val doc = RefParser("classpath:catalog/service/asyncapi.yml")
                .withDefaultLoaders(ClasspathLoader(projectLoader))
                .parse()
                .getParsedDocument()

            assertEquals("2.6.0", doc.schema["asyncapi"])
        }
    }

    @Test
    fun `legacy refs list excludes internal asyncapi trait helper refs`() {
        val tempRoot = Files.createTempDirectory("legacy-ref-filter-test")
        val rootFile = tempRoot.resolve("asyncapi.yml")
        val bindingsFile = tempRoot.resolve("kafka-bindings.yml")

        Files.writeString(
            rootFile,
            """
                asyncapi: 3.0.0
                info:
                  title: Trait Filter Test
                  version: 1.0.0
                channels:
                  ping:
                    messages:
                      Ping:
                        payload:
                          schema:
                            type: object
                        traits:
                          - ${'$'}ref: './kafka-bindings.yml#/components/messageTraits/kafkaKeyString'
            """.trimIndent()
        )
        Files.writeString(
            bindingsFile,
            """
                components:
                  messageTraits:
                    kafkaKeyString:
                      bindings:
                        kafka:
                          key:
                            type: string
            """.trimIndent()
        )

        val refs = `$RefParser`(rootFile.toUri())
            .dereference()
            .getRefs()

        assertTrue(
            refs.getOriginalRefsList().none { it.key.getRef().contains("/components/messageTraits/") },
            "legacy compatibility ref lists should not expose internal trait helper refs"
        )
    }

    @Test
    fun `legacy refs list excludes refs used from traits regardless of target path`() {
        val tempRoot = Files.createTempDirectory("legacy-traits-source-filter-test")
        val rootFile = tempRoot.resolve("asyncapi.yml")
        val traitsFile = tempRoot.resolve("traits.yml")

        Files.writeString(
            rootFile,
            """
                asyncapi: 3.0.0
                info:
                  title: Trait Source Filter Test
                  version: 1.0.0
                channels:
                  ping:
                    messages:
                      Ping:
                        payload:
                          schema:
                            type: object
                        traits:
                          - ${'$'}ref: './traits.yml#/KafkaKeyString'
            """.trimIndent()
        )
        Files.writeString(
            traitsFile,
            """
                KafkaKeyString:
                  bindings:
                    kafka:
                      key:
                        type: string
            """.trimIndent()
        )

        val refs = `$RefParser`(rootFile.toUri())
            .dereference()
            .getRefs()

        assertTrue(
            refs.getOriginalRefsList().none { it.key.getRef().contains("traits.yml#/KafkaKeyString") },
            "legacy compatibility ref lists should not expose refs whose usage site is inside traits"
        )
    }

    @Test
    fun `zenwave asyncapi traits remain map shaped after legacy ref annotations`() {
        val resourcesDir = zenwaveSdkTestResourcesDir() ?: return
        val apiUri = resourcesDir
            .resolve("retail-domain-catalog")
            .resolve("merchandising")
            .resolve("inventory")
            .resolve("inventory-adjustment")
            .resolve("asyncapi.yml")
            .toUri()

        val refs = `$RefParser`(apiUri)
            .dereference()
            .getRefs()

        refs.getOriginalRefsList().forEach { pair ->
            (pair.value as? MutableMap<*, *>)?.let { (it as MutableMap<String, Any?>)["x--original-\$ref"] = pair.key.getRef() }
        }
        refs.getReplacedRefsList().forEach { pair ->
            (pair.value as? MutableMap<*, *>)?.let { (it as MutableMap<String, Any?>)["x--original-\$ref"] = pair.key.getRef() }
        }

        val violations = mutableListOf<String>()
        collectTraitShapeViolations(refs.schema(), "$", violations)
        assertTrue(
            violations.isEmpty(),
            "Trait maps must contain only nested map values after legacy annotations. Violations: ${violations.joinToString()}"
        )
    }

    @Test
    fun `zenwave asyncapi traits remain map shaped after legacy ref annotations via classpath`() {
        val resourcesDir = zenwaveSdkTestResourcesDir() ?: return
        val apiUri = URI.create("classpath:retail-domain-catalog/merchandising/inventory/inventory-adjustment/asyncapi.yml")
        val jarFile = createJarFromResources(resourcesDir, "retail-domain-catalog")

        URLClassLoader(arrayOf(jarFile.toUri().toURL()), javaClass.classLoader).use { projectLoader ->
            val refs = `$RefParser`(apiUri)
                .withResourceClassLoader(projectLoader)
                .dereference()
                .getRefs()

            refs.getOriginalRefsList().forEach { pair ->
                (pair.value as? MutableMap<*, *>)?.let { (it as MutableMap<String, Any?>)["x--original-\$ref"] = pair.key.getRef() }
            }
            refs.getReplacedRefsList().forEach { pair ->
                (pair.value as? MutableMap<*, *>)?.let { (it as MutableMap<String, Any?>)["x--original-\$ref"] = pair.key.getRef() }
            }

            val violations = mutableListOf<String>()
            collectTraitShapeViolations(refs.schema(), "$", violations)
            assertTrue(
                violations.isEmpty(),
                "Classpath trait maps must contain only nested map values after legacy annotations. Violations: ${violations.joinToString()}"
            )
        }
    }

    private fun zenwaveSdkTestResourcesDir(): Path? {
        val repoRoot = Path.of("").toAbsolutePath().normalize()
        val sibling = repoRoot.parent?.resolve("zenwave-sdk")
            ?.resolve("zenwave-sdk-test-resources")
            ?.resolve("src")
            ?.resolve("main")
            ?.resolve("resources")
        return sibling?.takeIf { Files.isDirectory(it) }
    }

    private fun collectTraitShapeViolations(node: Any?, path: String, violations: MutableList<String>) {
        when (node) {
            is Map<*, *> -> {
                val traits = node["traits"]
                if (traits is List<*>) {
                    traits.forEachIndexed { index, trait ->
                        if (trait is Map<*, *>) {
                            trait.forEach { (key, value) ->
                                if (value !is Map<*, *>) {
                                    violations += "$path/traits/$index/$key=${value?.let { it::class.qualifiedName } ?: "null"}"
                                }
                            }
                        } else {
                            violations += "$path/traits/$index=${trait?.let { it::class.qualifiedName } ?: "null"}"
                        }
                    }
                }
                node.forEach { (key, value) ->
                    collectTraitShapeViolations(value, "$path/${key ?: "<null>"}", violations)
                }
            }
            is List<*> -> node.forEachIndexed { index, value ->
                collectTraitShapeViolations(value, "$path/$index", violations)
            }
        }
    }

    private fun createJarFromResources(resourcesDir: Path, rootPrefix: String): Path {
        val jarFile = Files.createTempFile("zenwave-sdk-test-resources", ".jar")
        JarOutputStream(FileOutputStream(jarFile.toFile())).use { jar ->
            Files.walk(resourcesDir.resolve(rootPrefix))
                .filter { Files.isRegularFile(it) }
                .forEach { file ->
                    val entryName = resourcesDir.relativize(file).toString().replace('\\', '/')
                    jar.putNextEntry(JarEntry(entryName))
                    Files.newInputStream(file).use { input -> input.copyTo(jar) }
                    jar.closeEntry()
                }
        }
        return jarFile
    }
}
