package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProductNoonWriteAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductNoonWriteAdapter adapter = new ProductNoonWriteAdapter();

    @Test
    void classifiesSuccessfulResponseWithoutChangingBody() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ok", true);

        ProductNoonWriteResult result = adapter.classifyResponse("Offer 写回", response);

        assertEquals(ProductNoonWriteErrorCategory.SUCCESS, result.getCategory());
        assertTrue(result.isSuccess());
        assertSame(response, result.getResponse());
    }

    @Test
    void classifiesNoonBusinessResponseAsUserFacingFailure() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", "Invalid sale price");

        ProductNoonWriteResult result = adapter.classifyResponse("Offer 写回", response);
        ProductNoonWriteException exception = assertThrows(
                ProductNoonWriteException.class,
                result::throwIfFailure
        );

        assertEquals(ProductNoonWriteErrorCategory.BUSINESS_FAILURE, result.getCategory());
        assertEquals(ProductNoonWriteErrorCategory.BUSINESS_FAILURE, exception.getCategory());
        assertEquals("noon_business_failure", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Offer 写回 失败：Invalid sale price"));
    }

    @Test
    void ignoresEmptyNoonBusinessErrorShapes() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("error", false);
        response.put("detail", "[]");

        ProductNoonWriteResult result = adapter.classifyResponse("ZSKU 写回", response);

        assertEquals(ProductNoonWriteErrorCategory.SUCCESS, result.getCategory());
        assertTrue(result.isSuccess());
    }

    @Test
    void classifiesTimeoutAsReadbackOnlyUnknownWrite() {
        ProductNoonWriteException exception = adapter.classifyException(
                "ZSKU 写回",
                new IllegalStateException("Noon request timed out", new HttpTimeoutException("timeout"))
        );

        assertEquals(ProductNoonWriteErrorCategory.UNKNOWN_WRITE_RESULT, exception.getCategory());
        assertEquals("noon_write_timeout", exception.getErrorCode());
        assertTrue(exception.isReadbackOnly());
        assertTrue(exception.getMessage().contains("ZSKU 写回 请求结果未知"));
    }

    @Test
    void classifiesConnectionClosureAsReadbackOnlyUnknownWrite() {
        ProductNoonWriteException exception = adapter.classifyException(
                "Seller size 写回",
                new IllegalStateException("HTTP/2 stream closed before response EOF")
        );

        assertEquals(ProductNoonWriteErrorCategory.UNKNOWN_WRITE_RESULT, exception.getCategory());
        assertEquals("noon_write_unknown", exception.getErrorCode());
        assertTrue(exception.isReadbackOnly());
    }

    @Test
    void classifiesRateLimitAuthAndGenericWriteFailures() {
        ProductNoonWriteException rateLimited = adapter.classifyException(
                "Offer 写回",
                new IllegalStateException("HTTP 429 too many requests")
        );
        ProductNoonWriteException authRequired = adapter.classifyException(
                "Offer 写回",
                new IllegalStateException("HTTP 403 forbidden")
        );
        ProductNoonWriteException generic = adapter.classifyException(
                "Offer 写回",
                new IllegalStateException("HTTP 500 internal server error")
        );

        assertEquals(ProductNoonWriteErrorCategory.RATE_LIMITED, rateLimited.getCategory());
        assertEquals("noon_rate_limited", rateLimited.getErrorCode());
        assertEquals(ProductNoonWriteErrorCategory.AUTH_REQUIRED, authRequired.getCategory());
        assertEquals("noon_auth_required", authRequired.getErrorCode());
        assertEquals(ProductNoonWriteErrorCategory.WRITE_FAILED, generic.getCategory());
        assertEquals("noon_write_failed", generic.getErrorCode());
    }

    @Test
    void executeDoesNotRetrySoRequestAccountingRemainsOwnedByNoonSession() {
        AtomicInteger calls = new AtomicInteger();

        ProductNoonWriteException exception = assertThrows(
                ProductNoonWriteException.class,
                () -> adapter.execute("Offer 写回", () -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("HTTP 504 gateway timeout");
                })
        );

        assertEquals(1, calls.get());
        assertEquals(ProductNoonWriteErrorCategory.UNKNOWN_WRITE_RESULT, exception.getCategory());
        assertTrue(exception.isReadbackOnly());
    }

    @Test
    void executeReturnsResponseWhenNoonWriteSucceeds() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ok", true);

        JsonNode actual = adapter.execute("Offer 写回", () -> response);

        assertSame(response, actual);
    }
}
