package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductPublishPreparationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductPublishPreparationService service;

    @BeforeEach
    void setUp() {
        service = new ProductPublishPreparationService(
                objectMapper,
                new ProductDraftMergePolicy(),
                new ProductPublishOfferWriter(objectMapper, null)
        );
    }

    @Test
    void shouldPrepareSnapshotWithBaselineOfferFieldsAndMirrorCurrentOfferToPricing() {
        ProductMasterSnapshotView baseline = snapshot("Base title", "STR245027-NAE", "48.00", "39.20");
        Map<String, Object> baselineOffer = baseline.getSiteOffers().get(0);
        baselineOffer.put("priceMin", "10.00");
        baselineOffer.put("priceMax", "55.00");
        baselineOffer.put("saleStart", "2025-10-20T00:00:00+00:00");
        baselineOffer.put("saleEnd", "2025-11-30T23:59:59+00:00");
        baselineOffer.put("isActive", true);
        baselineOffer.put("offerNote", "baseline note");

        ProductMasterSnapshotView draft = copySnapshot(baseline);
        Map<String, Object> draftOffer = draft.getSiteOffers().get(0);
        draftOffer.put("priceMin", "9.13");
        draftOffer.remove("saleStart");
        draftOffer.remove("saleEnd");
        draftOffer.remove("isActive");
        draftOffer.remove("offerNote");

        ProductMasterSnapshotView prepared = service.prepareSnapshotForPublish(draft, baseline, "STR245027-NAE");
        Map<String, Object> preparedOffer = prepared.getSiteOffers().get(0);

        assertEquals("9.13", preparedOffer.get("priceMin"));
        assertEquals("2025-10-20T00:00:00+00:00", preparedOffer.get("saleStart"));
        assertEquals("2025-11-30T23:59:59+00:00", preparedOffer.get("saleEnd"));
        assertEquals(true, preparedOffer.get("isActive"));
        assertEquals("baseline note", preparedOffer.get("offerNote"));
        assertEquals("9.13", prepared.getPricing().get("priceMin"));
        assertEquals("2025-10-20T00:00:00+00:00", prepared.getPricing().get("saleStart"));
    }

    @Test
    void shouldKeepExplicitBlankSaleFieldsWhenPreparingPublishSnapshot() {
        ProductMasterSnapshotView baseline = snapshot("Base title", "STR245027-NAE", "48.00", "39.20");
        Map<String, Object> baselineOffer = baseline.getSiteOffers().get(0);
        baselineOffer.put("saleStart", "2025-10-20T00:00:00+00:00");
        baselineOffer.put("saleEnd", "2025-11-30T23:59:59+00:00");

        ProductMasterSnapshotView draft = copySnapshot(baseline);
        Map<String, Object> draftOffer = draft.getSiteOffers().get(0);
        draftOffer.put("salePrice", "");
        draftOffer.put("saleStart", "");
        draftOffer.put("saleEnd", "");

        ProductMasterSnapshotView prepared = service.prepareSnapshotForPublish(draft, baseline, "STR245027-NAE");
        Map<String, Object> preparedOffer = prepared.getSiteOffers().get(0);

        assertEquals("", preparedOffer.get("salePrice"));
        assertEquals("", preparedOffer.get("saleStart"));
        assertEquals("", preparedOffer.get("saleEnd"));
        assertEquals("", prepared.getPricing().get("salePrice"));
        assertEquals("", prepared.getPricing().get("saleStart"));
        assertEquals("", prepared.getPricing().get("saleEnd"));
    }

    @Test
    void shouldSkipSiteOfferLiveReadOnlyWhenCurrentSiteOfferIsUnchanged() {
        ProductMasterSnapshotView baseline = snapshot("Base title", "STR245027-NAE", "188.00", "168.00");
        ProductMasterSnapshotView sharedOnlyDraft = copySnapshot(baseline);
        sharedOnlyDraft.getContent().put("titleEn", "Draft title");
        sharedOnlyDraft.getSiteOffers().get(0).put("fbnStock", 32);
        sharedOnlyDraft.getSiteOffers().get(0).put("statusCode", "NOT_LIVE");

        ProductMasterSnapshotView offerDraft = copySnapshot(sharedOnlyDraft);
        offerDraft.getSiteOffers().get(0).put("price", "199.00");

        assertTrue(service.shouldSkipSiteOfferLiveReadForSharedOnlyPublish(
                sharedOnlyDraft,
                baseline,
                "STR245027-NAE"
        ));
        assertFalse(service.shouldSkipSiteOfferLiveReadForSharedOnlyPublish(
                offerDraft,
                baseline,
                "STR245027-NAE"
        ));
    }

    @Test
    void shouldDetectPublishConflictFieldsForSameChangedField() {
        ProductMasterSnapshotView baseline = snapshot("Base title", "STR245027-NAE", "100.00", null);
        ProductMasterSnapshotView localDraft = copySnapshot(baseline);
        ProductMasterSnapshotView noonCurrent = copySnapshot(baseline);
        localDraft.getSiteOffers().get(0).put("price", "110.00");
        noonCurrent.getSiteOffers().get(0).put("price", "120.00");
        noonCurrent.getContent().put("descriptionEn", "Noon changed a different field");

        List<Map<String, Object>> conflicts = service.detectPublishConflictFields(
                baseline,
                localDraft,
                noonCurrent,
                "STR245027-NAE"
        );

        assertEquals(1, conflicts.size());
        assertEquals("siteOffers[0].price", conflicts.get(0).get("path"));
        assertEquals("site", conflicts.get(0).get("scope"));
        assertEquals("100", conflicts.get(0).get("baselineValue"));
        assertEquals("110", conflicts.get(0).get("localValue"));
        assertEquals("120", conflicts.get(0).get("noonValue"));
    }

    @Test
    void shouldValidatePublishSnapshotAndOperationalKeys() {
        ProductMasterSnapshotView baseline = snapshot("Base title", "STR245027-NAE", "48.00", "39.20");
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getContent().put("images", List.of("/api/product-master/image-assets/local-image.png"));
        draft.getSiteOffers().get(0).remove("pskuCode");
        baseline.getSiteOffers().get(0).remove("pskuCode");
        draft.getSiteOffers().get(0).put("price", "49.00");

        List<String> snapshotErrors = service.validatePublishSnapshot(draft, baseline, "STR245027-NAE");
        List<String> operationalErrors = service.validatePublishOperationalKeys(draft, baseline, "STR245027-NAE");

        assertFalse(snapshotErrors.isEmpty());
        assertTrue(operationalErrors.stream().anyMatch((error) -> error.contains("pskuCode")));
    }

    @Test
    void shouldRejectPublicDetailReadonlySnapshotForPublish() {
        ProductMasterSnapshotView baseline = snapshot("Base title", "STR245027-NAE", "48.00", "39.20");
        baseline.setMode(ProductPublicDetailReadonlyWorkbenchFactory.MODE);
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getContent().put("titleEn", "Changed title");

        List<String> errors = service.validatePublishSnapshot(draft, baseline, "STR245027-NAE");

        assertTrue(errors.stream().anyMatch((error) -> error.contains("前台公开详情")));
    }

    @Test
    void shouldDefaultSaleWindowOnlyForDirtyOfferWithSalePrice() {
        ProductMasterSnapshotView baseline = snapshot("Base title", "STR245027-NAE", "48.00", "39.20");
        baseline.getSiteOffers().get(0).put("priceMin", "10.00");

        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getSiteOffers().get(0).put("priceMin", "9.13");

        ProductMasterSnapshotView prepared = service.prepareSnapshotForPublish(draft, baseline, "STR245027-NAE");
        Map<String, Object> preparedOffer = prepared.getSiteOffers().get(0);

        LocalDate saleStart = LocalDate.parse(String.valueOf(preparedOffer.get("saleStart")));
        LocalDate saleEnd = LocalDate.parse(String.valueOf(preparedOffer.get("saleEnd")));
        assertTrue(!saleStart.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1)));
        assertTrue(!saleStart.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1)));
        assertEquals(saleStart.plusYears(10), saleEnd);
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    private ProductMasterSnapshotView snapshot(
            String titleEn,
            String storeCode,
            String price,
            String salePrice
    ) {
        ProductMasterSnapshotView view = new ProductMasterSnapshotView();

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("skuParent", "PARENT-001");
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
        siteOffer.put("pskuCode", "PSKU-001");
        siteOffer.put("price", price);
        if (salePrice != null) {
            siteOffer.put("salePrice", salePrice);
        }
        siteOffer.put("fbnStock", 33);
        siteOffer.put("fbpStock", 0);
        siteOffer.put("statusCode", "LIVE");
        view.setSiteOffers(new ArrayList<>(List.of(siteOffer)));
        return view;
    }
}
