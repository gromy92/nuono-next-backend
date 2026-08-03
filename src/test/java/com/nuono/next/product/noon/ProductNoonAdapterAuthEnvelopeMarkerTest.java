package com.nuono.next.product.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.product.ProductWriteAuthRecovery;
import com.nuono.next.product.ProductWriteAuthRequiredException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductNoonAdapterAuthEnvelopeMarkerTest {
    private static final Long OWNER_USER_ID = 307L;
    private static final String PROJECT_CODE = "LOCAL-PRJ";
    private static final String STORE_CODE = "STR108065-NAE";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void explicitNestedStatusCodesShouldEnterRecovery() {
        RecoveryFixture fixture = fixture();

        for (int status : List.of(301, 302, 303, 307, 308, 401, 403)) {
            ProductWriteAuthRequiredException exception = assertThrows(
                    ProductWriteAuthRequiredException.class,
                    () -> fixture.adapter.requireNoAuthResponse(
                            OWNER_USER_ID,
                            PROJECT_CODE,
                            STORE_CODE,
                            nestedStatus(status)
                    ),
                    "HTTP " + status
            );
            assertEquals(991L, exception.getRecoveryId());
        }

        verify(fixture.queue, times(7))
                .enqueue(NoonAuthWaitRequest.binding(OWNER_USER_ID, PROJECT_CODE, STORE_CODE));
    }

    @Test
    void explicitAuthTextMarkersShouldEnterRecovery() {
        RecoveryFixture fixture = fixture();

        for (JsonNode response : List.of(
                objectMapper.createObjectNode().put("error", "auth_required"),
                nestedText("detail", "message", "session has expired"),
                nestedText("body", "error", "cookie expired"),
                nestedText("response", "message", "unauthorized"),
                nestedText("error", "detail", "authorization rejected"),
                nestedText("error", "message", "access is forbidden for current credentials"),
                objectMapper.createObjectNode().put("status", "forbidden"),
                objectMapper.createObjectNode().put("code", "FORBIDDEN")
        )) {
            assertThrows(
                    ProductWriteAuthRequiredException.class,
                    () -> fixture.adapter.requireNoAuthResponse(
                            OWNER_USER_ID,
                            PROJECT_CODE,
                            STORE_CODE,
                            response
                    )
            );
        }

        verify(fixture.queue, times(8))
                .enqueue(NoonAuthWaitRequest.binding(OWNER_USER_ID, PROJECT_CODE, STORE_CODE));
    }

    @Test
    void ordinaryDataPayloadShouldNotEnterRecovery() {
        RecoveryFixture fixture = fixture();
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode data = response.putObject("response").putObject("body").putObject("data");
        data.put("code", 403);
        data.put("message", "authorization rejected");

        JsonNode actual = fixture.adapter.requireNoAuthResponse(
                OWNER_USER_ID,
                PROJECT_CODE,
                STORE_CODE,
                response
        );

        assertEquals(response, actual);
        verify(fixture.queue, never()).enqueue(any());
    }

    @Test
    void businessMessagesContainingForbiddenShouldNotEnterRecovery() {
        RecoveryFixture fixture = fixture();
        ObjectNode response = objectMapper.createObjectNode();
        response.putObject("error")
                .put("message", "listing title contains a forbidden phrase")
                .put("detail", "attribute forbiddenClaims is invalid");
        response.put("message", "product transition is forbidden while item is archived");
        response.putArray("errorMessages").add("forbidden color family");

        JsonNode actual = fixture.adapter.requireNoAuthResponse(
                OWNER_USER_ID,
                PROJECT_CODE,
                STORE_CODE,
                response
        );

        assertEquals(response, actual);
        verify(fixture.queue, never()).enqueue(any());
    }

    @Test
    void businessIdentifiersContainingStatusNumbersShouldNotEnterRecovery() {
        RecoveryFixture fixture = fixture();
        ObjectNode response = objectMapper.createObjectNode();
        response.putObject("error")
                .put("message", "PSKU 401 already exists; barcode 403 belongs to this store")
                .put("detail", "variant 302 is not editable");
        response.putArray("errorMessages").add("401").add("product status 403 is invalid");

        JsonNode actual = fixture.adapter.requireNoAuthResponse(
                OWNER_USER_ID,
                PROJECT_CODE,
                STORE_CODE,
                response
        );

        assertEquals(response, actual);
        verify(fixture.queue, never()).enqueue(any());
    }

    private ObjectNode nestedStatus(int status) {
        ObjectNode response = objectMapper.createObjectNode();
        response.putObject("response")
                .putObject("body")
                .putObject("error")
                .put("code", status);
        return response;
    }

    private ObjectNode nestedText(String outer, String inner, String value) {
        ObjectNode response = objectMapper.createObjectNode();
        response.putObject(outer).put(inner, value);
        return response;
    }

    private RecoveryFixture fixture() {
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);
        when(queue.enqueue(NoonAuthWaitRequest.binding(OWNER_USER_ID, PROJECT_CODE, STORE_CODE)))
                .thenReturn(Optional.of(991L));
        ProductNoonAdapter adapter = new ProductNoonAdapter(
                mock(NoonSessionGateway.class),
                new NoonProductGateway()
        );
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(
                queue,
                mock(NoonPullProjectAuthGate.class)
        ));
        return new RecoveryFixture(adapter, queue);
    }

    private static final class RecoveryFixture {
        private final ProductNoonAdapter adapter;
        private final NoonAuthWaitQueue queue;

        private RecoveryFixture(
                ProductNoonAdapter adapter,
                NoonAuthWaitQueue queue
        ) {
            this.adapter = adapter;
            this.queue = queue;
        }
    }
}
