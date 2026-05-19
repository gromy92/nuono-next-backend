package com.nuono.next.ai;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AiJsonSchemaValidatorTest {

    private final AiJsonSchemaValidator validator = new AiJsonSchemaValidator();

    @Test
    void shouldAcceptValidObject() {
        List<String> errors = validator.validate(sampleSchema(), object("routeName", "air", "estimatedCost", 12.5d, "priority", 1));

        Assertions.assertTrue(errors.isEmpty());
    }

    @Test
    void shouldRejectMissingRequiredProperty() {
        List<String> errors = validator.validate(sampleSchema(), object("routeName", "air", "priority", 1));

        Assertions.assertTrue(errors.stream().anyMatch(error -> error.contains("$.estimatedCost is required")));
    }

    @Test
    void shouldRejectWrongType() {
        List<String> errors = validator.validate(sampleSchema(), object("routeName", "air", "estimatedCost", "12.5", "priority", "first"));

        Assertions.assertTrue(errors.stream().anyMatch(error -> error.contains("$.estimatedCost expected number")));
        Assertions.assertTrue(errors.stream().anyMatch(error -> error.contains("$.priority expected integer")));
    }

    private Map<String, Object> sampleSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("routeName", object("type", "string"));
        properties.put("estimatedCost", object("type", "number"));
        properties.put("priority", object("type", "integer"));
        return object(
                "type", "object",
                "required", Arrays.asList("routeName", "estimatedCost"),
                "properties", properties
        );
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
