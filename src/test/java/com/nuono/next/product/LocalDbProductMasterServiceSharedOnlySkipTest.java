package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalDbProductMasterServiceSharedOnlySkipTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final LocalDbProductMasterService service = new LocalDbProductMasterService(
            null,
            null,
            null,
            objectMapper,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    @Test
    void shouldIgnoreUnsupportedStockAndStatusDriftForSharedOnlySkipGate() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Baseline title", "STR245027-NAE", "188.00", "168.00", 33, 0, "LIVE");
        ProductMasterSnapshotView draft = snapshot("Draft title", "STR245027-NAE", "188.00", "168.00", 32, 1, "NOT_LIVE");

        assertTrue(invokeShouldSkip(draft, baseline, "STR245027-NAE"));
    }

    @Test
    void shouldStillRequireSupportedCurrentSiteOfferFieldsToMatch() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Baseline title", "STR245027-NAE", "188.00", "168.00", 33, 0, "LIVE");
        ProductMasterSnapshotView draft = snapshot("Draft title", "STR245027-NAE", "199.00", "168.00", 33, 0, "LIVE");

        assertFalse(invokeShouldSkip(draft, baseline, "STR245027-NAE"));
    }

    @Test
    void shouldNormalizeOfferDatesForPublishComparable() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "188.00", "168.00", 33, 0, "LIVE");
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        baseline.getSiteOffers().get(0).put("saleStart", "2026-04-13 00:00:00");
        baseline.getSiteOffers().get(0).put("saleEnd", "2026-04-30T23:59:59+00:00");
        draft.getSiteOffers().get(0).put("saleStart", "2026-04-13");
        draft.getSiteOffers().get(0).put("saleEnd", "2026-04-30");

        assertTrue(invokeSameScopedSnapshot(draft, baseline, "STR245027-NAE"));
    }

    @Test
    void shouldIgnoreLocalTranslationHelperFieldsForPublishComparable() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "188.00", "168.00", 33, 0, "LIVE");
        ProductMasterSnapshotView draft = copySnapshot(baseline);

        draft.getContent().put("titleCn", "中文标题");
        draft.getContent().put("descriptionCn", "中文长描述");
        draft.getContent().put("highlightsZh", List.of("中文卖点"));

        assertTrue(invokeSameScopedSnapshot(draft, baseline, "STR245027-NAE"));
    }

    @Test
    void shouldIgnoreIdentityBarcodeMirrorWhenAttributeValueIsUnchanged() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "188.00", "168.00", 33, 0, "LIVE");
        ProductMasterSnapshotView draft = copySnapshot(baseline);

        draft.getIdentity().put("barcode", "MILKYWAYA01");
        draft.getIdentity().put("barcodes", List.of("MILKYWAYA01"));

        assertTrue(invokeSameScopedSnapshot(draft, baseline, "STR245027-NAE"));
    }

    @Test
    void shouldStillTrackWritableAttributeValueChanges() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "188.00", "168.00", 33, 0, "LIVE");
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        Map<String, Object> baselineAttribute = new LinkedHashMap<>();
        baselineAttribute.put("code", "base_material");
        baselineAttribute.put("labelEn", "Base Material");
        baselineAttribute.put("commonValue", "Plastic");
        baselineAttribute.put("options", List.of("Plastic", "Metal"));
        Map<String, Object> draftAttribute = new LinkedHashMap<>(baselineAttribute);
        draftAttribute.put("commonValue", "Metal");
        draftAttribute.put("options", List.of("Metal", "Plastic"));
        baseline.setKeyAttributes(new ArrayList<>(List.of(baselineAttribute)));
        draft.setKeyAttributes(new ArrayList<>(List.of(draftAttribute)));

        assertFalse(invokeSameScopedSnapshot(draft, baseline, "STR245027-NAE"));
    }

    @Test
    void shouldBlockBarcodeAttributeWriteCoverage() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "188.00", "168.00", 33, 0, "LIVE");
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        Map<String, Object> baselineAttribute = new LinkedHashMap<>();
        baselineAttribute.put("code", "barcode");
        baselineAttribute.put("commonValue", "OLD-CODE");
        Map<String, Object> draftAttribute = new LinkedHashMap<>(baselineAttribute);
        draftAttribute.put("commonValue", "OLD-CODE,NEW-CODE");
        baseline.setKeyAttributes(new ArrayList<>(List.of(baselineAttribute)));
        draft.setKeyAttributes(new ArrayList<>(List.of(draftAttribute)));

        Object unsupportedChanges = invokeDetectUnsupportedChanges(draft, baseline, "STR245027-NAE");
        List<String> errors = invokeValidatePublishWriteCoverage(unsupportedChanges);

        assertTrue(errors.stream().anyMatch((item) -> item.contains("barcode")));
    }

    @Test
    void shouldBlockLocalUploadedImagePublish() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "188.00", "168.00", 33, 0, "LIVE");
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getContent().put(
                "images",
                List.of(
                        "https://img.example.com/1.jpg",
                        "/api/product-master/image-assets/local-image.png"
                )
        );

        List<String> errors = invokeValidatePublishSnapshot(draft, baseline, "STR245027-NAE");

        assertTrue(errors.stream().anyMatch((item) -> item.contains("本地上传图片")));
    }

    @Test
    void shouldReturnClearPublishPlannerBlockerMessageForLocalImages() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "188.00", "168.00", 33, 0, "LIVE");
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        List<String> localImages = List.of("/api/product-master/image-assets/local-image.png");
        baseline.getContent().put("images", localImages);
        draft.getContent().put("images", localImages);

        Object unsupportedChanges = invokeDetectUnsupportedChanges(draft, baseline, "STR245027-NAE");
        invokePublishableSnapshotForSupportedChanges(draft, baseline, unsupportedChanges);
        List<String> errors = invokeValidatePublishWriteCoverage(unsupportedChanges);

        assertTrue(errors.contains("本地上传图片仍是系统相对地址，不能发布到 Noon。"));
        assertFalse(errors.stream().anyMatch((item) ->
                item.contains("本地上传图片") && item.contains("当前没有 Noon 写回适配")));
    }

    @Test
    void shouldDefaultMissingSaleWindowToTenYearsWhenPublishingSalePrice() throws Exception {
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("salePrice", "39.20");

        Map<String, String> saleWindow = invokeSaleWindowForPublish(siteOffer);

        LocalDate saleStart = LocalDate.parse(saleWindow.get("saleStart"));
        LocalDate saleEnd = LocalDate.parse(saleWindow.get("saleEnd"));
        assertTrue(!saleStart.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1)));
        assertTrue(!saleStart.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1)));
        assertTrue(saleEnd.equals(saleStart.plusYears(10)));
    }

    @Test
    void shouldPreserveExistingSaleWindowWhenPublishingSalePrice() throws Exception {
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("salePrice", "39.20");
        siteOffer.put("saleStart", "2025-10-20T00:00:00+00:00");
        siteOffer.put("saleEnd", "2025-11-30 23:59:59");

        Map<String, String> saleWindow = invokeSaleWindowForPublish(siteOffer);

        assertTrue("2025-10-20".equals(saleWindow.get("saleStart")));
        assertTrue("2025-11-30".equals(saleWindow.get("saleEnd")));
    }

    @Test
    void shouldPreserveBaselineOfferFieldsWhenPublishDraftOmitsThem() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "48.00", "39.20", 33, 0, "LIVE");
        Map<String, Object> baselineOffer = baseline.getSiteOffers().get(0);
        baselineOffer.put("priceMin", "10.00");
        baselineOffer.put("priceMax", "55.00");
        baselineOffer.put("saleStart", "2025-10-20T00:00:00+00:00");
        baselineOffer.put("saleEnd", "2025-11-30T23:59:59+00:00");
        baselineOffer.put("isActive", true);

        ProductMasterSnapshotView draft = copySnapshot(baseline);
        Map<String, Object> draftOffer = draft.getSiteOffers().get(0);
        draftOffer.put("priceMin", "9.13");
        draftOffer.remove("saleStart");
        draftOffer.remove("saleEnd");
        draftOffer.remove("isActive");

        ProductMasterSnapshotView prepared = invokePrepareSnapshotForPublish(draft, baseline, "STR245027-NAE");
        Map<String, Object> preparedOffer = prepared.getSiteOffers().get(0);

        assertEquals("2025-10-20T00:00:00+00:00", preparedOffer.get("saleStart"));
        assertEquals("2025-11-30T23:59:59+00:00", preparedOffer.get("saleEnd"));
        assertEquals(true, preparedOffer.get("isActive"));
        assertEquals("9.13", preparedOffer.get("priceMin"));
    }

    @Test
    void shouldKeepExplicitBlankSaleFieldsWhenPublishDraftClearsThem() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "48.00", "39.20", 33, 0, "LIVE");
        Map<String, Object> baselineOffer = baseline.getSiteOffers().get(0);
        baselineOffer.put("saleStart", "2025-10-20T00:00:00+00:00");
        baselineOffer.put("saleEnd", "2025-11-30T23:59:59+00:00");

        ProductMasterSnapshotView draft = copySnapshot(baseline);
        Map<String, Object> draftOffer = draft.getSiteOffers().get(0);
        draftOffer.put("salePrice", "");
        draftOffer.put("saleStart", "");
        draftOffer.put("saleEnd", "");

        ProductMasterSnapshotView prepared = invokePrepareSnapshotForPublish(draft, baseline, "STR245027-NAE");
        Map<String, Object> preparedOffer = prepared.getSiteOffers().get(0);

        assertEquals("", preparedOffer.get("salePrice"));
        assertEquals("", preparedOffer.get("saleStart"));
        assertEquals("", preparedOffer.get("saleEnd"));
        assertEquals("", prepared.getPricing().get("salePrice"));
        assertEquals("", prepared.getPricing().get("saleStart"));
        assertEquals("", prepared.getPricing().get("saleEnd"));
    }

    @Test
    void shouldNotTreatExplicitBlankOfferFieldsAsMissingWhenPreparingPublishSnapshot() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "48.00", "39.90", 33, 0, "LIVE");
        Map<String, Object> baselineOffer = baseline.getSiteOffers().get(0);
        baselineOffer.put("priceMax", "55.00");
        baselineOffer.put("saleStart", "2026-05-19 00:00:00");
        baselineOffer.put("saleEnd", "2036-05-19 23:59:59");

        ProductMasterSnapshotView draft = copySnapshot(baseline);
        Map<String, Object> draftOffer = draft.getSiteOffers().get(0);
        draftOffer.put("salePrice", "");
        draftOffer.put("saleStart", "");
        draftOffer.put("saleEnd", "");
        draftOffer.remove("priceMax");

        ProductMasterSnapshotView prepared = invokePrepareSnapshotForPublish(draft, baseline, "STR245027-NAE");
        Map<String, Object> preparedOffer = prepared.getSiteOffers().get(0);

        assertEquals("", preparedOffer.get("salePrice"));
        assertEquals("", preparedOffer.get("saleStart"));
        assertEquals("", preparedOffer.get("saleEnd"));
        assertEquals("55.00", preparedOffer.get("priceMax"));
    }

    @Test
    void shouldDefaultSaleWindowInPublishSnapshotOnlyForDirtyOffer() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "STR245027-NAE", "48.00", "39.20", 33, 0, "LIVE");
        baseline.getSiteOffers().get(0).put("priceMin", "10.00");
        baseline.getSiteOffers().get(0).put("isActive", true);

        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getSiteOffers().get(0).put("priceMin", "9.13");

        ProductMasterSnapshotView prepared = invokePrepareSnapshotForPublish(draft, baseline, "STR245027-NAE");
        Map<String, Object> preparedOffer = prepared.getSiteOffers().get(0);

        LocalDate saleStart = LocalDate.parse(String.valueOf(preparedOffer.get("saleStart")));
        LocalDate saleEnd = LocalDate.parse(String.valueOf(preparedOffer.get("saleEnd")));
        assertTrue(!saleStart.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1)));
        assertTrue(!saleStart.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1)));
        assertEquals(saleStart.plusYears(10), saleEnd);
    }

    private boolean invokeShouldSkip(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "shouldSkipSiteOfferLiveReadForSharedOnlyPublish",
                ProductMasterSnapshotView.class,
                ProductMasterSnapshotView.class,
                String.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(service, draft, baseline, currentSiteCode);
    }

    private ProductMasterSnapshotView invokePrepareSnapshotForPublish(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "prepareSnapshotForPublish",
                ProductMasterSnapshotView.class,
                ProductMasterSnapshotView.class,
                String.class
        );
        method.setAccessible(true);
        return (ProductMasterSnapshotView) method.invoke(service, draft, baseline, currentSiteCode);
    }

    private boolean invokeSameScopedSnapshot(
            ProductMasterSnapshotView left,
            ProductMasterSnapshotView right,
            String currentSiteCode
    ) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "sameScopedSnapshot",
                ProductMasterSnapshotView.class,
                ProductMasterSnapshotView.class,
                String.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(service, left, right, currentSiteCode);
    }

    private ProductPublishUnsupportedChanges invokeDetectUnsupportedChanges(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        return new ProductPublishUnsupportedChangesDetector(objectMapper).detect(draft, baseline, currentSiteCode);
    }

    private ProductMasterSnapshotView invokePublishableSnapshotForSupportedChanges(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            Object unsupportedChanges
    ) {
        return new ProductPublishSupportedSnapshotBuilder(
                objectMapper,
                new ProductPublishPlanner(new ProductDraftMergePolicy())
        ).build(draft, baseline, (ProductPublishUnsupportedChanges) unsupportedChanges);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeValidatePublishWriteCoverage(Object unsupportedChanges) {
        return new ProductPublishUnsupportedChangesDetector(objectMapper)
                .validateWriteCoverage((ProductPublishUnsupportedChanges) unsupportedChanges);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeValidatePublishSnapshot(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) throws Exception {
        Method method = LocalDbProductMasterService.class.getDeclaredMethod(
                "validatePublishSnapshot",
                ProductMasterSnapshotView.class,
                ProductMasterSnapshotView.class,
                String.class
        );
        method.setAccessible(true);
        return (List<String>) method.invoke(service, draft, baseline, currentSiteCode);
    }

    private Map<String, String> invokeSaleWindowForPublish(Map<String, Object> siteOffer) {
        return new ProductPublishOfferWriter(objectMapper, null).saleWindowForPublish(siteOffer);
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    private ProductMasterSnapshotView snapshot(
            String titleEn,
            String storeCode,
            String price,
            String salePrice,
            Integer fbnStock,
            Integer fbpStock,
            String statusCode
    ) {
        ProductMasterSnapshotView view = new ProductMasterSnapshotView();

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("skuParent", "Z580978E7ED8F9491B50BZ");
        identity.put("partnerSku", "PARTNER-SKU-001");
        identity.put("pskuCode", "PSKU-001");
        identity.put("brand", "test-brand");
        view.setIdentity(identity);

        Map<String, Object> taxonomy = new LinkedHashMap<>();
        taxonomy.put("productFulltype", "home_decor-lighting");
        view.setTaxonomy(taxonomy);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("titleEn", titleEn);
        content.put("titleAr", "");
        content.put("descriptionEn", "Same description");
        content.put("descriptionAr", "");
        content.put("images", new ArrayList<>(List.of("https://img.example.com/1.jpg")));
        view.setContent(content);

        Map<String, Object> storeContext = new LinkedHashMap<>();
        storeContext.put("storeCode", storeCode);
        view.setStoreContext(storeContext);

        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("storeCode", storeCode);
        siteOffer.put("site", "AE");
        siteOffer.put("price", price);
        siteOffer.put("salePrice", salePrice);
        siteOffer.put("fbnStock", fbnStock);
        siteOffer.put("fbpStock", fbpStock);
        siteOffer.put("statusCode", statusCode);
        view.setSiteOffers(new ArrayList<>(List.of(siteOffer)));
        return view;
    }
}
