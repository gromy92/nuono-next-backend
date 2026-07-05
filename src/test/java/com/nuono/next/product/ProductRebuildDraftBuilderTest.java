package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.productlisting.ProductListingDraftCommand;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductRebuildDraftBuilderTest {

    private final ProductRebuildDraftBuilder builder = new ProductRebuildDraftBuilder();

    @Test
    void shouldBuildListingDraftFromCurrentSelfBuiltSnapshotAndInheritListingStartedMetadata() {
        ProductMasterSnapshotView snapshot = snapshot("SELF_BUILT");

        ProductListingDraftCommand draft = builder.build(
                64001L,
                "STR245027-NAE",
                snapshot
        );

        assertEquals("STR245027-NAE", draft.getStoreCode());
        assertEquals("PRODUCT_REBUILD", draft.getSourceType());
        assertEquals(64001L, draft.getSourceRefId());
        assertEquals(64001L, draft.getRebuildSourceProductMasterId());
        assertEquals("MILKYWAYA17", draft.getPsku());
        assertEquals(3066L, draft.getIdProductFullType());
        assertEquals("electronic_accessories-headphones-wired_headphones", draft.getProductFullType());
        assertEquals("electronic_accessories", draft.getFamily());
        assertEquals("headphones", draft.getProductType());
        assertEquals("wired_headphones", draft.getProductSubType());
        assertEquals("Generic", draft.getProductBrand());
        assertEquals("generic", draft.getProductBrandCode());
        assertEquals("本地中文标题", draft.getProductTitleCn());
        assertEquals("Wired headphones with microphone", draft.getProductTitleEn());
        assertEquals("Arabic wired headphones title", draft.getProductTitleAr());
        assertEquals("English description", draft.getProductDescriptionEn());
        assertEquals(List.of("Long cable", "Clear voice"), draft.getProductHighlightsEn());
        assertEquals(List.of("https://example.test/image-1.jpg"), draft.getImageUrls());
        assertEquals("49.90", draft.getPrice().toPlainString());
        assertEquals("45.00", draft.getPriceMin().toPlainString());
        assertEquals("59.00", draft.getPriceMax().toPlainString());
        assertEquals("47.50", draft.getSalePrice().toPlainString());
        assertEquals("2026-07-01", draft.getSaleStart());
        assertEquals("2026-07-07", draft.getSaleEnd());
        assertEquals("19.90", draft.getPurchasePrice().toPlainString());
        assertEquals("1688_OFFER", draft.getSupplyEvidenceType());
        assertEquals(43101L, draft.getSupplyEvidenceRefId());
        assertEquals(70001L, draft.getOptionalPurchaseOrderId());
        assertEquals(24, draft.getIdWarranty());
        assertEquals(Boolean.TRUE, draft.getIsActive());
        assertEquals("Launch stock prepared.", draft.getOfferNote());
        assertEquals("6290000000001", draft.getBarcode());
        assertEquals("2026-03-12 00:00:00", draft.getInheritedListingStartedAt());
        assertEquals("pv", draft.getInheritedListingStartedSource());
    }

    @Test
    void shouldFallbackRebuildPurchaseEvidenceWhenSourceSnapshotDoesNotHavePurchaseLineage() {
        ProductMasterSnapshotView snapshot = snapshot("SELF_BUILT");
        snapshot.getPricing().clear();
        snapshot.getStock().clear();

        ProductListingDraftCommand draft = builder.build(
                64001L,
                "STR245027-NAE",
                snapshot
        );

        assertEquals("49.90", draft.getPrice().toPlainString());
        assertEquals("49.90", draft.getPurchasePrice().toPlainString());
        assertEquals("PRODUCT_REBUILD", draft.getSupplyEvidenceType());
    }

    @Test
    void shouldRejectFollowSellSnapshotForRebuild() {
        ProductMasterSnapshotView snapshot = snapshot("FOLLOW_SELL");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(64001L, "STR245027-NAE", snapshot)
        );

        assertEquals("商品重建当前只支持自建品 SELF_BUILT。", error.getMessage());
    }

    private ProductMasterSnapshotView snapshot(String productSourceType) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setReady(true);
        snapshot.getIdentity().put("productSourceType", productSourceType);
        snapshot.getIdentity().put("partnerSku", "MILKYWAYA17");
        snapshot.getIdentity().put("brand", "Generic");
        snapshot.getIdentity().put("brandCode", "generic");
        snapshot.getTaxonomy().put("idProductFullType", 3066);
        snapshot.getTaxonomy().put("productFulltype", "electronic_accessories-headphones-wired_headphones");
        snapshot.getTaxonomy().put("family", "electronic_accessories");
        snapshot.getTaxonomy().put("productType", "headphones");
        snapshot.getTaxonomy().put("productSubtype", "wired_headphones");
        snapshot.getContent().put("titleCn", "本地中文标题");
        snapshot.getContent().put("titleEn", "Wired headphones with microphone");
        snapshot.getContent().put("titleAr", "Arabic wired headphones title");
        snapshot.getContent().put("descriptionEn", "English description");
        snapshot.getContent().put("highlightsEn", List.of("Long cable", "Clear voice"));
        snapshot.getContent().put("images", List.of(
                "https://example.test/image-1.jpg",
                "original/pzsku/Z3065D60053B999AE0D32Z/45/_/1730634537/image.jpg"
        ));
        snapshot.getPricing().put("purchasePrice", "19.90");
        snapshot.getStock().put("supplyEvidenceType", "1688_OFFER");
        snapshot.getStock().put("supplyEvidenceRefId", 43101);
        snapshot.getStock().put("optionalPurchaseOrderId", 70001);
        snapshot.setKeyAttributes(List.of(Map.of(
                "code", "barcode",
                "commonValue", "6290000000001"
        )));
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("storeCode", "STR245027-NAE");
        siteOffer.put("price", "49.90");
        siteOffer.put("priceMin", "45.00");
        siteOffer.put("priceMax", "59.00");
        siteOffer.put("salePrice", "47.50");
        siteOffer.put("saleStart", "2026-07-01");
        siteOffer.put("saleEnd", "2026-07-07");
        siteOffer.put("idWarranty", 24);
        siteOffer.put("isActive", true);
        siteOffer.put("offerNote", "Launch stock prepared.");
        siteOffer.put("listingStartedAt", "2026-03-12 00:00:00");
        siteOffer.put("listingStartedSource", "pv");
        snapshot.getSiteOffers().add(siteOffer);
        return snapshot;
    }
}
