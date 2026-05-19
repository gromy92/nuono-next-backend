package com.nuono.next.procurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
class ProcurementJsonSupport {

    private final ObjectMapper objectMapper;

    ProcurementJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String toJson(Object value, String failureMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(failureMessage, exception);
        }
    }
}
