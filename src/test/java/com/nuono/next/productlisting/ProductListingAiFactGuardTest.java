package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductListingAiFactGuardTest {

    @Test
    void shouldAllowReorderedGenericTitleWhenCoreFactsRemain() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleEn("30-Piece Blue Vintage Lace-Edge Scrapbooking Paper Set");

        List<String> issues = ProductListingAiFactGuard.validate(draft, Map.of(
                "productTitleEn", "Blue Vintage Lace Edge Scrapbooking Paper Set, 30 Pieces",
                "productTitleAr", "مجموعة ورق سكرابوك أزرق عتيق بحواف دانتيل من ٣٠ قطعة"
        ));

        assertTrue(issues.isEmpty());
    }

    @Test
    void shouldBlockMissingLiteralQuantityAcrossBothTitles() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleEn("12-Piece Stainless Steel Kitchen Utensil Set");

        List<String> issues = ProductListingAiFactGuard.validate(draft, Map.of(
                "productTitleEn", "Compact plastic bathroom storage organizer",
                "productTitleAr", "منظم تخزين بلاستيكي مدمج للحمام"
        ));

        assertTrue(issues.stream().anyMatch(item -> item.contains("原标题核心事实")));
        assertTrue(issues.stream().anyMatch(item -> item.contains("12")));
    }

    @Test
    void shouldNotForceStructuredAttributeIntoTitleWhenSourceTitleDidNotContainIt() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleEn("Compact Desk Organizer for Remote Controls");
        draft.setKeyAttributes(List.of(Map.of(
                "code", "base_material",
                "enValue", "Metal",
                "arValue", "معدن"
        )));

        List<String> issues = ProductListingAiFactGuard.validate(draft, Map.of(
                "productTitleEn", "Compact Desk Organizer for Daily Remote Controls",
                "productTitleAr", "منظم مكتب مدمج لأجهزة التحكم اليومية"
        ));

        assertFalse(issues.stream().anyMatch(item -> item.contains("Metal") || item.contains("معدن")));
    }

    @Test
    void shouldNotTreatProductTitleAttributeAsOneVerbatimProtectedAttribute() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setProductTitleEn("Galaxy Projector Hd Image Large Projection Area Bedroom Night Light");
        draft.setKeyAttributes(List.of(Map.of(
                "code", "product_title",
                "enValue", "Galaxy Projector Hd Image Large Projection Area Bedroom Night Light"
        )));

        List<String> issues = ProductListingAiFactGuard.validate(draft, Map.of(
                "productTitleEn", "HD Galaxy Projector with Large Projection Area and Bedroom Night Light",
                "productTitleAr", "جهاز عرض مجرة عالي الدقة بمساحة عرض كبيرة وضوء ليلي لغرفة النوم"
        ));

        assertFalse(issues.stream().anyMatch(item -> item.contains("已验证属性")));
    }
}
