package io.zenwave360.jsonrefparser

import io.zenwave360.jsonrefparser.model.SourceLocation
import io.zenwave360.jsonrefparser.parser.parseText
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Thorough harness for the source-location index that snakeyaml-engine-kmp makes possible.
 *
 * Each test group verifies a distinct aspect:
 *   1. Completeness   – every node has an entry in [locations]
 *   2. Accuracy       – line/column values match real positions in the text
 *   3. Pointer format – JSON Pointer strings are correctly built and escaped
 *   4. Node types     – maps, sequences, scalars, nulls
 *   5. Dereference    – external-file nodes carry the origin file URI
 *   6. allOf merge    – locations survive merging
 */
class SourceLocationTest {

    private val BASE_URI = "file:///test.yaml"

    // -----------------------------------------------------------------------
    // 1. Completeness
    // -----------------------------------------------------------------------

    @Test
    fun rootNodeAlwaysHasALocation() {
        val doc = parseText("type: string", BASE_URI)
        assertNotNull(doc.locations[""], "root pointer '' must have a location")
    }

    @Test
    fun everyTopLevelKeyHasALocation() {
        val yaml = """
            type: object
            title: Foo
            description: Bar
        """.trimIndent()
        val doc = parseText(yaml, BASE_URI)
        assertNotNull(doc.locations["/type"])
        assertNotNull(doc.locations["/title"])
        assertNotNull(doc.locations["/description"])
    }

    @Test
    fun nestedMapNodesAllHaveLocations() {
        val yaml = """
            components:
              schemas:
                Foo:
                  type: string
                  description: a string
        """.trimIndent()
        val doc = parseText(yaml, BASE_URI)
        assertNotNull(doc.locations["/components"])
        assertNotNull(doc.locations["/components/schemas"])
        assertNotNull(doc.locations["/components/schemas/Foo"])
        assertNotNull(doc.locations["/components/schemas/Foo/type"])
        assertNotNull(doc.locations["/components/schemas/Foo/description"])
    }

    @Test
    fun sequenceItemsAllHaveLocations() {
        val yaml = """
            required:
              - id
              - name
              - email
        """.trimIndent()
        val doc = parseText(yaml, BASE_URI)
        assertNotNull(doc.locations["/required"])
        assertNotNull(doc.locations["/required/0"])
        assertNotNull(doc.locations["/required/1"])
        assertNotNull(doc.locations["/required/2"])
    }

    @Test
    fun sequenceOfMapsEachItemAndItsChildrenHaveLocations() {
        val yaml = """
            fields:
              - name: id
                type: string
              - name: age
                type: integer
        """.trimIndent()
        val doc = parseText(yaml, BASE_URI)
        assertNotNull(doc.locations["/fields/0"])
        assertNotNull(doc.locations["/fields/0/name"])
        assertNotNull(doc.locations["/fields/0/type"])
        assertNotNull(doc.locations["/fields/1"])
        assertNotNull(doc.locations["/fields/1/name"])
        assertNotNull(doc.locations["/fields/1/type"])
    }

    @Test
    fun nullValuedNodeHasALocation() {
        val yaml = "nullable: null"
        val doc = parseText(yaml, BASE_URI)
        assertNotNull(doc.locations["/nullable"])
    }

    @Test
    fun allLocationsReferenceTheSuppliedFileUri() {
        val uri = "file:///schemas/my-schema.yaml"
        val yaml = "type: object\nproperties:\n  id:\n    type: string"
        val doc = parseText(yaml, uri)
        assertTrue(doc.locations.values.all { it.file == uri },
            "every location must reference the supplied URI")
    }

    @Test
    fun locationCountMatchesNodeCount() {
        val yaml = """
            a: 1
            b:
              c: 2
              d: 3
        """.trimIndent()
        val doc = parseText(yaml, BASE_URI)
        // Expect: "" /a /b /b/c /b/d  → 5 entries
        assertEquals(5, doc.locations.size)
    }

    // -----------------------------------------------------------------------
    // 2. Accuracy – exact line / column numbers
    //    YAML positions are 0-based in snakeyaml-engine
    // -----------------------------------------------------------------------

    @Test
    fun rootNodeStartsAtLineZeroColZero() {
        val doc = parseText("a: 1", BASE_URI)
        val root = doc.locations[""]!!
        assertEquals(0, root.line,   "root line should be 0")
        assertEquals(0, root.column, "root col should be 0")
    }

    @Test
    fun scalarValueOnFirstLineHasCorrectColumn() {
        // "a: 1"  →  value "1" starts at col 3
        val doc = parseText("a: 1", BASE_URI)
        val loc = doc.locations["/a"]!!
        assertEquals(0, loc.line)
        assertEquals(3, loc.column)
    }

    @Test
    fun secondTopLevelKeyStartsOnLineOne() {
        val yaml = "a: 1\nb: 2"
        val doc = parseText(yaml, BASE_URI)
        val loc = doc.locations["/b"]!!
        assertEquals(1, loc.line)
        assertEquals(3, loc.column)
    }

    @Test
    fun nestedMapValueLineIsCorrect() {
        // line 0: "outer:"
        // line 1: "  inner: hello"   → value "hello" starts at line 1, col 9
        val yaml = "outer:\n  inner: hello"
        val doc = parseText(yaml, BASE_URI)
        val loc = doc.locations["/outer/inner"]!!
        assertEquals(1, loc.line)
        assertEquals(9, loc.column)
    }

    @Test
    fun endLineIsGreaterThanOrEqualToStartLine() {
        val yaml = """
            type: object
            properties:
              name:
                type: string
        """.trimIndent()
        val doc = parseText(yaml, BASE_URI)
        doc.locations.values.forEach { loc ->
            assertTrue(loc.endLine >= loc.line,
                "endLine(${loc.endLine}) should be >= line(${loc.line}) for ${loc.file}")
        }
    }

    @Test
    fun multiLineStringValueSpansMultipleLines() {
        val yaml = "desc: |\n  line one\n  line two\nother: x"
        val doc = parseText(yaml, BASE_URI)
        val loc = doc.locations["/desc"]!!
        // The block scalar starts on line 0 and its content ends before "other"
        assertTrue(loc.endLine > loc.line,
            "multi-line block scalar should have endLine > line")
    }

    // -----------------------------------------------------------------------
    // 3. Pointer format – RFC 6901 encoding
    // -----------------------------------------------------------------------

    @Test
    fun keyWithForwardSlashIsEscapedInPointer() {
        // In YAML, the key "a/b" must be quoted
        val yaml = "\"a/b\": value"
        val doc = parseText(yaml, BASE_URI)
        // RFC 6901: "/" in a key is encoded as "~1"
        assertNotNull(doc.locations["/a~1b"],
            "key 'a/b' should be encoded as /a~1b in the pointer")
        assertNull(doc.locations["/a/b"],
            "unescaped /a/b should NOT be a valid pointer for key 'a/b'")
    }

    @Test
    fun keyWithTildeIsEscapedInPointer() {
        val yaml = "\"a~b\": value"
        val doc = parseText(yaml, BASE_URI)
        // RFC 6901: "~" in a key is encoded as "~0"
        assertNotNull(doc.locations["/a~0b"],
            "key 'a~b' should be encoded as /a~0b in the pointer")
    }

    @Test
    fun sequenceIndicesFormValidIntegerPointerTokens() {
        val yaml = "items:\n  - x\n  - y\n  - z"
        val doc = parseText(yaml, BASE_URI)
        for (i in 0..2) {
            assertNotNull(doc.locations["/items/$i"],
                "sequence item $i should have pointer /items/$i")
        }
    }

    @Test
    fun deeplyNestedPointerIsWellFormed() {
        val yaml = """
            a:
              b:
                c:
                  d: leaf
        """.trimIndent()
        val doc = parseText(yaml, BASE_URI)
        assertNotNull(doc.locations["/a/b/c/d"])
        assertEquals("/a/b/c/d",
            doc.locations.keys.first { it == "/a/b/c/d" })
    }

    // -----------------------------------------------------------------------
    // 4. Node types – booleans, numbers, nulls, flow style
    // -----------------------------------------------------------------------

    @Test
    fun booleanScalarHasALocation() {
        val doc = parseText("enabled: true\ndisabled: false", BASE_URI)
        assertNotNull(doc.locations["/enabled"])
        assertNotNull(doc.locations["/disabled"])
    }

    @Test
    fun integerScalarHasALocation() {
        val doc = parseText("count: 42", BASE_URI)
        assertNotNull(doc.locations["/count"])
        assertEquals(0, doc.locations["/count"]!!.line)
    }

    @Test
    fun floatScalarHasALocation() {
        val doc = parseText("rate: 3.14", BASE_URI)
        assertNotNull(doc.locations["/rate"])
    }

    @Test
    fun flowStyleMapNodesHaveLocations() {
        val yaml = "point: {x: 1, y: 2}"
        val doc = parseText(yaml, BASE_URI)
        assertNotNull(doc.locations["/point"])
        assertNotNull(doc.locations["/point/x"])
        assertNotNull(doc.locations["/point/y"])
    }

    @Test
    fun flowStyleSequenceNodesHaveLocations() {
        val yaml = "tags: [alpha, beta, gamma]"
        val doc = parseText(yaml, BASE_URI)
        assertNotNull(doc.locations["/tags"])
        assertNotNull(doc.locations["/tags/0"])
        assertNotNull(doc.locations["/tags/1"])
        assertNotNull(doc.locations["/tags/2"])
    }

    // -----------------------------------------------------------------------
    // 5. Real fixture files – completeness on actual schemas
    // -----------------------------------------------------------------------

    @Test
    fun petstoreYamlEveryTopLevelKeyHasALocation() {
        val text = readTestFile("openapi/openapi-petstore.yml")
        val doc = parseText(text, BASE_URI)
        // All first-level keys in an OpenAPI document
        for (key in listOf("openapi", "info", "paths")) {
            assertNotNull(doc.locations["/$key"], "/$key should have a location")
        }
    }

    @Test
    fun shopingCartSchemasYamlLocationsPopulatedForProperties() {
        val text = readTestFile("asyncapi/shoping-cart-multiple-files/schemas.yml")
        val doc = parseText(text, BASE_URI)
        assertNotNull(doc.locations["/components/schemas/cart.header.v1"])
        assertNotNull(doc.locations["/components/schemas/cart.header.v1/type"])
        assertNotNull(doc.locations["/components/schemas/cart.header.v1/properties"])
        assertNotNull(doc.locations["/components/schemas/cart.header.v1/required"])
        assertNotNull(doc.locations["/components/schemas/cart.header.v1/required/0"])
    }

    @Test
    fun avscJsonFileLocationsPopulated() {
        val text = readTestFile("asyncapi/shoping-cart-avro-array/all_cart_entities.avsc")
        val doc = parseText(text, BASE_URI)
        assertNotNull(doc.locations[""])
        assertTrue(doc.locations.size > 3)
    }

    @Test
    fun gh18JsonFileEveryPropertyHasALocation() {
        val text = readTestFile("GH-18.json")
        val doc = parseText(text, BASE_URI)
        assertNotNull(doc.locations["/properties/lorem"])
        assertNotNull(doc.locations["/properties/ipsum"])
    }

    // -----------------------------------------------------------------------
    // 6. Dereference – external-file nodes retain origin URI
    // -----------------------------------------------------------------------

    @Test
    fun afterDereferenceExternalNodesCarryOriginFileUri() = runTest {
        val rootUri = testResourceUri("GH-36/root.json")
        val commonUri = testResourceUri("GH-36/common.schema.json")

        val doc = RefParser(rootUri).dereference().getParsedDocument()

        // Nodes from common.schema.json must reference that file, not root.json
        val externalLocations = doc.locations.values.filter { it.file == commonUri }
        assertTrue(externalLocations.isNotEmpty(),
            "locations from common.schema.json should be present with origin URI")

        // Root file nodes must still reference root.json
        val rootLocations = doc.locations.values.filter { it.file == rootUri }
        assertTrue(rootLocations.isNotEmpty(),
            "locations from root.json should still be present")
    }

    @Test
    fun afterDereferenceInlineNodesStillHaveRootFileUri() = runTest {
        val rootUri = testResourceUri("GH-36/root.json")
        val doc = RefParser(rootUri).dereference().getParsedDocument()

        // The root-level keys (type, properties, required) should have the root URI
        val rootNodeLoc = doc.locations[""]
        assertNotNull(rootNodeLoc)
        assertEquals(rootUri, rootNodeLoc.file)
    }

    @Test
    fun multiFileAvscEachFileHasDistinctOriginUriInLocations() = runTest {
        val rootUri = testResourceUri("asyncapi/shoping-cart-multiple-files/shoping-cart-multiple-files.yml")
        val doc = RefParser(rootUri).dereference().getParsedDocument()

        val distinctFiles = doc.locations.values.map { it.file }.toSet()
        assertTrue(distinctFiles.size > 1,
            "dereferencing multi-file schema should yield locations from multiple origin files")
        assertTrue(distinctFiles.any { it.endsWith(".avsc") },
            "at least one origin file should be an AVSC file")
    }

    // -----------------------------------------------------------------------
    // 7. Locations survive allOf merge
    // -----------------------------------------------------------------------

    @Test
    fun locationsStillPresentAfterMergeAllOf() = runTest {
        val text = readTestFile("asyncapi/multiple-allOf.yml")
        val doc = RefParser.fromText(text, testResourceUri("asyncapi/multiple-allOf.yml"))
            .dereference()
            .mergeAllOf()
            .getParsedDocument()

        // Top-level structure must still be locatable
        assertNotNull(doc.locations[""])
        assertNotNull(doc.locations["/asyncapi"])
        assertTrue(doc.locations.size > 10,
            "locations map should be populated even after mergeAllOf")
    }
}
