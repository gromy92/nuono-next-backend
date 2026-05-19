package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductGroupSnapshotSupportTest {

    @Test
    void groupMemberSkuParentUsesPartnerSkuFallback() {
        Map<String, Object> member = Map.of("partnerSku", " MILKYWAYA20 ");

        assertEquals("MILKYWAYA20", ProductGroupSnapshotSupport.groupMemberSkuParent(member));
    }

    @Test
    void addedAndRemovedMembersCompareByNormalizedSkuParent() {
        Map<String, Object> baselineGroup = Map.of(
                "members",
                List.of(
                        Map.of("skuParent", "A"),
                        Map.of("partnerSku", "B")
                )
        );
        Map<String, Object> draftGroup = Map.of(
                "members",
                List.of(
                        Map.of("skuParent", "B"),
                        Map.of("skuParent", "C")
                )
        );

        assertEquals(List.of("C"), ProductGroupSnapshotSupport.addedGroupMembers(draftGroup, baselineGroup));
        assertEquals(List.of("A"), ProductGroupSnapshotSupport.removedGroupMembers(draftGroup, baselineGroup));
    }

    @Test
    void groupAxisValueChangesCompareConfiguredAxisOnly() {
        Map<String, Object> baselineGroup = Map.of(
                "axes",
                List.of(Map.of("axisCode", "colour_name")),
                "members",
                List.of(Map.of(
                        "skuParent", "A",
                        "axisValues", Map.of("colour_name", "blue"),
                        "axisValuesAr", Map.of("colour_name", "ازرق")
                ))
        );
        Map<String, Object> draftGroup = Map.of(
                "axes",
                List.of(Map.of("axisCode", "colour_name")),
                "members",
                List.of(Map.of(
                        "skuParent", "A",
                        "axisValues", Map.of("colour_name", "red"),
                        "axisValuesAr", Map.of("colour_name", "ازرق")
                ))
        );

        assertEquals(
                Map.of("A", Map.of("colour_name", "red")),
                ProductGroupSnapshotSupport.groupAxisValueChanges(draftGroup, baselineGroup, "en")
        );
        assertEquals(
                Map.of(),
                ProductGroupSnapshotSupport.groupAxisValueChanges(draftGroup, baselineGroup, "ar")
        );
    }
}
