package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductNoonPublishPayloadBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductNoonPublishPayloadBuilder builder = new ProductNoonPublishPayloadBuilder(objectMapper);

    @Test
    void buildsEnglishZskuPayloadForSupportedContentAndVariantSizeOnly() {
        ProductMasterSnapshotView baseline = validSnapshot();
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getContent().put("titleEn", "New title");
        draft.getContent().put("descriptionEn", "New description");
        draft.getContent().put("highlightsEn", List.of("First new bullet"));
        draft.getContent().put("images", List.of("https://img.example.com/new.jpg"));
        baseline.setKeyAttributes(new ArrayList<>(List.of(
                attribute("material", "Cotton", null),
                attribute("barcode", "OLD-BARCODE", null)
        )));
        draft.setKeyAttributes(new ArrayList<>(List.of(
                attribute("material", "Linen", "cm"),
                attribute("barcode", "NEW-BARCODE", null)
        )));
        draft.getVariants().get(0).put("sizeEn", "L");

        ProductUnsupportedChanges unsupportedChanges = new ProductUnsupportedChanges();
        unsupportedChanges.addUnsupportedAttributeCode("barcode");

        ObjectNode body = builder.buildZskuUpsertBody(draft, baseline, "en", unsupportedChanges);

        assertEquals("ZSKU-PARENT", body.path("skuParent").asText());
        assertEquals("en", body.path("lang").asText());
        ObjectNode attributes = (ObjectNode) body.path("attributes");
        assertEquals("New title", attributes.path("product_title").asText());
        assertEquals("New description", attributes.path("long_description").asText());
        assertEquals("First new bullet", attributes.path("feature_bullet_1").asText());
        assertEquals("https://img.example.com/new.jpg", attributes.path("image_url_1").asText());
        assertEquals("Linen", attributes.path("material").asText());
        assertEquals("cm", attributes.path("material_unit").asText());
        assertFalse(attributes.has("barcode"));
        assertTrue(builder.hasZskuUpsertPayloadChanges(body));
        assertEquals("CHILD-SKU-001", body.path("variants").get(0).path("sku").asText());
        assertEquals("L", body.path("variants").get(0).path("attributes").path("size").asText());
    }

    @Test
    void buildsOfferPayloadWithManualPricingAndNormalizedSaleWindow() {
        Map<String, Object> siteOffer = offer(
                "storeCode", "STR245027-NAE",
                "price", "48.00",
                "salePrice", "39.90",
                "saleStart", "2026-05-20T01:02:03Z",
                "saleEnd", "2026-06-01 10:00:00",
                "priceMin", "10.00",
                "priceMax", "55.00",
                "isActive", "active",
                "idWarranty", "7",
                "offerNote", "seller note"
        );

        ObjectNode body = builder.buildOfferUpsertBody("PSKU-001", siteOffer);

        ObjectNode offerNode = (ObjectNode) body.path("pskus").get(0);
        assertEquals("PSKU-001", offerNode.path("pskuCode").asText());
        assertEquals("ae", offerNode.path("country").asText());
        assertEquals(1, offerNode.path("isActive").asInt());
        assertEquals("manual", offerNode.path("pricingMethod").asText());
        assertEquals(0, new BigDecimal("48.00").compareTo(offerNode.path("price").decimalValue()));
        assertEquals(0, new BigDecimal("39.90").compareTo(offerNode.path("salePrice").decimalValue()));
        assertEquals(0, new BigDecimal("10.00").compareTo(offerNode.path("priceMin").decimalValue()));
        assertEquals(0, new BigDecimal("55.00").compareTo(offerNode.path("priceMax").decimalValue()));
        assertEquals("2026-05-20", offerNode.path("saleStart").asText());
        assertEquals("2026-06-01", offerNode.path("saleEnd").asText());
        assertEquals(7, offerNode.path("idWarranty").asInt());
        assertEquals("seller note", offerNode.path("offerNote").asText());
        assertTrue(offerNode.path("pricingRule").isNull());
        assertTrue(offerNode.path("priceEngineMin").isNull());
        assertTrue(offerNode.path("priceEngineMax").isNull());
    }

    @Test
    void buildsSellerSizeProductUpdatePayload() {
        ProductMasterSnapshotView baseline = validSnapshot();
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getVariants().get(0).put("sizeEn", "L");
        draft.getVariants().get(0).put("sizeAr", "كبير");
        draft.getVariants().get(0).put("variantIndex", "2");
        draft.getVariants().get(0).put("partnerSku", "PARTNER-CHILD-001");
        draft.getVariants().get(0).put("pskuCode", "PSKU-CHILD-001");
        ProductMasterSnapshotView live = copySnapshot(baseline);
        live.getTaxonomy().put("familyNameEn", "Home");
        live.getTaxonomy().put("productTypeNameEn", "Lighting");
        live.getTaxonomy().put("productSubtypeNameEn", "Lamp");

        ObjectNode body = builder.buildProductUpdateVariantSizeBody(draft, baseline, live);

        ObjectNode update = (ObjectNode) body.path("productUpdate").get(0);
        assertEquals("ZSKU-PARENT", update.path("parent").path("parentGroupKey").asText());
        assertEquals("Home", update.path("parent").path("product_fulltype").path("family").asText());
        assertEquals("Lighting", update.path("parent").path("product_fulltype").path("product_type").asText());
        assertEquals("Lamp", update.path("parent").path("product_fulltype").path("product_subtype").asText());
        ObjectNode sizeAxis = (ObjectNode) update.path("axesUpdate").get(0);
        assertEquals("Size", sizeAxis.path("axisName").asText());
        assertEquals("size", sizeAxis.path("axisCode").asText());
        assertEquals("L", sizeAxis.path("axisOptions").get(0).path("optionName").asText());
        assertEquals(2, sizeAxis.path("axisOptions").get(0).path("sortOrder").asInt());
        assertTrue(sizeAxis.path("axisOptions").get(0).path("optionLocale").asText().contains("\"ar\":\"كبير\""));
        ObjectNode childUpdate = (ObjectNode) update.path("childrenUpdate").get(0);
        assertEquals("CHILD-SKU-001", childUpdate.path("sku").asText());
        assertEquals("PARTNER-CHILD-001", childUpdate.path("partnerSku").asText());
        assertEquals("PSKU-CHILD-001", childUpdate.path("pskuCode").asText());
        assertEquals("L", childUpdate.path("size").asText());
    }

    @Test
    void buildsGroupCreateUnlinkAndAxisPayloads() {
        ProductMasterSnapshotView baseline = validSnapshot();
        baseline.setGroup(group(
                "GROUP-A",
                "12in1",
                List.of(axis("colour_name", "Colour Name")),
                List.of(
                        groupMember("GROUP-MEMBER-001", "colour_name", "Blue"),
                        groupMember("GROUP-MEMBER-002", "colour_name", "Green")
                )
        ));
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.setGroup(group(
                "GROUP-A",
                "12in1",
                List.of(axis("colour_name", "Colour Name")),
                List.of(
                        groupMember("GROUP-MEMBER-001", "colour_name", "Red"),
                        groupMember("UNGROUPED-003", "colour_name", "Yellow")
                )
        ));

        ObjectNode createBody = builder.buildGroupMemberCreateBody(
                draft.getGroup(),
                baseline.getGroup(),
                ProductGroupSnapshotSupport.addedGroupMembers(draft.getGroup(), baseline.getGroup())
        );
        assertEquals("GROUP-A", createBody.path("groupUpdate").path("group").path("skuGroup").asText());
        assertEquals("12in1", createBody.path("groupUpdate").path("group").path("partnerRef").asText());
        assertEquals("UNGROUPED-003", createBody.path("groupUpdate").path("parentsCreate").get(0).asText());
        assertEquals(0, createBody.path("groupUpdate").path("parentsDelete").size());

        ObjectNode unlinkBody = builder.buildGroupMemberDeleteBody(
                draft.getGroup(),
                baseline.getGroup(),
                ProductGroupSnapshotSupport.removedGroupMembers(draft.getGroup(), baseline.getGroup())
        );
        assertEquals("GROUP-MEMBER-002", unlinkBody.path("groupUpdate").path("parentsDelete").get(0).asText());
        assertEquals(0, unlinkBody.path("groupUpdate").path("parentsCreate").size());

        List<ObjectNode> axisBodies = builder.buildGroupAxisValueBodies(draft.getGroup(), baseline.getGroup(), "en");
        assertEquals(2, axisBodies.size());
        ObjectNode axisBody = axisBodies.stream()
                .filter((body) -> "GROUP-MEMBER-001".equals(body.path("skuParent").asText()))
                .findFirst()
                .orElseThrow();
        assertEquals("GROUP-MEMBER-001", axisBody.path("skuParent").asText());
        assertEquals("en", axisBody.path("lang").asText());
        assertEquals("Red", axisBody.path("attributes").path("colour_name").asText());
        assertEquals(0, axisBody.path("variants").size());
    }

    private ProductMasterSnapshotView validSnapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setIdentity(new LinkedHashMap<>());
        snapshot.getIdentity().put("skuParent", "ZSKU-PARENT");
        snapshot.getIdentity().put("partnerSku", "PARTNER-PARENT");
        snapshot.getIdentity().put("brand", "test-brand");

        snapshot.setTaxonomy(new LinkedHashMap<>());
        snapshot.getTaxonomy().put("productFulltype", "home_decor-lighting");

        snapshot.setContent(new LinkedHashMap<>());
        snapshot.getContent().put("titleEn", "Same title");
        snapshot.getContent().put("descriptionEn", "Same description");
        snapshot.getContent().put("highlightsEn", new ArrayList<>(List.of("Same bullet")));
        snapshot.getContent().put("images", new ArrayList<>(List.of("https://img.example.com/1.jpg")));

        snapshot.setVariants(new ArrayList<>(List.of(variant("CHILD-SKU-001", "M"))));
        return snapshot;
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    private Map<String, Object> attribute(String code, Object commonValue, String unit) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("code", code);
        attribute.put("commonValue", commonValue);
        if (unit != null) {
            attribute.put("unit", unit);
        }
        return attribute;
    }

    private Map<String, Object> variant(String childSku, String sizeEn) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("childSku", childSku);
        variant.put("sizeEn", sizeEn);
        return variant;
    }

    private Map<String, Object> offer(Object... values) {
        Map<String, Object> offer = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            offer.put(String.valueOf(values[i]), values[i + 1]);
        }
        return offer;
    }

    private Map<String, Object> group(
            String skuGroup,
            String groupRef,
            List<Map<String, Object>> axes,
            List<Map<String, Object>> members
    ) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("skuGroup", skuGroup);
        group.put("groupRef", groupRef);
        group.put("groupRefCanonical", groupRef.toUpperCase());
        group.put("axes", axes);
        group.put("members", members);
        return group;
    }

    private Map<String, Object> axis(String axisCode, String axisName) {
        Map<String, Object> axis = new LinkedHashMap<>();
        axis.put("axisCode", axisCode);
        axis.put("axisName", axisName);
        return axis;
    }

    private Map<String, Object> groupMember(String skuParent, String axisCode, String axisValue) {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("skuParent", skuParent);
        member.put("axisValues", Map.of(axisCode, axisValue));
        return member;
    }
}
