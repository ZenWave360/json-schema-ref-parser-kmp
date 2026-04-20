package io.zenwave360.jsonrefparser

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RegressionTest {

    @Test
    fun dereferenceAndMergeAllOfKeepAsyncapiOutputAcyclicForDeepScans() = runTest {
        val text = readTestFile("asyncapi/multiple-allOf.yml")
        val doc = RefParser.fromText(text, testResourceUri("asyncapi/multiple-allOf.yml"))
            .dereference()
            .mergeAllOf()
            .getParsedDocument()

        assertAcyclic(doc.schema)
    }

    @Test
    fun asyncapiJavaTypeFixtureMatchesSdkExpectations() = runTest {
        val uri = testResourceUri("asyncapi/sdk-javaType/asyncapi-javaType.yml")
        val parsed = RefParser(uri).dereference().getParsedDocument()
        @Suppress("UNCHECKED_CAST")
        val schema = parsed.schema as MutableMap<String, Any?>
        annotateResolvedRefs(schema, parsed.resolvedRefs)

        @Suppress("UNCHECKED_CAST")
        val channels = schema["channels"] as Map<String, Any?>
        val channel = channels["ecommerce.\${tenant}.\${environment}.purchase.cart.public.v1"] as Map<String, Any?>
        val publish = channel["publish"] as Map<String, Any?>
        val message = publish["message"] as Map<String, Any?>
        val oneOf = message["oneOf"] as List<Map<String, Any?>>

        val paramTypes = oneOf.mapNotNull { messageJavaType(it) }

        assertEquals(4, paramTypes.size)
        assertEquals("org.asyncapi.tools.example.event.cart.v1.LinesAddedEvent", paramTypes[0])
        assertEquals("ProductPayload", paramTypes[1])
        assertEquals("ProductPayload", paramTypes[2])
        assertEquals("io.example.schema.TransportNotificationEventData", paramTypes[3])
    }
}
