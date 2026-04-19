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

        assertTrue(firstRef.getKey().getRef().contains("#") || firstRef.getKey().getRef().contains(".avsc"));
        assertEquals(URI.create(firstRef.getKey().getRef()), firstRef.getKey().getURI());
        assertTrue(firstRef.getKey().getRefFormat() == RefFormat.INTERNAL
                || firstRef.getKey().getRefFormat() == RefFormat.RELATIVE
                || firstRef.getKey().getRefFormat() == RefFormat.CLASSPATH);

        AuthenticationValue auth = new AuthenticationValue()
                .withQueryParam("token", "value")
                .withUrlPattern(".*example.*");
        assertEquals(AuthenticationValue.AuthenticationType.QUERY, auth.getType());
    }
}
