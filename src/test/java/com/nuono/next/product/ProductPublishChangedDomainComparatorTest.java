package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductPublishChangedDomainComparatorTest {

    private final ProductPublishChangedDomainComparator comparator =
            new ProductPublishChangedDomainComparator(new ObjectMapper());

    @Test
    void returnsUnknownWhenComparableFieldsDoNotChange() {
        ProductMasterSnapshotView baseline = snapshot();
        ProductMasterSnapshotView draft = snapshot();

        List<String> domains = comparator.resolve(draft, baseline, "SA", false, false);

        assertEquals(List.of("unknown"), domains);
        assertFalse(comparator.variantSizeChanged(draft, baseline));
    }

    @Test
    void detectsChangedDomainsInStableOrder() {
        ProductMasterSnapshotView baseline = snapshot();
        ProductMasterSnapshotView draft = snapshot();
        draft.getContent().put("titleEn", "new title");
        draft.getIdentity().put("brand", "new brand");
        draft.getKeyAttributes().get(0).put("commonValue", "cotton");
        draft.getSiteOffers().get(0).put("price", new BigDecimal("11.50"));
        draft.getVariants().get(0).put("sizeEn", "M");
        draft.getGroup().put("skuGroup", "GROUP-B");

        List<String> domains = comparator.resolve(draft, baseline, "SA", false, false);

        assertEquals(List.of("content", "main", "attributes", "site_offer", "sizes", "grouping"), domains);
        assertTrue(comparator.variantSizeChanged(draft, baseline));
    }

    @Test
    void comparesOnlyCurrentSiteOffer() {
        ProductMasterSnapshotView baseline = snapshot();
        ProductMasterSnapshotView draft = snapshot();
        draft.getSiteOffers().get(1).put("price", new BigDecimal("18.00"));

        assertEquals(List.of("unknown"), comparator.resolve(draft, baseline, "SA", false, false));
        assertEquals(List.of("site_offer"), comparator.resolve(draft, baseline, "AE", false, false));
    }

    @Test
    void includesUnsupportedChangeHints() {
        ProductMasterSnapshotView baseline = snapshot();
        ProductMasterSnapshotView draft = snapshot();

        List<String> domains = comparator.resolve(draft, baseline, "SA", true, true);

        assertEquals(List.of("grouping", "sizes"), domains);
    }

    @Test
    void detectsGroupMemberAxisValueWhenAxisCodeUsesSnakeCaseFallback() {
        ProductMasterSnapshotView baseline = snapshot();
        ProductMasterSnapshotView draft = snapshot();
        Map<String, Object> axis = new LinkedHashMap<>();
        axis.put("axisCode", "");
        axis.put("axis_code", "color");
        baseline.getGroup().put("axes", List.of(axis));
        draft.getGroup().put("axes", List.of(axis));
        baseline.getGroup().put("members", List.of(member("PAPER-001", "red")));
        draft.getGroup().put("members", List.of(member("PAPER-001", "blue")));

        List<String> domains = comparator.resolve(draft, baseline, "SA", false, false);

        assertEquals(List.of("grouping"), domains);
    }

    private ProductMasterSnapshotView snapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getIdentity().put("brand", "Papersays");
        snapshot.getTaxonomy().put("productFulltype", "stationery-paper");
        snapshot.getContent().put("titleEn", "baseline title");
        snapshot.getContent().put("images", List.of("https://cdn.example/a.jpg"));
        snapshot.getKeyAttributes().add(attribute("material", "paper"));
        snapshot.getSiteOffers().add(siteOffer("SA", "SA", "10.00"));
        snapshot.getSiteOffers().add(siteOffer("AE", "AE", "15.00"));
        snapshot.getVariants().add(variant("PAPER-001", "S", "S"));
        snapshot.getGroup().put("skuGroup", "GROUP-A");
        snapshot.getGroup().put("members", List.of(member("PAPER-001")));
        return snapshot;
    }

    private Map<String, Object> attribute(String code, String value) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("code", code);
        attribute.put("commonValue", value);
        return attribute;
    }

    private Map<String, Object> siteOffer(String storeCode, String site, String price) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", storeCode);
        offer.put("site", site);
        offer.put("price", new BigDecimal(price));
        offer.put("isActive", true);
        return offer;
    }

    private Map<String, Object> variant(String childSku, String sizeEn, String sizeAr) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("childSku", childSku);
        variant.put("sizeEn", sizeEn);
        variant.put("sizeAr", sizeAr);
        return variant;
    }

    private Map<String, Object> member(String skuParent) {
        return member(skuParent, null);
    }

    private Map<String, Object> member(String skuParent, String color) {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("skuParent", skuParent);
        if (color != null) {
            member.put("color", color);
        }
        return member;
    }
}
