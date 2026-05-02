package io.zenwave360.jsonrefparser.parser

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

class ExtendedJsonContext internal constructor(
    private val value: Any?,
) {
    fun json(): Any? = value
}

object Parser {
    private var resourceClassLoader: ClassLoader = Parser::class.java.classLoader
    private val yamlMapper = ObjectMapper(YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @JvmStatic
    fun withResourceClassLoader(resourceClassLoader: ClassLoader?) {
        if (resourceClassLoader != null) {
            Parser.resourceClassLoader = resourceClassLoader
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun parse(uri: URI): ExtendedJsonContext {
        if (uri.scheme != null && uri.scheme == "classpath") {
            val resource = uri.path.removePrefix("/")
            val inputStream = resourceClassLoader.getResourceAsStream(resource)
                ?: throw IllegalArgumentException("\$RefParser.parse(): InputStream not found [$uri]")
            inputStream.use {
                return parse(it, uri)
            }
        }
        FileInputStream(File(uri)).use {
            return parse(it, uri)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun parse(file: File): ExtendedJsonContext =
        FileInputStream(file).use { parse(it, file) }

    @JvmStatic
    fun parse(content: String): ExtendedJsonContext =
        ByteArrayInputStream(content.toByteArray()).use { parse(it, "string") }

    @JvmStatic
    @Throws(IOException::class)
    fun parse(inputStream: InputStream, source: Any): ExtendedJsonContext {
        if (inputStream == null) {
            throw IllegalArgumentException("\$RefParser.parse(): InputStream not found [$source]")
        }
        val parsed = yamlMapper.readValue(inputStream, Any::class.java)
        return ExtendedJsonContext(parsed)
    }
}
