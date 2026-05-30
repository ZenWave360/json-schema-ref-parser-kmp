package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.model.ParsedDocument
import io.zenwave360.jsonrefparser.model.ResolvedRef
import io.zenwave360.jsonrefparser.model.SourceLocation
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LegacyRefsCompanionBranchTest {

    @Test
    fun `refs companion filters internal helper refs but keeps normal refs addressable by object`() {
        val publicValue = linkedMapOf<String, Any?>("type" to "string")
        val traitValue = linkedMapOf<String, Any?>("type" to "object")
        val bindingValue = linkedMapOf<String, Any?>("type" to "object")

        val doc = ParsedDocument(
            root = mapOf("value" to publicValue),
            schema = mapOf("value" to publicValue),
            locations = mapOf("" to SourceLocation("memory://schema.yml", 0, 0, 0, 0)),
            resolvedRefs = listOf(
                ResolvedRef(
                    refString = "#/components/schemas/Public",
                    resolvedTo = publicValue,
                    sourcePointer = "/components/messages/Ping/payload",
                ),
                ResolvedRef(
                    refString = "#/components/messageTraits/InternalTrait",
                    resolvedTo = traitValue,
                    sourcePointer = "/channels/ping/messages/Ping/traits/0",
                ),
                ResolvedRef(
                    refString = "#/components/channelBindings/InternalBinding",
                    resolvedTo = bindingValue,
                    sourcePointer = "/channels/ping/bindings",
                ),
            ),
        )

        val refs = `$Refs`.from(doc)

        assertEquals(1, refs.getOriginalRefsList().size)
        assertEquals("#/components/schemas/Public", refs.getOriginalRefsList().first().key.getRef())
        assertEquals("#/components/schemas/Public", refs.getOriginalRef(publicValue)?.getRef())
        assertEquals("#/components/messageTraits/InternalTrait", refs.getOriginalRef(traitValue)?.getRef())
        assertEquals("#/components/channelBindings/InternalBinding", refs.getOriginalRef(bindingValue)?.getRef())
    }

    @Test
    fun `refs companion parses json paths for root dot and bracket notation`() {
        val rootLocation = SourceLocation("memory://schema.yml", 0, 0, 0, 0)
        val infoLocation = SourceLocation("memory://schema.yml", 1, 2, 1, 10)
        val nameLocation = SourceLocation("memory://schema.yml", 2, 4, 2, 12)
        val locations = mapOf(
            "" to rootLocation,
            "/info" to infoLocation,
            "/names/0" to nameLocation,
        )

        val refs = `$Refs`.from(
            ParsedDocument(
                root = mapOf("info" to mapOf("title" to "Example")),
                schema = mapOf("info" to mapOf("title" to "Example")),
                locations = locations,
                documentLocations = mapOf("memory://schema.yml" to locations),
            )
        )

        assertEquals(0, refs.getJsonLocationRange("$")!!.first.lineNr)
        assertEquals(1, refs.getJsonLocationRange("$.info")!!.first.lineNr)
        assertEquals(2, refs.getJsonLocationRange(URI.create("memory://schema.yml"), "$['names'][0]")!!.first.lineNr)
        assertTrue(refs.getJsonLocationRange("$.") == null)
        assertTrue(refs.getJsonLocationRange("$['names'") == null)
        assertTrue(refs.getJsonLocationRange("$.missing") == null)
    }
}
