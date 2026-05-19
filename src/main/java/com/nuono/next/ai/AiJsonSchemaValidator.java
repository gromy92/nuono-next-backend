package com.nuono.next.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class AiJsonSchemaValidator {

    public List<String> validate(Map<String, Object> schema, Object value) {
        List<String> errors = new ArrayList<>();
        if (schema == null || schema.isEmpty()) {
            return errors;
        }
        validateValue("$", schema, value, errors);
        return errors;
    }

    @SuppressWarnings("unchecked")
    private void validateValue(String path, Map<String, Object> schema, Object value, List<String> errors) {
        Object typeValue = schema.get("type");
        if (!matchesType(typeValue, value)) {
            errors.add(path + " expected " + typeValue);
            return;
        }
        if (!isObjectType(typeValue)) {
            return;
        }
        if (!(value instanceof Map)) {
            errors.add(path + " expected object");
            return;
        }
        Map<String, Object> objectValue = (Map<String, Object>) value;
        Object propertiesValue = schema.get("properties");
        Map<String, Object> properties = propertiesValue instanceof Map ? (Map<String, Object>) propertiesValue : null;
        Object requiredValue = schema.get("required");
        if (requiredValue instanceof List) {
            for (Object requiredItem : (List<?>) requiredValue) {
                if (requiredItem instanceof String) {
                    String key = (String) requiredItem;
                    if (!objectValue.containsKey(key) || (objectValue.get(key) == null && !allowsNull(properties, key))) {
                        errors.add(path + "." + key + " is required");
                    }
                }
            }
        }
        if (properties == null) {
            return;
        }
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            if (!objectValue.containsKey(property.getKey()) || objectValue.get(property.getKey()) == null) {
                continue;
            }
            if (property.getValue() instanceof Map) {
                validateValue(path + "." + property.getKey(), (Map<String, Object>) property.getValue(), objectValue.get(property.getKey()), errors);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean allowsNull(Map<String, Object> properties, String key) {
        if (properties == null || !(properties.get(key) instanceof Map)) {
            return false;
        }
        Map<String, Object> propertySchema = (Map<String, Object>) properties.get(key);
        Object type = propertySchema.get("type");
        if (type instanceof String) {
            return "null".equals(type);
        }
        return type instanceof List && ((List<?>) type).contains("null");
    }

    private boolean matchesType(Object type, Object value) {
        if (type instanceof List) {
            for (Object item : (List<?>) type) {
                if (item instanceof String && matchesSingleType((String) item, value)) {
                    return true;
                }
            }
            return false;
        }
        return !(type instanceof String) || matchesSingleType((String) type, value);
    }

    private boolean matchesSingleType(String type, Object value) {
        if (value == null) {
            return "null".equals(type);
        }
        switch (type) {
            case "null":
                return false;
            case "object":
                return value instanceof Map;
            case "array":
                return value instanceof List;
            case "string":
                return value instanceof String;
            case "number":
                return value instanceof Number;
            case "integer":
                return isInteger(value);
            case "boolean":
                return value instanceof Boolean;
            default:
                return true;
        }
    }

    private boolean isObjectType(Object type) {
        if (type instanceof String) {
            return "object".equals(type);
        }
        return type instanceof List && ((List<?>) type).contains("object");
    }

    private boolean isInteger(Object value) {
        if (!(value instanceof Number)) {
            return false;
        }
        if (value instanceof Float || value instanceof Double) {
            double doubleValue = ((Number) value).doubleValue();
            return Math.rint(doubleValue) == doubleValue;
        }
        return true;
    }
}
