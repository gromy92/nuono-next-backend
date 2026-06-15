package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductSnapshotSectionBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductSnapshotSectionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ProductSnapshotSectionBuilder(objectMapper);
    }

    @Test
    void shouldBuildIdentityTaxonomyContentAndPlatformSignals() throws Exception {
        JsonNode productNode = objectMapper.readTree("{"
                + "\"parent_sku\":\"PARENT-FROM-NOON\","
                + "\"variants\":[{\"sku\":\"CHILD-SNAPSHOT\"}]"
                + "}");
        JsonNode commonNode = objectMapper.readTree("{"
                + "\"brand\":\"Acme\","
                + "\"barcode\":\"6291000000012\","
                + "\"family\":\"home\","
                + "\"family_option_name_en\":\"Home\","
                + "\"family_option_name_ar\":\"بيت\","
                + "\"product_type\":\"tray\","
                + "\"product_type_option_name_en\":\"Tray\","
                + "\"product_subtype\":\"decorative\","
                + "\"product_fulltype\":\"home-decorative-tray\","
                + "\"grade\":\"new\","
                + "\"item_condition\":\"new\","
                + "\"image_url_1\":\"https://cdn.example/a.jpg\","
                + "\"original_1\":\"https://cdn.example/a.jpg\","
                + "\"original_2\":\"https://cdn.example/b.jpg\","
                + "\"is_hidden_1\":true,"
                + "\"is_hidden_2\":\"1\","
                + "\"qc_state\":\"approved\","
                + "\"status_images\":2,"
                + "\"noon_qc_rejection_reasons_localized\":[\"bad title\"],"
                + "\"is_active_localized_affecting_attributes\":[\"brand\"]"
                + "}");
        JsonNode enNode = objectMapper.readTree("{"
                + "\"product_title\":\"Tray EN\","
                + "\"full_product_title\":\"Full Tray EN\","
                + "\"long_description\":\"Description EN\","
                + "\"feature_bullet_1\":\"One\","
                + "\"feature_bullet_3\":\"Three\""
                + "}");
        JsonNode arNode = objectMapper.readTree("{"
                + "\"product_title\":\"Tray AR\","
                + "\"long_description\":\"Description AR\","
                + "\"feature_bullet_1\":\"واحد\""
                + "}");
        JsonNode pricingRoot = objectMapper.readTree("{\"data\":[{"
                + "\"psku\":\"PARTNER-LIVE\","
                + "\"sku\":\"CHILD-LIVE\","
                + "\"offer_code\":\"OFF-1\""
                + "}]}");

        Map<String, Object> identity = builder.buildIdentity(
                productNode,
                commonNode,
                pricingRoot,
                "SKU-PARENT",
                "PARTNER-PASSED",
                "PSKU-1"
        );
        Map<String, Object> taxonomy = builder.buildTaxonomy(commonNode);
        Map<String, Object> content = builder.buildContent(commonNode, enNode, arNode);
        Map<String, Object> platformSignals = builder.buildPlatformSignals(commonNode);

        assertEquals("SKU-PARENT", identity.get("skuParent"));
        assertEquals("PARENT-FROM-NOON", identity.get("parentSku"));
        assertEquals("PARTNER-PASSED", identity.get("partnerSku"));
        assertEquals("PSKU-1", identity.get("pskuCode"));
        assertEquals("Acme", identity.get("brand"));
        assertEquals("6291000000012", identity.get("barcode"));
        assertEquals("CHILD-LIVE", identity.get("childSku"));
        assertEquals("OFF-1", identity.get("offerCode"));
        assertEquals(1, identity.get("variantCount"));

        assertEquals("home", taxonomy.get("family"));
        assertEquals("Home", taxonomy.get("familyNameEn"));
        assertEquals("بيت", taxonomy.get("familyNameAr"));
        assertEquals("home-decorative-tray", taxonomy.get("productFulltype"));

        assertEquals("Tray EN", content.get("titleEn"));
        assertEquals("Tray AR", content.get("titleAr"));
        assertEquals("Description EN", content.get("descriptionEn"));
        assertEquals(List.of("One", "Three"), content.get("highlightsEn"));
        assertEquals(List.of("https://cdn.example/a.jpg", "https://cdn.example/b.jpg"), content.get("images"));
        assertEquals(2, content.get("imageCount"));

        assertEquals("approved", platformSignals.get("qcState"));
        assertEquals(2L, platformSignals.get("statusImages"));
        assertEquals(2, platformSignals.get("imageCount"));
        assertEquals(2, platformSignals.get("hiddenImageCount"));
        assertEquals(List.of("bad title"), platformSignals.get("rejectionReasons"));
        assertEquals(List.of("brand"), platformSignals.get("affectingAttributes"));
    }

    @Test
    void shouldBuildGroupBodyGroupSummaryAndMergedVariants() throws Exception {
        JsonNode groupDetailRoot = objectMapper.readTree("{\"GRP-1\":{"
                + "\"group\":{\"group_ref\":\"REF-1\",\"group_ref_canonical\":\"REF-CANON\"},"
                + "\"conditions\":{\"brand\":\"Acme\",\"fulltype\":\"home-decorative-tray\"},"
                + "\"axes\":[{\"axis_code\":\"colour_name\",\"axis_name\":\"Colour\"}],"
                + "\"zsku_parents\":[{\"sku_parent\":\"PARENT-1\",\"title\":\"Red tray\",\"image_url\":\"https://cdn.example/red.jpg\"}]"
                + "}}");
        JsonNode groupListNode = objectMapper.readTree("[{\"zsku_group\":\"GRP-1\",\"group_ref\":\"REF-1\",\"brand\":\"Acme\",\"zsku_parents\":[\"PARENT-1\"]}]");
        JsonNode groupParentAttributesRoot = objectMapper.readTree("{\"PARENT-1\":{\"attributes\":{"
                + "\"common\":{\"colour_name\":\"Red\"},"
                + "\"ar\":{\"colour_name\":\"أحمر\"}"
                + "}}}");
        JsonNode variantInfoRoot = objectMapper.readTree("{\"CHILD-1\":{"
                + "\"partner_sku\":\"PARTNER-1\","
                + "\"psku_code\":\"PSKU-1\","
                + "\"size\":{\"en\":\"Small\",\"ar\":\"صغير\"},"
                + "\"ix\":2"
                + "}}");
        JsonNode productNode = objectMapper.readTree("{\"variants\":[{"
                + "\"sku\":\"CHILD-1\","
                + "\"seller_size_en\":\"S\""
                + "},{"
                + "\"sku\":\"CHILD-2\","
                + "\"partner_sku\":\"PARTNER-2\","
                + "\"seller_size_en\":\"M\""
                + "}]}");

        JsonNode groupBody = builder.buildGroupParentAttributeFetchBody(groupDetailRoot, "GRP-1");
        Map<String, Object> group = builder.buildGroup(
                objectMapper.readTree("{}"),
                groupDetailRoot,
                groupListNode,
                "GRP-1",
                groupParentAttributesRoot
        );
        List<Map<String, Object>> variants = builder.buildVariants(variantInfoRoot, productNode);

        assertNotNull(groupBody);
        assertEquals("PARENT-1", groupBody.path("skuParents").path(0).asText());
        assertEquals("colour_name", groupBody.path("attributeCodes").path(0).asText());
        assertEquals("GRP-1", group.get("skuGroup"));
        assertEquals("REF-1", group.get("groupRef"));
        assertEquals(1, group.get("memberCount"));
        assertEquals(1, group.get("candidateGroupCount"));
        List<?> members = (List<?>) group.get("members");
        Map<?, ?> member = (Map<?, ?>) members.get(0);
        assertEquals("PARENT-1", member.get("skuParent"));
        assertEquals("Red", member.get("colour_name"));
        assertEquals(Map.of("colour_name", "Red"), member.get("axisValues"));
        assertEquals(Map.of("colour_name", "أحمر"), member.get("axisValuesAr"));

        assertEquals(2, variants.size());
        assertEquals("CHILD-1", variants.get(0).get("childSku"));
        assertEquals("PARTNER-1", variants.get(0).get("partnerSku"));
        assertEquals("PSKU-1", variants.get(0).get("pskuCode"));
        assertEquals("Small", variants.get(0).get("sizeEn"));
        assertEquals("صغير", variants.get(0).get("sizeAr"));
        assertEquals(2, variants.get(0).get("variantIndex"));
        assertEquals("CHILD-2", variants.get(1).get("childSku"));
        assertEquals("PARTNER-2", variants.get(1).get("partnerSku"));
        assertEquals("M", variants.get(1).get("sizeEn"));
    }
}
