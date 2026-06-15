package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductPublishUnsupportedChangesDetectorTest {

    private final ProductPublishUnsupportedChangesDetector detector =
            new ProductPublishUnsupportedChangesDetector(new ObjectMapper());

    @Test
    void marksBarcodeAndComplexAttributesAsUnsupportedWriteCoverage() {
        ProductMasterSnapshotView baseline = snapshot();
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        baseline.setKeyAttributes(new ArrayList<>(List.of(
                attribute("barcode", "OLD-CODE"),
                attribute("material", "paper")
        )));
        draft.setKeyAttributes(new ArrayList<>(List.of(
                attribute("barcode", "NEW-CODE"),
                attribute("material", List.of("paper", "cotton"))
        )));

        ProductPublishUnsupportedChanges unsupportedChanges = detector.detect(draft, baseline, "SA");
        List<String> errors = detector.validateWriteCoverage(unsupportedChanges);

        assertTrue(unsupportedChanges.getUnsupportedAttributeCodes().contains("barcode"));
        assertTrue(unsupportedChanges.getUnsupportedAttributeCodes().contains("material"));
        assertTrue(errors.stream().anyMatch((item) -> item.contains("barcode")));
        assertTrue(errors.stream().anyMatch((item) -> item.contains("material")));
    }

    @Test
    void marksGroupDefinitionAndVariantStructureChanges() {
        ProductMasterSnapshotView baseline = snapshot();
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        baseline.getGroup().put("axes", List.of(axis("color", "Color")));
        draft.getGroup().put("axes", List.of(axis("size", "Size")));
        draft.getVariants().add(variant("CHILD-002", "M"));

        ProductPublishUnsupportedChanges unsupportedChanges = detector.detect(draft, baseline, "SA");
        List<String> errors = detector.validateWriteCoverage(unsupportedChanges);

        assertTrue(unsupportedChanges.isGroupChanged());
        assertTrue(unsupportedChanges.isVariantStructureChanged());
        assertTrue(errors.stream().anyMatch((item) -> item.contains("Group")));
        assertTrue(errors.stream().anyMatch((item) -> item.contains("尺码新增")));
    }

    @Test
    void checksUnsupportedSiteFieldsOnlyForCurrentSite() {
        ProductMasterSnapshotView baseline = snapshot();
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        baseline.setSiteOffers(new ArrayList<>(List.of(
                siteOffer("SA", "OLD-SA"),
                siteOffer("AE", "OLD-AE")
        )));
        draft.setSiteOffers(new ArrayList<>(List.of(
                siteOffer("SA", "NEW-SA"),
                siteOffer("AE", "NEW-AE")
        )));

        ProductPublishUnsupportedChanges unsupportedChanges = detector.detect(draft, baseline, "SA");

        assertTrue(unsupportedChanges.getUnsupportedSiteFields().containsKey("SA"));
        assertFalse(unsupportedChanges.getUnsupportedSiteFields().containsKey("AE"));
    }

    private ProductMasterSnapshotView snapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getGroup().put("skuGroup", "GROUP-A");
        snapshot.getVariants().add(variant("CHILD-001", "S"));
        snapshot.getSiteOffers().add(siteOffer("SA", "OLD-SA"));
        return snapshot;
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        ProductMasterSnapshotView copy = new ProductMasterSnapshotView();
        copy.setGroup(new LinkedHashMap<>(source.getGroup()));
        copy.setVariants(new ArrayList<>(source.getVariants()));
        copy.setSiteOffers(new ArrayList<>(source.getSiteOffers()));
        copy.setKeyAttributes(new ArrayList<>(source.getKeyAttributes()));
        return copy;
    }

    private Map<String, Object> attribute(String code, Object commonValue) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("code", code);
        attribute.put("commonValue", commonValue);
        return attribute;
    }

    private Map<String, Object> axis(String axisCode, String axisName) {
        Map<String, Object> axis = new LinkedHashMap<>();
        axis.put("axisCode", axisCode);
        axis.put("axisName", axisName);
        return axis;
    }

    private Map<String, Object> variant(String childSku, String sizeEn) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("childSku", childSku);
        variant.put("sizeEn", sizeEn);
        return variant;
    }

    private Map<String, Object> siteOffer(String storeCode, String barcode) {
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("storeCode", storeCode);
        siteOffer.put("barcode", barcode);
        return siteOffer;
    }
}
