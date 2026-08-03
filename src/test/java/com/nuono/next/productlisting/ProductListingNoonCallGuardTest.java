package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingNoonCallGuardTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void nestedBusinessCodeIsNotMistakenForAnAuthEnvelope() {
        ObjectNode response = objectMapper.createObjectNode();
        response.putObject("data").put("code", 403).put("status", "catalog_attribute");

        assertDoesNotThrow(() -> ProductListingNoonCallGuard.requireAuthorized(response));
    }

    @Test
    void alternateErrorFieldsAndTextArraysAreRecognized() {
        for (String field : List.of(
                "errorMessages", "errorMessage", "error_message", "err", "errors")) {
            ObjectNode response = objectMapper.createObjectNode();
            response.putArray(field).add("HTTP 403");
            assertThrows(
                    IllegalStateException.class,
                    () -> ProductListingNoonCallGuard.requireAuthorized(response),
                    field
            );
        }
        assertThrows(
                IllegalStateException.class,
                () -> ProductListingNoonCallGuard.requireAuthorized(
                        objectMapper.getNodeFactory().textNode("auth_required"))
        );
    }
}
