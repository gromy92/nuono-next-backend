package com.nuono.next.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiProblemExceptionHandlerTest {

    @Test
    void returnsStableStructuredProblemEnvelope() {
        ApiProblemException exception = new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "NOON_PBARCODE_UNMAPPED",
                "BUSINESS_VALIDATION",
                "CREATE_ASN_LINES",
                "请先补全 Noon 商品映射。",
                false,
                true,
                "A05726515PN",
                Map.of("localAsnNo", "OWA-501277"),
                null
        );

        ResponseEntity<ApiProblemResponse> response = new ApiProblemExceptionHandler().handle(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("NOON_PBARCODE_UNMAPPED");
        assertThat(response.getBody().isPartialSuccess()).isTrue();
        assertThat(response.getBody().getReference()).isEqualTo("A05726515PN");
        assertThat(response.getBody().getDetails()).containsEntry("localAsnNo", "OWA-501277");
    }
}
