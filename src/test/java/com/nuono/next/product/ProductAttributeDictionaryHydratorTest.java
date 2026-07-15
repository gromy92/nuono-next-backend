package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductAttributeDictionaryHydratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProductAttributeTemplateService productAttributeTemplateService;

    private ProductAttributeDictionaryHydrator hydrator;

    @BeforeEach
    void setUp() {
        hydrator = new ProductAttributeDictionaryHydrator(productAttributeTemplateService);
    }

    @Test
    void shouldHydrateSnapshotAttributesFromTemplateDictionary() throws Exception {
        JsonNode template = objectMapper.readTree("{"
                + "\"_nuonoAttributeDictionary\":["
                + "{"
                + "\"code\":\"color\","
                + "\"labelEn\":\"Color\","
                + "\"labelAr\":\"لون\","
                + "\"labelZh\":\"颜色\","
                + "\"groupName\":\"Basics\","
                + "\"dictionarySource\":\"dictionary-table\","
                + "\"options\":[{\"value\":\"red\",\"en\":\"Red\",\"ar\":\"أحمر\",\"zh\":\"红色\"}]"
                + "},"
                + "{"
                + "\"code\":\"width\","
                + "\"labelEn\":\"Width\","
                + "\"unitOptions\":[\"cm\",\"m\"]"
                + "}"
                + "]"
                + "}");
        when(productAttributeTemplateService.loadTemplate(
                isNull(),
                eq("PRJ108065"),
                eq("STR245027-NAE"),
                eq("home_decor-lighting"),
                eq(10002L),
                anyList()
        )).thenReturn(template);
        ProductMasterSnapshotView snapshot = snapshot();
        Map<String, Object> color = attribute("color");
        Map<String, Object> width = attribute("width");
        snapshot.setKeyAttributes(new ArrayList<>(List.of(color, width)));

        hydrator.hydrateSnapshotAttributeDictionary(10002L, "fallback-store", snapshot, new ArrayList<>());

        assertEquals("Color", color.get("labelEn"));
        assertEquals("لون", color.get("labelAr"));
        assertEquals("颜色", color.get("labelZh"));
        assertEquals("Basics", color.get("groupName"));
        assertEquals("dictionary-table", color.get("dictionarySource"));
        assertEquals("select", color.get("kind"));
        assertEquals(List.of(Map.of("value", "red", "en", "Red", "ar", "أحمر", "zh", "红色")), color.get("options"));
        assertEquals("dimension", width.get("kind"));
        assertEquals(List.of(Map.of("value", "cm", "en", "cm"), Map.of("value", "m", "en", "m")), width.get("unitOptions"));
    }

    @Test
    void shouldHydrateBothWorkbenchSnapshots() throws Exception {
        JsonNode template = objectMapper.readTree("{"
                + "\"_nuonoAttributeDictionary\":["
                + "{\"code\":\"material\",\"labelEn\":\"Material\",\"kind\":\"textarea\"}"
                + "]"
                + "}");
        when(productAttributeTemplateService.loadTemplate(
                isNull(),
                eq("PRJ108065"),
                eq("STR245027-NAE"),
                eq("home_decor-lighting"),
                eq(10002L),
                anyList()
        )).thenReturn(template);
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        ProductMasterSnapshotView baseline = snapshot();
        ProductMasterSnapshotView draft = snapshot();
        baseline.setKeyAttributes(new ArrayList<>(List.of(attribute("material"))));
        draft.setKeyAttributes(new ArrayList<>(List.of(attribute("material"))));
        record.setBaselineSnapshot(baseline);
        record.setDraftSnapshot(draft);

        hydrator.hydrateWorkbenchAttributeDictionary(10002L, "STR245027-NAE", record, new ArrayList<>());

        assertEquals("Material", baseline.getKeyAttributes().get(0).get("labelEn"));
        assertEquals("Material", draft.getKeyAttributes().get(0).get("labelEn"));
        assertEquals("textarea", draft.getKeyAttributes().get(0).get("kind"));
    }

    @Test
    void shouldSkipTemplateLookupWhenContextIsIncomplete() {
        ProductMasterSnapshotView snapshot = snapshot();
        snapshot.getTaxonomy().clear();
        snapshot.setKeyAttributes(new ArrayList<>(List.of(attribute("color"))));

        hydrator.hydrateSnapshotAttributeDictionary(10002L, "STR245027-NAE", snapshot, new ArrayList<>());

        verify(productAttributeTemplateService, never()).loadTemplate(
                isNull(),
                eq("PRJ108065"),
                eq("STR245027-NAE"),
                eq("home_decor-lighting"),
                eq(10002L),
                anyList()
        );
    }

    private ProductMasterSnapshotView snapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getStoreContext().put("projectCode", "PRJ108065");
        snapshot.getStoreContext().put("storeCode", "STR245027-NAE");
        snapshot.getTaxonomy().put("productFulltype", "home_decor-lighting");
        return snapshot;
    }

    private Map<String, Object> attribute(String code) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("code", code);
        return attribute;
    }
}
