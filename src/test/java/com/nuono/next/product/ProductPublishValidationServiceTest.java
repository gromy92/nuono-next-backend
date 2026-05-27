package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductPublishValidationServiceTest {

    private static final String CURRENT_SITE = "STR245027-NAE";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductPublishValidationService service = new ProductPublishValidationService(
            objectMapper,
            new ProductPublishPlanner(new ProductDraftMergePolicy())
    );

    @Test
    void shouldReturnPlannerBlockerForExistingLocalImageWithoutUnsupportedCoverageLabel() {
        ProductMasterSnapshotView baseline = validSnapshot();
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        List<String> localImages = List.of("/api/product-master/image-assets/local-image.png");
        baseline.getContent().put("images", localImages);
        draft.getContent().put("images", localImages);

        ProductUnsupportedChanges unsupportedChanges = service.detectUnsupportedChanges(draft, baseline, CURRENT_SITE);
        ProductMasterSnapshotView publishable =
                service.publishableSnapshotForSupportedChanges(draft, baseline, unsupportedChanges);
        ProductPublishValidationResult result = service.validate(publishable, baseline, CURRENT_SITE, unsupportedChanges);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().contains("本地上传图片仍是系统相对地址，不能发布到 Noon。"));
        assertFalse(result.getErrors().stream().anyMatch((error) ->
                error.contains("本地上传图片") && error.contains("当前没有 Noon 写回适配")));
    }

    @Test
    void shouldReturnRequiredSharedFieldErrorsWithoutNullPointerOnPartialSnapshots() {
        ProductMasterSnapshotView partial = new ProductMasterSnapshotView();
        partial.setIdentity(null);
        partial.setTaxonomy(null);
        partial.setContent(null);
        partial.setSiteOffers(null);

        List<String> errors = service.validateSnapshotOnly(partial, null, CURRENT_SITE);

        assertTrue(errors.contains("共享主档缺少标题 EN。"));
        assertTrue(errors.contains("共享主档缺少品牌。"));
        assertTrue(errors.contains("共享主档缺少 Fulltype。"));
        assertTrue(errors.contains("共享主档至少需要保留 1 张图片。"));
    }

    @Test
    void shouldReturnCurrentSitePriceValidationErrorsOnly() {
        ProductMasterSnapshotView baseline = validSnapshot();
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getSiteOffers().get(0).put("price", "40.00");
        draft.getSiteOffers().get(0).put("salePrice", "45.00");
        draft.getSiteOffers().get(0).put("priceMin", "41.00");
        draft.getSiteOffers().get(0).put("priceMax", "39.00");
        draft.getSiteOffers().add(offer(
                "storeCode", "STR245027-NSA",
                "site", "SA",
                "price", "0",
                "salePrice", "1",
                "pskuCode", "PSKU-SA"
        ));

        List<String> errors = service.validateSnapshotOnly(draft, baseline, CURRENT_SITE);

        assertTrue(errors.contains("AE / STR245027-NAE 的促销价不能高于原价。"));
        assertTrue(errors.contains("AE / STR245027-NAE 的售价低于允许范围。"));
        assertTrue(errors.contains("AE / STR245027-NAE 的售价高于允许范围。"));
        assertFalse(errors.stream().anyMatch((error) -> error.contains("STR245027-NSA")));
    }

    @Test
    void shouldDetectBarcodeAttributeAndVariantStructureAsUnsupported() {
        ProductMasterSnapshotView baseline = validSnapshot();
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        baseline.setKeyAttributes(new ArrayList<>(List.of(attribute("barcode", "OLD-CODE"))));
        draft.setKeyAttributes(new ArrayList<>(List.of(attribute("barcode", "NEW-CODE"))));
        draft.getVariants().add(variant("CHILD-SKU-002", "L"));

        ProductUnsupportedChanges unsupportedChanges = service.detectUnsupportedChanges(draft, baseline, CURRENT_SITE);

        assertTrue(unsupportedChanges.isVariantStructureChanged());
        assertTrue(unsupportedChanges.getUnsupportedAttributeCodes().contains("barcode"));
    }

    @Test
    void shouldAllowSupportedGroupMemberAxisAddAndUnlinkChanges() {
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

        ProductUnsupportedChanges unsupportedChanges = service.detectUnsupportedChanges(draft, baseline, CURRENT_SITE);

        assertFalse(unsupportedChanges.isGroupChanged());
        assertEquals(List.of(), service.validateWriteCoverageOnly(unsupportedChanges));
    }

    @Test
    void shouldDetectUnsupportedGroupDefinitionCreateAndDeleteChanges() {
        ProductMasterSnapshotView baseline = validSnapshot();
        Map<String, Object> baselineGroup = group(
                "GROUP-A",
                "12in1",
                List.of(axis("colour_name", "Colour Name")),
                List.of(groupMember("GROUP-MEMBER-001", "colour_name", "Blue"))
        );
        baseline.setGroup(baselineGroup);

        ProductMasterSnapshotView switchedGroupDraft = copySnapshot(baseline);
        switchedGroupDraft.setGroup(group(
                "GROUP-B",
                "24in1",
                List.of(axis("colour_name", "Colour Name")),
                List.of(groupMember("GROUP-MEMBER-001", "colour_name", "Blue"))
        ));
        assertUnsupportedGroupDefinitionChange(switchedGroupDraft, baseline);

        ProductMasterSnapshotView axisDefinitionDraft = copySnapshot(baseline);
        axisDefinitionDraft.setGroup(group(
                "GROUP-A",
                "12in1",
                List.of(
                        axis("colour_name", "Colour Name"),
                        axis("size_name", "Size Name")
                ),
                List.of(groupMember("GROUP-MEMBER-001", "colour_name", "Blue"))
        ));
        assertUnsupportedGroupDefinitionChange(axisDefinitionDraft, baseline);

        ProductMasterSnapshotView createdGroupDraft = copySnapshot(baseline);
        createdGroupDraft.setGroup(baselineGroup);
        ProductMasterSnapshotView ungroupedBaseline = copySnapshot(baseline);
        ungroupedBaseline.setGroup(Map.of());
        assertUnsupportedGroupDefinitionChange(createdGroupDraft, ungroupedBaseline);

        ProductMasterSnapshotView deletedGroupDraft = copySnapshot(baseline);
        deletedGroupDraft.setGroup(Map.of());
        assertUnsupportedGroupDefinitionChange(deletedGroupDraft, baseline);
    }

    @Test
    void shouldReturnWriteCoverageErrorsForUnsupportedChanges() {
        ProductUnsupportedChanges unsupportedChanges = new ProductUnsupportedChanges();
        unsupportedChanges.setGroupChanged(true);
        unsupportedChanges.setVariantStructureChanged(true);
        unsupportedChanges.addUnsupportedAttributeCode("barcode");
        unsupportedChanges.addUnsupportedAttributeCode("");
        unsupportedChanges.markUnsupportedSiteField(CURRENT_SITE, "barcode");
        unsupportedChanges.markUnsupportedSiteField("", "ignored");
        unsupportedChanges.markUnsupportedSiteField(CURRENT_SITE, "");
        unsupportedChanges.addPublishBlocker("planner blocker");
        unsupportedChanges.addPublishBlocker("");

        List<String> errors = service.validateWriteCoverageOnly(unsupportedChanges);

        assertTrue(errors.contains("planner blocker"));
        assertTrue(errors.contains("Group 换组或轴定义当前暂未开放 Noon 写回；本期支持已有成员 Group 轴属性值、新增未分组商品和 Unlink。"));
        assertTrue(errors.contains("尺码新增、删除或 Child SKU 变更当前没有 Noon 写回适配，请撤回这类修改后再发布。"));
        assertTrue(errors.contains("关键属性 barcode 当前没有 Noon 写回适配，请撤回这类修改后再发布。"));
        assertTrue(errors.contains("STR245027-NAE 的 barcode 当前没有 Noon 写回适配，或属于 Noon 只读/汇总字段。"));
        assertEquals(1, unsupportedChanges.getUnsupportedSiteFields().size());
        assertEquals(Set.of("barcode"), unsupportedChanges.getUnsupportedAttributeCodes());
        assertEquals(List.of("planner blocker"), unsupportedChanges.getPublishBlockers());
        assertThrows(UnsupportedOperationException.class, () ->
                unsupportedChanges.getUnsupportedAttributeCodes().add("another"));
        assertThrows(UnsupportedOperationException.class, () ->
                unsupportedChanges.getPublishBlockers().add("another"));
        assertThrows(UnsupportedOperationException.class, () ->
                unsupportedChanges.getUnsupportedSiteFields().put("STR245027-NSA", Set.of("barcode")));
        assertThrows(UnsupportedOperationException.class, () ->
                unsupportedChanges.getUnsupportedSiteFields().get(CURRENT_SITE).add("statusCode"));
    }

    @Test
    void shouldAggregateSnapshotOperationalAndCoverageValidationOnce() {
        ProductMasterSnapshotView baseline = validSnapshot();
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getSiteOffers().get(0).put("price", "49.00");
        draft.getSiteOffers().get(0).remove("pskuCode");
        baseline.getSiteOffers().get(0).remove("pskuCode");
        ProductUnsupportedChanges unsupportedChanges = new ProductUnsupportedChanges();
        unsupportedChanges.addPublishBlockers(List.of("coverage blocker", "", "  "));

        ProductPublishValidationResult result = service.validate(draft, baseline, CURRENT_SITE, unsupportedChanges);

        assertEquals(1, frequency(result.getErrors(), "AE / STR245027-NAE 缺少 pskuCode，暂时不能发布当前站点经营字段。"));
        assertEquals(1, frequency(result.getErrors(), "coverage blocker"));
    }

    @Test
    void resultShouldDefensivelyCopyAndExposeUnmodifiableErrors() {
        List<String> source = new ArrayList<>();
        source.add("first");
        ProductPublishValidationResult result = new ProductPublishValidationResult(source);
        source.add("second");

        assertTrue(result.hasErrors());
        assertEquals(List.of("first"), result.getErrors());
        assertThrows(UnsupportedOperationException.class, () -> result.getErrors().add("third"));
    }

    private ProductMasterSnapshotView validSnapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setIdentity(new LinkedHashMap<>());
        snapshot.getIdentity().put("skuParent", "Z580978E7ED8F9491B50BZ");
        snapshot.getIdentity().put("partnerSku", "MILKYWAYA01");
        snapshot.getIdentity().put("brand", "test-brand");

        snapshot.setTaxonomy(new LinkedHashMap<>());
        snapshot.getTaxonomy().put("productFulltype", "home_decor-lighting");

        snapshot.setContent(new LinkedHashMap<>());
        snapshot.getContent().put("titleEn", "Same title");
        snapshot.getContent().put("descriptionEn", "Same description");
        snapshot.getContent().put("images", new ArrayList<>(List.of("https://img.example.com/1.jpg")));

        snapshot.setVariants(new ArrayList<>(List.of(variant("CHILD-SKU-001", "M"))));
        snapshot.setSiteOffers(new ArrayList<>(List.of(offer(
                "storeCode", CURRENT_SITE,
                "site", "AE",
                "price", "48.00",
                "salePrice", "39.90",
                "priceMin", "10.00",
                "priceMax", "55.00",
                "pskuCode", "PSKU-001"
        ))));
        return snapshot;
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    private Map<String, Object> offer(Object... values) {
        Map<String, Object> offer = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            offer.put(String.valueOf(values[i]), values[i + 1]);
        }
        return offer;
    }

    private Map<String, Object> attribute(String code, Object commonValue) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("code", code);
        attribute.put("commonValue", commonValue);
        return attribute;
    }

    private Map<String, Object> variant(String childSku, String sizeEn) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("childSku", childSku);
        variant.put("sizeEn", sizeEn);
        return variant;
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
        group.put("conditionsBrand", "test-brand");
        group.put("conditionsFulltype", "home_decor-lighting");
        group.put("axes", axes);
        group.put("members", members);
        group.put("memberCount", members.size());
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

    private void assertUnsupportedGroupDefinitionChange(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline
    ) {
        ProductUnsupportedChanges unsupportedChanges = service.detectUnsupportedChanges(draft, baseline, CURRENT_SITE);

        assertTrue(unsupportedChanges.isGroupChanged());
        assertTrue(service.validateWriteCoverageOnly(unsupportedChanges).contains(
                "Group 换组或轴定义当前暂未开放 Noon 写回；本期支持已有成员 Group 轴属性值、新增未分组商品和 Unlink。"
        ));
    }

    private long frequency(List<String> errors, String expected) {
        return errors.stream().filter(expected::equals).count();
    }
}
