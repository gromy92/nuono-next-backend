package com.nuono.next.procurement.aliorder;

import org.springframework.http.ResponseEntity;

/** Closes the response boundary before provider payload parsing or credential writes. */
final class Ali1688OpenApiHttpResponse {
    private Ali1688OpenApiHttpResponse() {
    }

    static <T> T requireSuccessfulBody(ResponseEntity<T> response) {
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("unexpected_http_status");
        }
        return response.getBody();
    }
}
