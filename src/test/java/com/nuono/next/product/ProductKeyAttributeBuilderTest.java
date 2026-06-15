package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductKeyAttributeBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductKeyAttributeBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ProductKeyAttributeBuilder(objectMapper);
    }

    @Test
    void shouldBuildKeyAttributesFromTemplateAndDictionary() throws Exception {
        JsonNode templateRoot = objectMapper.readTree("{"
                + "\"data\":{\"fundamental\":{"
                + "\"attribute_class\":{\"mandatory\":[\"brand\",\"width\"]},"
                + "\"attribute_properties\":{"
                + "\"brand\":{"
                + "\"attribute_name\":{\"en\":\"Brand Official\",\"ar\":\"علامة\"},"
                + "\"attribute_group_name\":{\"en\":\"Basics\"}"
                + "},"
                + "\"colour_name\":{"
                + "\"is_grouping\":1,"
                + "\"attribute_name_en\":\"Colour\","
                + "\"options\":[{\"value\":\"red\",\"option_name\":{\"en\":\"Red\",\"ar\":\"أحمر\"}}]"
                + "},"
                + "\"material\":{"
                + "\"is_visible_seller\":1,"
                + "\"value_type\":\"long_text\""
                + "},"
                + "\"width\":{"
                + "\"unit_options\":[\"cm\",\"m\"]"
                + "}"
                + "}"
                + "}},"
                + "\"_nuonoAttributeDictionary\":["
                + "{"
                + "\"code\":\"item_condition\","
                + "\"required\":true,"
                + "\"labelEn\":\"Condition\","
                + "\"kind\":\"select\","
                + "\"options\":[{\"value\":\"new\",\"en\":\"New\"}],"
                + "\"dictionarySource\":\"dictionary-table\""
                + "},"
                + "{"
                + "\"code\":\"pattern\","
                + "\"visibleSeller\":true,"
                + "\"labelEn\":\"Pattern\","
                + "\"kind\":\"textarea\""
                + "}"
                + "]"
                + "}");
        JsonNode commonNode = objectMapper.readTree("{"
                + "\"brand\":\"Acme\","
                + "\"colour_name\":\"red\","
                + "\"width\":10,"
                + "\"width_unit\":\"cm\","
                + "\"item_condition\":\"new\""
                + "}");
        JsonNode enNode = objectMapper.readTree("{"
                + "\"material\":\"Cotton\""
                + "}");
        JsonNode arNode = objectMapper.readTree("{"
                + "\"brand\":\"أكمي\""
                + "}");

        List<Map<String, Object>> attributes = builder.buildKeyAttributes(
                templateRoot,
                commonNode,
                enNode,
                arNode
        );

        Map<String, Object> brand = attribute(attributes, "brand");
        assertEquals(true, brand.get("required"));
        assertEquals("text", brand.get("kind"));
        assertEquals("Brand Official", brand.get("labelEn"));
        assertEquals("علامة", brand.get("labelAr"));
        assertEquals("Basics", brand.get("groupName"));
        assertEquals("Acme", brand.get("commonValue"));
        assertEquals("أكمي", brand.get("arValue"));

        Map<String, Object> colour = attribute(attributes, "colour_name");
        assertEquals(true, colour.get("grouping"));
        assertEquals("select", colour.get("kind"));
        assertEquals("Colour", colour.get("labelEn"));
        assertEquals(List.of(Map.of("value", "red", "en", "Red", "ar", "أحمر")), colour.get("options"));
        assertEquals("official-template", colour.get("dictionarySource"));
        assertEquals("red", colour.get("commonValue"));

        Map<String, Object> material = attribute(attributes, "material");
        assertEquals(true, material.get("visibleSeller"));
        assertEquals("textarea", material.get("kind"));
        assertEquals("Cotton", material.get("enValue"));

        Map<String, Object> width = attribute(attributes, "width");
        assertEquals(true, width.get("required"));
        assertEquals("dimension", width.get("kind"));
        assertEquals("10", width.get("commonValue"));
        assertEquals("cm", width.get("unit"));
        assertEquals(List.of(Map.of("value", "cm", "en", "cm"), Map.of("value", "m", "en", "m")), width.get("unitOptions"));

        Map<String, Object> condition = attribute(attributes, "item_condition");
        assertEquals(true, condition.get("required"));
        assertEquals("select", condition.get("kind"));
        assertEquals("Item Condition", condition.get("labelEn"));
        assertEquals(List.of(Map.of("value", "new", "en", "New")), condition.get("options"));
        assertEquals("dictionary-table", condition.get("dictionarySource"));

        Map<String, Object> pattern = attribute(attributes, "pattern");
        assertEquals(true, pattern.get("visibleSeller"));
        assertEquals("textarea", pattern.get("kind"));
        assertEquals("Pattern", pattern.get("labelEn"));
    }

    @Test
    void shouldUseDefaultCandidateCodesWhenTemplateHasNoAttributes() throws Exception {
        JsonNode templateRoot = objectMapper.readTree("{}");
        JsonNode commonNode = objectMapper.readTree("{\"brand\":\"Acme\",\"colour_family\":\"Red\"}");
        JsonNode enNode = objectMapper.readTree("{\"model_name\":\"Tray 100\"}");
        JsonNode arNode = objectMapper.readTree("{}");

        List<Map<String, Object>> attributes = builder.buildKeyAttributes(
                templateRoot,
                commonNode,
                enNode,
                arNode
        );

        assertEquals("Acme", attribute(attributes, "brand").get("commonValue"));
        assertEquals("Tray 100", attribute(attributes, "model_name").get("enValue"));
        assertEquals("Red", attribute(attributes, "colour_family").get("commonValue"));
    }

    private Map<String, Object> attribute(List<Map<String, Object>> attributes, String code) {
        Map<String, Object> attribute = attributes.stream()
                .filter(item -> code.equals(item.get("code")))
                .findFirst()
                .orElse(null);
        assertNotNull(attribute, "missing attribute " + code);
        return attribute;
    }
}
