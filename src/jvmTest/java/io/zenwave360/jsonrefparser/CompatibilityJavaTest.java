package io.zenwave360.jsonrefparser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.zenwave360.jsonrefparser.resolver.RefFormat;
import org.junit.Test;

public class CompatibilityJavaTest {

    @Test
    public void legacyFacadeCompilesFromJava() throws Exception {
        String uri = Thread.currentThread()
                .getContextClassLoader()
                .getResource("asyncapi/sdk-javaType/avros/all_cart_entities.avsc")
                .toURI()
                .toString();

        $RefParser parser = new $RefParser(uri)
                .withOptions(new $RefParserOptions().withOnCircular($RefParserOptions.OnCircular.SKIP));

        Object json = parser.parse().getRefs().jsonContext.json();
        assertTrue(json instanceof List);
        assertEquals(3, ((List<?>) json).size());

        Map.Entry<$Ref, Object> firstRef = new $RefParser("classpath:asyncapi/sdk-javaType/asyncapi-javaType.yml")
                .mergeAllOf()
                .getRefs()
                .getOriginalRefsList()
                .get(0);

        String ref = firstRef.getKey().getRef();
        URI resolvedUri = firstRef.getKey().getURI();
        assertTrue(ref.contains("#") || ref.contains(".avsc"));
        if (ref.startsWith("#")) {
            // Internal refs have no separate target file: getURI() falls back to the ref itself.
            assertEquals(URI.create(ref), resolvedUri);
        } else {
            // External refs resolve to the absolute target *file* URI, with any JSON Pointer
            // fragment (e.g. "#/1") stripped -- getURI() and getRef() are expected to differ.
            String filePart = ref.contains("#") ? ref.substring(0, ref.indexOf('#')) : ref;
            String fileName = filePart.substring(filePart.lastIndexOf('/') + 1);
            assertTrue(resolvedUri.toString().endsWith(fileName));
        }
        assertTrue(firstRef.getKey().getRefFormat() == RefFormat.INTERNAL
                || firstRef.getKey().getRefFormat() == RefFormat.RELATIVE
                || firstRef.getKey().getRefFormat() == RefFormat.CLASSPATH);

        AuthenticationValue auth = new AuthenticationValue()
                .withQueryParam("token", "value")
                .withUrlPattern(".*example.*");
        assertEquals(AuthenticationValue.AuthenticationType.QUERY, auth.getType());
    }
}
