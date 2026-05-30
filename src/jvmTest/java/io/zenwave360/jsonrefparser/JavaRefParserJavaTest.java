package io.zenwave360.jsonrefparser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.zenwave360.jsonrefparser.io.ClasspathLoader;
import io.zenwave360.jsonrefparser.model.OnCircular;
import io.zenwave360.jsonrefparser.model.OnMissing;
import io.zenwave360.jsonrefparser.model.ParsedDocument;
import io.zenwave360.jsonrefparser.model.RefParserOptions;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

public class JavaRefParserJavaTest {

    @Test
    public void javaFacadeCompilesFromJava() {
        ParsedDocument doc = JavaRefParser.from(new File("src/commonTest/resources/openapi/openapi-petstore.yml"))
                .withOptions(new RefParserOptions(OnCircular.SKIP, OnMissing.FAIL))
                .withResourceClassLoader(Thread.currentThread().getContextClassLoader())
                .dereference()
                .mergeAllOf()
                .getParsedDocument();

        Map<String, Object> schema = (Map<String, Object>) doc.getSchema();
        assertNotNull(schema);
        assertEquals("3.0.2", schema.get("openapi"));
    }

    @Test
    public void javaFacadeSupportsFromText() {
        ParsedDocument doc = JavaRefParser.fromText("{\"type\":\"object\"}", "memory://schema.json")
                .parse()
                .getParsedDocument();

        assertEquals("object", ((Map<String, Object>) doc.getRoot()).get("type"));
        assertEquals("object", doc.getSchema().get("type"));
        assertTrue(doc.getLocations().containsKey(""));
    }

    @Test
    public void javaFacadeExposesTopLevelArrayRoot() {
        Object root = JavaRefParser.from(new File("src/commonTest/resources/asyncapi/sdk-javaType/avros/all_cart_entities.avsc"))
                .parse()
                .getRoot();

        assertTrue(root instanceof java.util.List<?>);
        assertEquals(3, ((java.util.List<?>) root).size());
    }

    @Test
    public void javaFacadeSupportsDefaultLoaderPatching() {
        ParsedDocument doc = JavaRefParser.from("classpath:openapi/openapi-petstore.yml")
                .withDefaultLoaders(Arrays.asList(
                        new ClasspathLoader(Thread.currentThread().getContextClassLoader())
                ))
                .parse()
                .getParsedDocument();

        assertEquals("3.0.2", doc.getSchema().get("openapi"));
    }
}
