package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingNoonCallGuardMarkerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void nestedExplicitContainersRecognizeEveryAuthStatus() {
        for (int status : List.of(301, 302, 303, 307, 308, 401, 403)) {
            ObjectNode response = objectMapper.createObjectNode();
            response.putObject("response")
                    .putObject("body")
                    .putObject("error")
                    .put("status", status);

            assertThrows(
                    IllegalStateException.class,
                    () -> ProductListingNoonCallGuard.requireAuthorized(response),
                    "HTTP " + status
            );
        }
    }

    @Test
    void explicitTextMarkersAreRecognized() {
        for (String marker : List.of(
                "auth_required",
                "session expired",
                "cookie has expired",
                "unauthorized",
                "authorization rejected",
                "access is forbidden"
        )) {
            ObjectNode response = objectMapper.createObjectNode();
            response.putObject("error").put("message", marker);

            assertThrows(
                    IllegalStateException.class,
                    () -> ProductListingNoonCallGuard.requireAuthorized(response),
                    marker
            );
        }
    }

    @Test
    void ordinaryDataPayloadIsNotTraversed() {
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode data = response.putObject("response").putObject("body").putObject("data");
        data.put("status", 403);
        data.put("message", "forbidden");

        assertDoesNotThrow(() -> ProductListingNoonCallGuard.requireAuthorized(response));
    }
}
