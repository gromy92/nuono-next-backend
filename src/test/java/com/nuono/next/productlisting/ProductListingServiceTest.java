package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccountType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductListingServiceTest {

    private FakeProductListingMapper mapper;
    private ProductListingService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeProductListingMapper();
        service = new ProductListingService(mapper, new ObjectMapper(), new ProductListingValidator());
    }

    @Test
    void saveDraftUsesSessionOwnerAndOperator() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");

        ProductListingDraftView view = service.saveDraft(context, validCommand());

        assertEquals(10002L, mapper.insertedDraft().getOwnerUserId());
        assertEquals(90001L, mapper.insertedDraft().getCreatedBy());
        assertEquals(90001L, mapper.insertedDraft().getUpdatedBy());
        assertEquals("ready_for_dry_run", view.getStatus());
    }

    @Test
    void dryRunTaskSucceedsWhenValidationPassesAndDoesNotWriteNoon() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("DRY_RUN", task.getMode());
        assertEquals("validated", task.getStatus());
        assertEquals("DRY_RUN", mapper.insertedTask().getMode());
    }

    @Test
    void saveDraftKeepsMissingImageDimensionsEligibleForAutomaticDryRunEnrichment() {
        CountingImageDownloader downloader = new CountingImageDownloader(jpeg(660, 900));
        service = new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                new ProductListingImageMetadataEnricher(downloader)
        );
        BusinessAccessContext context =
                businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand listing = validCommand();
        listing.setImageAssetMetadata(List.of());
        ProductListingDraftView draft = service.saveDraft(context, listing);
        ProductListingDraftView reloadedAfterSave =
                service.loadDraft(context, draft.getDraftId());
        ProductListingDryRunSubmitCommand command =
                new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView firstTask = service.submitDryRun(context, command);
        ProductListingTaskView secondTask = service.submitDryRun(context, command);
        ProductListingDraftView reloaded =
                service.loadDraft(context, draft.getDraftId());

        assertEquals("ready_for_dry_run", draft.getStatus());
        assertTrue(draft.getValidationIssues().stream()
                .anyMatch(issue -> "noon_image_dimension_missing".equals(issue.getCode())));
        assertTrue(reloadedAfterSave.getDraft().getImageAssetMetadata().isEmpty());
        assertEquals("validated", firstTask.getStatus());
        assertEquals("validated", secondTask.getStatus());
        assertEquals(1, downloader.downloadCount);
        assertEquals(
                660,
                reloaded.getDraft().getImageAssetMetadata().get(0).get("width")
        );
        assertEquals(
                900,
                reloaded.getDraft().getImageAssetMetadata().get(0).get("height")
        );
        assertTrue(mapper.insertedTask().getInputSnapshotJson().contains("\"width\":660"));
        assertTrue(mapper.insertedTask().getInputSnapshotJson().contains("\"height\":900"));
    }

    @Test
    void dryRunWarnsWhenOfferPriceOrSplitFieldsAreDisabledButDoesNotBlockValidation() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand listing = validCommand();
        listing.setPriceMin(new BigDecimal("45.00"));
        listing.setPriceMax(new BigDecimal("59.00"));
        listing.setSalePrice(new BigDecimal("47.50"));
        listing.setSaleStart("2026-07-01");
        listing.setSaleEnd("2026-07-07");
        listing.setIsActive(Boolean.TRUE);
        listing.setOfferNote("Launch stock prepared.");
        ProductListingDraftView draft = service.saveDraft(context, listing);
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertWarning(task, "offerPrice", "offer_price_not_written");
        assertWarning(task, "offerSplit", "offer_note_active_not_written");
        assertTrue(mapper.insertedTask().getValidationJson().contains("offer_price_not_written"));
        assertTrue(mapper.insertedTask().getValidationJson().contains("offer_note_active_not_written"));
    }

    @Test
    void dryRunOmitsSupportedOfferWarningsWhenSplitOfferWriteIsEnabled() {
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setOfferUpsertEnabled(true);
        properties.setOfferSplitWriteEnabled(true);
        service = new ProductListingService(mapper, new ObjectMapper(), new ProductListingValidator(), properties);
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand listing = validCommand();
        listing.setPriceMin(new BigDecimal("45.00"));
        listing.setPriceMax(new BigDecimal("59.00"));
        listing.setSalePrice(new BigDecimal("47.50"));
        listing.setSaleStart("2026-07-01");
        listing.setSaleEnd("2026-07-07");
        listing.setIsActive(Boolean.TRUE);
        listing.setOfferNote("Launch note.");
        ProductListingDraftView draft = service.saveDraft(context, listing);
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertNoWarning(task, "offer_price_not_written");
        assertNoWarning(task, "offer_note_active_not_written");
        assertNoWarning(task, "offer_stock_not_written");
    }

    @Test
    void dryRunBlocksUnsupportedWarehouseStockEvenWhenSplitOfferWriteIsEnabled() {
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setOfferUpsertEnabled(true);
        properties.setOfferSplitWriteEnabled(true);
        service = new ProductListingService(mapper, new ObjectMapper(), new ProductListingValidator(), properties);
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand listing = validCommand();
        listing.setFbp(Boolean.TRUE);
        listing.setWarehouseCode("W00752151SA");
        listing.setQuantity(100);
        ProductListingDraftView draft = service.saveDraft(context, listing);
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validation_failed", task.getStatus());
        assertIssue(task.getValidationIssues(), "fbp", "noon_fbp_not_supported");
        assertIssue(
                task.getValidationIssues(),
                "warehouseStock",
                "noon_warehouse_not_supported"
        );
        assertIssue(
                task.getValidationIssues(),
                "quantity",
                "noon_stock_quantity_not_supported"
        );
    }

    @Test
    void saveDraftPreservesDetailedAttributesInDryRunSnapshot() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand command = validCommand();
        Map<String, Object> baseMaterial = new LinkedHashMap<>();
        baseMaterial.put("code", "base_material");
        baseMaterial.put("labelEn", "Base Material");
        baseMaterial.put("commonValue", "metal");
        baseMaterial.put("enValue", "Metal");
        command.setKeyAttributes(List.of(baseMaterial));

        ProductListingDraftView draft = service.saveDraft(context, command);
        ProductListingDryRunSubmitCommand dryRunCommand = new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");
        service.submitDryRun(context, dryRunCommand);

        assertEquals("metal", draft.getDraft().getKeyAttributes().get(0).get("commonValue"));
        assertTrue(mapper.insertedDraft().getDraftJson().contains("\"base_material\""));
        assertTrue(mapper.insertedTask().getInputSnapshotJson().contains("\"base_material\""));
    }

    @Test
    void saveDraftPersistsExplicitBarcodeRemoval() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView created = service.saveDraft(context, validCommand());
        ProductListingDraftCommand update = validCommand();
        update.setDraftId(created.getDraftId());
        update.setBarcode(null);
        update.setKeyAttributes(List.of(Map.of(
                "code", "barcode",
                "commonValue", "",
                "enValue", "",
                "arValue", ""
        )));

        ProductListingDraftView saved = service.saveDraft(context, update);
        ProductListingDraftView loaded = service.loadDraft(context, saved.getDraftId());

        assertEquals(null, loaded.getDraft().getBarcode());
        assertEquals("", loaded.getDraft().getKeyAttributes().get(0).get("commonValue"));
    }

    @Test
    void saveDraftPreservesCompetitorMaterialsForDraftRecovery() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand command = validCommand();
        Map<String, Object> competitor = new LinkedHashMap<>();
        competitor.put("id", "noon-zsku-1");
        competitor.put("sourceHost", "Noon");
        competitor.put("externalSku", "ZCOMPETITOR1");
        competitor.put("titleEn", "Competitor rugged case");
        competitor.put("sellingPointsEn", List.of("Competitor selling point"));
        command.setCompetitorMaterials(List.of(competitor));

        ProductListingDraftView saved = service.saveDraft(context, command);
        ProductListingDraftView loaded = service.loadDraft(context, saved.getDraftId());

        assertEquals("Competitor rugged case", loaded.getDraft().getCompetitorMaterials().get(0).get("titleEn"));
        assertTrue(mapper.insertedDraft().getDraftJson().contains("\"competitorMaterials\""));
        assertTrue(mapper.insertedDraft().getDraftJson().contains("\"ZCOMPETITOR1\""));
    }

    @Test
    void updateDraftDoesNotClearExistingTaxonomyAndBrandWithBlankHydrationPayload() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView created = service.saveDraft(context, validCommand());
        ProductListingDraftCommand update = validCommand();
        update.setDraftId(created.getDraftId());
        update.setPsku("NN-TEST-PSKU-UPDATED");
        update.setIdProductFullType(null);
        update.setProductFullType(null);
        update.setFamily(null);
        update.setProductType(null);
        update.setProductSubType(null);
        update.setProductBrand(null);
        update.setProductBrandCode(null);

        ProductListingDraftView saved = service.saveDraft(context, update);
        ProductListingDryRunSubmitCommand dryRunCommand = new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(saved.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");
        ProductListingTaskView task = service.submitDryRun(context, dryRunCommand);

        assertEquals("NN-TEST-PSKU-UPDATED", saved.getDraft().getPsku());
        assertEquals("electronic_accessories-headphones-wired_headphones", saved.getDraft().getProductFullType());
        assertEquals("Generic", saved.getDraft().getProductBrand());
        assertEquals("validated", task.getStatus());
        assertTrue(mapper.insertedTask().getInputSnapshotJson()
                .contains("\"productFullType\":\"electronic_accessories-headphones-wired_headphones\""));
        assertTrue(mapper.insertedTask().getInputSnapshotJson().contains("\"productBrand\":\"Generic\""));
    }

    @Test
    void updateDraftDoesNotCarryOldTaxonomyLabelsWhenProductFullTypeChanges() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand first = validCommand();
        first.setFamily("Electronic Accessories");
        first.setProductType("Headphones");
        first.setProductSubType("Wired Headphones");
        ProductListingDraftView created = service.saveDraft(context, first);
        ProductListingDraftCommand update = validCommand();
        update.setDraftId(created.getDraftId());
        update.setPsku("NN-TEST-PSKU-FULLTYPE-CHANGED");
        update.setIdProductFullType(null);
        update.setProductFullType("electronic_accessories-phone_accessories-phone_grips_stands");
        update.setFamily(null);
        update.setProductType(null);
        update.setProductSubType(null);

        ProductListingDraftView saved = service.saveDraft(context, update);

        assertEquals("electronic_accessories-phone_accessories-phone_grips_stands", saved.getDraft().getProductFullType());
        assertEquals(null, saved.getDraft().getIdProductFullType());
        assertEquals(null, saved.getDraft().getFamily());
        assertEquals(null, saved.getDraft().getProductType());
        assertEquals(null, saved.getDraft().getProductSubType());
    }

    @Test
    void updateDraftTreatsExplicitProductFullTypeAsSourceOfTruth() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand first = validCommand();
        first.setIdProductFullType(3066L);
        first.setProductFullType("electronic_accessories-phone_accessories-phone_grips_stands");
        first.setFamily("Electronic Accessories");
        first.setProductType("Headphones");
        first.setProductSubType("Wired Headphones");
        ProductListingDraftView created = service.saveDraft(context, first);
        ProductListingDraftCommand update = validCommand();
        update.setDraftId(created.getDraftId());
        update.setPsku("NN-TEST-PSKU-SAME-FULLTYPE");
        update.setIdProductFullType(3066L);
        update.setProductFullType("electronic_accessories-phone_accessories-phone_grips_stands");
        update.setFamily("Electronic Accessories");
        update.setProductType("Headphones");
        update.setProductSubType("Wired Headphones");

        ProductListingDraftView saved = service.saveDraft(context, update);

        assertEquals("electronic_accessories-phone_accessories-phone_grips_stands", saved.getDraft().getProductFullType());
        assertEquals(null, saved.getDraft().getIdProductFullType());
        assertEquals(null, saved.getDraft().getFamily());
        assertEquals(null, saved.getDraft().getProductType());
        assertEquals(null, saved.getDraft().getProductSubType());
    }

    @Test
    void saveDraftPreservesContentCopyInDryRunSnapshot() throws Exception {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        String json = "{"
                + "\"storeCode\":\"STR245027-NAE\","
                + "\"psku\":\"NN-CONTENT-PSKU\","
                + "\"idProductFullType\":3066,"
                + "\"productFullType\":\"home_decor-lighting-table_lamps\","
                + "\"productBrand\":\"Generic\","
                + "\"productBrandCode\":\"generic\","
                + "\"productTitleCn\":\"Ceramic lamp CN draft\","
                + "\"productTitleEn\":\"Ceramic bedside lamp\","
                + "\"productTitleAr\":\"Arabic ceramic lamp title\","
                + "\"productDescriptionCn\":\"Chinese description draft\","
                + "\"productDescriptionEn\":\"English long description for Noon listing.\","
                + "\"productDescriptionAr\":\"Arabic long description for Noon listing.\","
                + "\"productHighlightsCn\":[\"Soft lighting CN draft\"],"
                + "\"productHighlightsEn\":[\"Soft ambient lighting\"],"
                + "\"productHighlightsAr\":[\"Soft ambient lighting AR\"],"
                + "\"imageUrls\":[\"https://example.test/images/sku-main.jpg\"],"
                + "\"price\":49.90,"
                + "\"purchasePrice\":19.90,"
                + "\"supplyEvidenceType\":\"1688_OFFER\","
                + "\"supplyEvidenceRefId\":43101,"
                + "\"fbp\":true,"
                + "\"warehouseId\":\"W00752151SA\","
                + "\"quantity\":100,"
                + "\"idWarranty\":24,"
                + "\"barcode\":\"6290000000001\""
                + "}";
        ProductListingDraftCommand command = new ObjectMapper().readValue(json, ProductListingDraftCommand.class);

        ProductListingDraftView draft = service.saveDraft(context, command);
        ProductListingDryRunSubmitCommand dryRunCommand = new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");
        service.submitDryRun(context, dryRunCommand);

        assertTrue(mapper.insertedDraft().getDraftJson().contains("\"productDescriptionEn\":\"English long description for Noon listing.\""));
        assertTrue(mapper.insertedDraft().getDraftJson().contains("\"productHighlightsEn\":[\"Soft ambient lighting\"]"));
        assertTrue(mapper.insertedTask().getInputSnapshotJson().contains("\"productDescriptionAr\":\"Arabic long description for Noon listing.\""));
        assertTrue(mapper.insertedTask().getInputSnapshotJson().contains("\"productHighlightsAr\":[\"Soft ambient lighting AR\"]"));
    }

    @Test
    void saveDraftPersistsSourceLineageFromCommand() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand command = validCommand();
        command.setSourceType("manual_selection_group");
        command.setSourceRefId(91002L);

        ProductListingDraftView draft = service.saveDraft(context, command);

        assertEquals("manual_selection_group", mapper.insertedDraft().getSourceType());
        assertEquals(91002L, mapper.insertedDraft().getSourceRefId());
        assertEquals("manual_selection_group", draft.getDraft().getSourceType());
        assertEquals(91002L, draft.getDraft().getSourceRefId());
    }

    @Test
    void saveDraftReusesActiveSourceDraftWhenDraftIdIsMissing() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand first = validCommand();
        first.setSourceType("manual_selection_group");
        first.setSourceRefId(91002L);
        ProductListingDraftView created = service.saveDraft(context, first);
        ProductListingDraftCommand second = validCommand();
        second.setSourceType("manual_selection_group");
        second.setSourceRefId(91002L);
        second.setProductTitleEn("Updated title from manual selection group");
        mapper.resetUpdateCount();

        ProductListingDraftView updated = service.saveDraft(context, second);

        assertEquals(created.getDraftId(), updated.getDraftId());
        assertEquals(1, mapper.updateCount());
        assertEquals("Updated title from manual selection group", updated.getDraft().getProductTitleEn());
    }

    @Test
    void loadActiveSourceDraftUsesExactOwnerStoreAndSourceBusinessKey() {
        BusinessAccessContext ownerContext = businessContext(
                10002L,
                90001L,
                Set.of("STR245027-NAE", "STR245027-NSA")
        );
        ProductListingDraftCommand command = validCommand();
        command.setSourceType("manual_selection_group");
        command.setSourceRefId(91002L);
        ProductListingDraftView created = service.saveDraft(ownerContext, command);

        ProductListingDraftView recovered = service.loadActiveSourceDraft(
                ownerContext,
                "STR245027-NAE",
                " manual_selection_group ",
                91002L
        );

        assertEquals(created.getDraftId(), recovered.getDraftId());
        assertEquals(created.getDraft().getPsku(), recovered.getDraft().getPsku());
        assertNull(service.loadActiveSourceDraft(
                ownerContext,
                "STR245027-NAE",
                "manual_selection_group",
                91003L
        ));
        assertNull(service.loadActiveSourceDraft(
                ownerContext,
                "STR245027-NSA",
                "manual_selection_group",
                91002L
        ));
        BusinessAccessContext otherOwner =
                businessContext(10003L, 90002L, "STR245027-NAE");
        assertNull(service.loadActiveSourceDraft(
                otherOwner,
                "STR245027-NAE",
                "manual_selection_group",
                91002L
        ));
    }

    @Test
    void loadActiveSourceDraftRejectsMalformedBusinessKeyAndStoreOutsideScope() {
        BusinessAccessContext context =
                businessContext(10002L, 90001L, "STR245027-NAE");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.loadActiveSourceDraft(
                        context,
                        "STR245027-NAE",
                        " ",
                        91002L
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.loadActiveSourceDraft(
                        context,
                        "STR245027-NAE",
                        "manual_selection_group",
                        0L
                )
        );
        assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.loadActiveSourceDraft(
                        context,
                        "STR245027-NSA",
                        "manual_selection_group",
                        91002L
                )
        );
    }

    @Test
    void listDraftsReturnsCurrentStoreEditableDraftsWithPayload() {
        BusinessAccessContext aeContext = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand first = validCommand();
        first.setPsku("NN-DRAFT-ONE");
        service.saveDraft(aeContext, first);
        ProductListingDraftCommand second = validCommand();
        second.setPsku("NN-DRAFT-TWO");
        second.setProductTitleEn("Second listing draft title");
        ProductListingDraftView latest = service.saveDraft(aeContext, second);
        BusinessAccessContext saContext = businessContext(10002L, 90002L, "STR245027-NSA");
        ProductListingDraftCommand otherStore = validCommand();
        otherStore.setStoreCode("STR245027-NSA");
        otherStore.setPsku("NN-DRAFT-SA");
        service.saveDraft(saContext, otherStore);

        List<ProductListingDraftView> drafts = service.listDrafts(aeContext, "STR245027-NAE", 20);

        assertEquals(2, drafts.size());
        assertEquals(latest.getDraftId(), drafts.get(0).getDraftId());
        assertEquals("NN-DRAFT-TWO", drafts.get(0).getDraft().getPsku());
        assertEquals("Second listing draft title", drafts.get(0).getDraft().getProductTitleEn());
        assertTrue(drafts.stream().noneMatch(draft -> "STR245027-NSA".equals(draft.getStoreCode())));
    }

    @Test
    void saveDraftPreservesOfferFieldsInDryRunSnapshot() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand command = validCommand();
        command.setPriceMin(new BigDecimal("45.00"));
        command.setPriceMax(new BigDecimal("59.00"));
        command.setSalePrice(new BigDecimal("47.50"));
        command.setSaleStart("2026-06-24");
        command.setSaleEnd("2026-07-01");
        command.setIsActive(Boolean.TRUE);
        command.setOfferNote("选品池: CAND-9001 / 物流 默认货代");

        ProductListingDraftView draft = service.saveDraft(context, command);
        ProductListingDryRunSubmitCommand dryRunCommand = new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");
        service.submitDryRun(context, dryRunCommand);

        assertTrue(mapper.insertedDraft().getDraftJson().contains("\"salePrice\":47.50"));
        assertTrue(mapper.insertedDraft().getDraftJson().contains("\"offerNote\":\"选品池: CAND-9001 / 物流 默认货代\""));
        assertTrue(mapper.insertedTask().getInputSnapshotJson().contains("\"priceMin\":45.00"));
        assertTrue(mapper.insertedTask().getInputSnapshotJson().contains("\"isActive\":true"));
    }

    @Test
    void dryRunTaskAllowsMissingPurchaseCost() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand invalid = validCommand();
        invalid.setPurchasePrice(null);
        ProductListingDraftView draft = service.saveDraft(context, invalid);
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .noneMatch(issue -> "purchasePrice".equals(issue.getFieldKey())));
    }

    @Test
    void dryRunTaskFailsWhenPartnerSkuAlreadyHasSuccessfulListingTask() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        mapper.seedSuccessfulRealRun(10002L, "STR245027-NAE", validCommand());
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validation_failed", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .anyMatch(issue -> "psku".equals(issue.getFieldKey())
                        && "partner_sku_already_exists".equals(issue.getCode())));
    }

    @Test
    void saveDraftFailsWhenPartnerSkuAlreadyExistsInLocalStore() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        mapper.seedLocalPartnerSku(10002L, "STR245027-NAE", "NN-TEST-PSKU", 60001L);

        ProductListingDraftView draft = service.saveDraft(context, validCommand());

        assertEquals("draft", draft.getStatus());
        assertTrue(draft.getValidationIssues().stream()
                .anyMatch(issue -> "psku".equals(issue.getFieldKey())
                        && "partner_sku_already_exists".equals(issue.getCode())
                        && issue.getMessage().contains("本地店铺")));
    }

    @Test
    void dryRunTaskFailsWhenBarcodeAlreadyExistsInLocalStore() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        mapper.seedLocalBarcode(10002L, "STR245027-NAE", "6290000000001", 60002L);
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validation_failed", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .anyMatch(issue -> "barcode".equals(issue.getFieldKey())
                        && "barcode_already_exists".equals(issue.getCode())
                        && issue.getMessage().contains("本地店铺")));
    }

    @Test
    void dryRunAllowsLocalProjectionCreatedBySameDraft() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        mapper.seedLocalPartnerSku(10002L, "STR245027-NAE", "NN-TEST-PSKU", 60003L, draft.getDraftId());
        mapper.seedLocalBarcode(10002L, "STR245027-NAE", "6290000000001", 60003L, draft.getDraftId());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .noneMatch(issue -> "partner_sku_already_exists".equals(issue.getCode())
                        || "barcode_already_exists".equals(issue.getCode())));
    }

    @Test
    void dryRunAllowsProductRebuildToReuseSourceProductPartnerSkuAndBarcode() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand rebuild = validProductRebuildCommand(60004L);
        mapper.seedLocalPartnerSku(10002L, "STR245027-NAE", "NN-TEST-PSKU", 60004L);
        mapper.seedLocalBarcode(10002L, "STR245027-NAE", "6290000000001", 60004L);
        mapper.seedSuccessfulRealRun(10002L, "STR245027-NAE", validCommand());
        ProductListingDraftView draft = service.saveDraft(context, rebuild);
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .noneMatch(issue -> "partner_sku_already_exists".equals(issue.getCode())
                        || "barcode_already_exists".equals(issue.getCode())));
    }

    @Test
    void dryRunAllowsPartnerSkuAfterSuccessfulProductDelete() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        mapper.seedSuccessfulRealRun(10002L, "STR245027-NAE", validCommand());
        mapper.seedSuccessfulProductDelete(10002L, "STR245027-NAE", "NN-TEST-PSKU");
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .noneMatch(issue -> "partner_sku_already_exists".equals(issue.getCode())));
    }

    @Test
    void confirmedRealRunAllowsProductRebuildToReuseHistoricalPartnerSkuTask() {
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        service = new ProductListingService(mapper, new ObjectMapper(), new ProductListingValidator(), properties);
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        mapper.seedSuccessfulRealRun(10002L, "STR245027-NAE", validCommand());
        ProductListingDraftView draft = service.saveDraft(context, validProductRebuildCommand(60004L));
        ProductListingDryRunSubmitCommand dryRunCommand = new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");
        ProductListingTaskView dryRun = service.submitDryRun(context, dryRunCommand);
        ProductListingRealRunCommand confirmCommand = new ProductListingRealRunCommand();
        confirmCommand.setConfirmRealNoonWrite(true);

        ProductListingTaskView realRun = service.confirmRealRun(context, dryRun.getTaskId(), confirmCommand);

        assertEquals("submitted", realRun.getStatus());
    }

    @Test
    void confirmedRealRunRejectsStaleValidatedDryRunWhenPartnerSkuAlreadyExists() {
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        service = new ProductListingService(mapper, new ObjectMapper(), new ProductListingValidator(), properties);
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand dryRunCommand = new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");
        ProductListingTaskView staleDryRun = service.submitDryRun(context, dryRunCommand);
        mapper.seedSuccessfulRealRun(10002L, "STR245027-NAE", validCommand());
        ProductListingRealRunCommand confirmCommand = new ProductListingRealRunCommand();
        confirmCommand.setConfirmRealNoonWrite(true);

        ProductListingTaskView realRun = service.confirmRealRun(context, staleDryRun.getTaskId(), confirmCommand);

        assertEquals("rejected", realRun.getStatus());
        assertEquals("partner_sku_already_exists", realRun.getFailureCode());
        assertTrue(realRun.getFailureMessage().contains("PSKU 已存在"));
    }

    @Test
    void validateDraftRejectsStoreOutsideSessionScopeBeforeUpdating() {
        BusinessAccessContext storeAeContext = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(storeAeContext, validCommand());
        BusinessAccessContext storeSaContext = businessContext(10002L, 90002L, "STR245027-NSA");
        mapper.resetUpdateCount();

        assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.validateDraft(storeSaContext, draft.getDraftId())
        );

        assertEquals(0, mapper.updateCount());
    }

    @Test
    void updateDraftRejectsOriginalStoreOutsideSessionScopeBeforeUpdating() {
        BusinessAccessContext storeAeContext = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(storeAeContext, validCommand());
        ProductListingDraftCommand changeStore = validCommand();
        changeStore.setDraftId(draft.getDraftId());
        changeStore.setStoreCode("STR245027-NSA");
        BusinessAccessContext storeSaContext = businessContext(10002L, 90002L, "STR245027-NSA");
        mapper.resetUpdateCount();

        assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.saveDraft(storeSaContext, changeStore)
        );

        assertEquals(0, mapper.updateCount());
    }

    @Test
    void updateDraftRejectsChangingStoreEvenWhenBothStoresAreAccessible() {
        BusinessAccessContext storeAeContext = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(storeAeContext, validCommand());
        ProductListingDraftCommand changeStore = validCommand();
        changeStore.setDraftId(draft.getDraftId());
        changeStore.setStoreCode("STR245027-NSA");
        BusinessAccessContext bothStoresContext = businessContext(
                10002L,
                90003L,
                Set.of("STR245027-NAE", "STR245027-NSA")
        );
        mapper.resetUpdateCount();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDraft(bothStoresContext, changeStore)
        );

        assertEquals(0, mapper.updateCount());
    }

    @Test
    void loadTaskRejectsStoreOutsideSessionScope() {
        BusinessAccessContext storeAeContext = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(storeAeContext, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");
        ProductListingTaskView task = service.submitDryRun(storeAeContext, command);
        BusinessAccessContext storeSaContext = businessContext(10002L, 90002L, "STR245027-NSA");

        assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.loadTask(storeSaContext, task.getTaskId())
        );
    }

    @Test
    void fieldValidationReturnsDuplicatePskuAndBarcodeWithoutSavingDraft() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand command = validCommand();
        mapper.seedLocalProduct(
                10002L,
                "STR245027-NAE",
                "NN-TEST-PSKU",
                "6290000000001",
                88001L,
                null
        );

        ProductListingFieldValidationView view = service.validateFields(context, command);

        assertIssue(view.getIssues(), "psku", "partner_sku_already_exists");
        assertIssue(view.getIssues(), "barcode", "barcode_already_exists");
        assertEquals(null, mapper.insertedDraft());
        assertEquals(0, mapper.updateCount());
    }

    private ProductListingDraftCommand validCommand() {
        ProductListingDraftCommand command = new ProductListingDraftCommand();
        command.setStoreCode("STR245027-NAE");
        command.setPsku("NN-TEST-PSKU");
        command.setIdProductFullType(3066L);
        command.setProductFullType("electronic_accessories-headphones-wired_headphones");
        command.setProductBrand("Generic");
        command.setProductBrandCode("generic");
        command.setProductTitleEn("Wired headphones with microphone");
        command.setProductTitleAr("Arabic wired headphones title");
        command.setImageUrls(List.of("https://example.test/images/sku-main.jpg"));
        command.setImageAssetMetadata(List.of(Map.of(
                "imageUrl", "https://example.test/images/sku-main.jpg",
                "width", 1247,
                "height", 1706
        )));
        command.setPrice(new BigDecimal("49.90"));
        command.setPurchasePrice(new BigDecimal("19.90"));
        command.setSupplyEvidenceType("1688_OFFER");
        command.setSupplyEvidenceRefId(43101L);
        command.setOptionalPurchaseOrderId(70001L);
        command.setIdWarranty(24);
        command.setBarcode("6290000000001");
        return command;
    }

    private ProductListingDraftCommand validProductRebuildCommand(Long productMasterId) {
        ProductListingDraftCommand command = validCommand();
        command.setSourceType("PRODUCT_REBUILD");
        command.setSourceRefId(productMasterId);
        command.setRebuildSourceProductMasterId(productMasterId);
        return command;
    }

    private static byte[] jpeg(int width, int height) {
        try {
            BufferedImage image =
                    new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class CountingImageDownloader
            implements ProductListingImageDownloader {
        private final byte[] imageBytes;
        private int downloadCount;

        private CountingImageDownloader(byte[] imageBytes) {
            this.imageBytes = imageBytes;
        }

        @Override
        public ProductListingImageDownload download(String imageUrl) {
            downloadCount++;
            return new ProductListingImageDownload(
                    "test.jpg",
                    "image/jpeg",
                    imageBytes
            );
        }
    }

    private BusinessAccessContext businessContext(Long ownerUserId, Long sessionUserId, String storeCode) {
        return businessContext(ownerUserId, sessionUserId, Set.of(storeCode));
    }

    private BusinessAccessContext businessContext(Long ownerUserId, Long sessionUserId, Set<String> storeCodes) {
        Map<String, Long> storeOwnerUserIds = new LinkedHashMap<>();
        for (String storeCode : storeCodes) {
            storeOwnerUserIds.put(storeCode, ownerUserId);
        }
        return BusinessAccessContext.builder()
                .sessionUserId(sessionUserId)
                .businessOwnerUserId(ownerUserId)
                .accountType(BusinessAccountType.OPERATOR)
                .roleId(3L)
                .roleLevel(2)
                .roleName("purchase")
                .storeCodes(storeCodes)
                .storeOwnerUserIds(storeOwnerUserIds)
                .menuPaths(Set.of("/purchase/listing", "/api/product-listing"))
                .build();
    }

    private void assertWarning(ProductListingTaskView task, String fieldKey, String code) {
        assertTrue(task.getValidationIssues().stream().anyMatch(issue ->
                        fieldKey.equals(issue.getFieldKey())
                                && code.equals(issue.getCode())
                                && "warning".equals(issue.getSeverity())),
                "Expected warning " + fieldKey + "/" + code);
    }

    private void assertNoWarning(ProductListingTaskView task, String code) {
        assertTrue(task.getValidationIssues().stream().noneMatch(issue -> code.equals(issue.getCode())),
                "Unexpected warning " + code);
    }

    private void assertIssue(List<ProductListingValidationIssue> issues, String fieldKey, String code) {
        assertTrue(issues.stream().anyMatch(issue ->
                        fieldKey.equals(issue.getFieldKey())
                                && code.equals(issue.getCode())
                                && "error".equals(issue.getSeverity())),
                "Expected issue " + fieldKey + "/" + code);
    }

    private static class FakeProductListingMapper implements ProductListingMapper {

        private long nextDraftId = 10001L;
        private long nextTaskId = 20001L;
        private final Map<Long, ProductListingDraftRecord> drafts = new LinkedHashMap<>();
        private final Map<Long, ProductListingTaskRecord> tasks = new LinkedHashMap<>();
        private final Map<String, Long> realRunAttemptClaims = new LinkedHashMap<>();
        private final Map<String, Long> localPartnerSkuProducts = new LinkedHashMap<>();
        private final Map<String, Long> localBarcodeProducts = new LinkedHashMap<>();
        private final Map<Long, Long> localProductListingDraftIds = new LinkedHashMap<>();
        private final Set<String> deletedPartnerSkus = new java.util.LinkedHashSet<>();
        private final ObjectMapper objectMapper = new ObjectMapper();
        private ProductListingDraftRecord insertedDraft;
        private ProductListingTaskRecord insertedTask;
        private int updateCount;

        void seedLocalProduct(
                Long ownerUserId,
                String storeCode,
                String partnerSku,
                String barcode,
                Long productMasterId,
                Long listingDraftId
        ) {
            localPartnerSkuProducts.put(localProductKey(ownerUserId, storeCode, partnerSku), productMasterId);
            localBarcodeProducts.put(localProductKey(ownerUserId, storeCode, barcode), productMasterId);
            if (listingDraftId != null) {
                localProductListingDraftIds.put(productMasterId, listingDraftId);
            }
        }

        @Override
        public int allocateProductListingId(IdSequenceCommand command) {
            return 1;
        }

        @Override
        public Long nextProductListingDraftId() {
            return nextDraftId++;
        }

        @Override
        public Long nextProductListingTaskId() {
            return nextTaskId++;
        }

        @Override
        public int insertDraft(ProductListingDraftRecord draft) {
            insertedDraft = draft;
            drafts.put(draft.getId(), draft);
            return 1;
        }

        @Override
        public int updateDraft(ProductListingDraftRecord draft) {
            updateCount++;
            drafts.put(draft.getId(), draft);
            return 1;
        }

        @Override
        public ProductListingDraftRecord selectDraftById(Long draftId, Long ownerUserId) {
            ProductListingDraftRecord draft = drafts.get(draftId);
            if (draft == null || !ownerUserId.equals(draft.getOwnerUserId())) {
                return null;
            }
            return draft;
        }

        @Override
        public ProductListingDraftRecord selectDraftByIdForUpdate(Long draftId, Long ownerUserId) {
            return selectDraftById(draftId, ownerUserId);
        }

        @Override
        public Long findActiveDraftId(Long ownerUserId, String storeCode, String sourceType, Long sourceRefId) {
            Long latest = null;
            for (ProductListingDraftRecord draft : drafts.values()) {
                if (!ownerUserId.equals(draft.getOwnerUserId())
                        || !storeCode.equals(draft.getStoreCode())
                        || !sourceType.equals(draft.getSourceType())
                        || !sourceRefId.equals(draft.getSourceRefId())
                        || !List.of("draft", "validation_failed", "ready_for_dry_run").contains(draft.getStatus())) {
                    continue;
                }
                if (latest == null || draft.getId() > latest) {
                    latest = draft.getId();
                }
            }
            return latest;
        }

        @Override
        public List<ProductListingDraftRecord> selectRecentDrafts(Long ownerUserId, String storeCode, int limit) {
            List<ProductListingDraftRecord> result = new ArrayList<>();
            for (ProductListingDraftRecord draft : drafts.values()) {
                if (ownerUserId.equals(draft.getOwnerUserId())
                        && storeCode.equals(draft.getStoreCode())
                        && List.of("draft", "validation_failed", "ready_for_dry_run").contains(draft.getStatus())) {
                    result.add(draft);
                }
            }
            result.sort((left, right) -> Long.compare(right.getId(), left.getId()));
            if (result.size() <= limit) {
                return result;
            }
            return new ArrayList<>(result.subList(0, limit));
        }

        @Override
        public int insertTask(ProductListingTaskRecord task) {
            insertedTask = task;
            tasks.put(task.getId(), task);
            return 1;
        }

        @Override
        public ProductListingTaskRecord selectTaskById(Long taskId, Long ownerUserId) {
            ProductListingTaskRecord task = tasks.get(taskId);
            if (task == null || !ownerUserId.equals(task.getOwnerUserId())) {
                return null;
            }
            return task;
        }

        @Override
        public ProductListingTaskRecord selectTaskByIdForUpdate(Long taskId, Long ownerUserId) {
            return selectTaskById(taskId, ownerUserId);
        }

        @Override
        public ProductListingTaskRecord selectTaskByIdForWorker(Long taskId) {
            return tasks.get(taskId);
        }

        @Override
        public List<ProductListingTaskRecord> selectRecentTasks(Long ownerUserId, String storeCode, int limit) {
            List<ProductListingTaskRecord> result = new ArrayList<>();
            for (ProductListingTaskRecord task : tasks.values()) {
                if (ownerUserId.equals(task.getOwnerUserId()) && storeCode.equals(task.getStoreCode())) {
                    result.add(task);
                }
            }
            return result;
        }

        @Override
        public List<ProductListingTaskRecord> selectRecentTasksByDraftId(
                Long ownerUserId,
                String storeCode,
                Long draftId,
                int limit
        ) {
            return selectRecentTasks(ownerUserId, storeCode, limit).stream()
                    .filter(task -> draftId.equals(task.getDraftId()))
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public ProductListingTaskRecord selectCurrentRealRunTaskByDraftId(Long ownerUserId, Long draftId) {
            return tasks.values().stream()
                    .filter(task -> ownerUserId.equals(task.getOwnerUserId()))
                    .filter(task -> draftId.equals(task.getDraftId()))
                    .filter(task -> "REAL_RUN".equals(task.getMode()))
                    .filter(task -> !isExplicitlyReopenedNotStarted(task))
                    .max(java.util.Comparator.comparing(ProductListingTaskRecord::getId))
                    .orElse(null);
        }

        @Override
        public ProductListingTaskRecord selectLatestDryRunTaskByDraftId(Long ownerUserId, Long draftId) {
            return tasks.values().stream()
                    .filter(task -> ownerUserId.equals(task.getOwnerUserId()))
                    .filter(task -> draftId.equals(task.getDraftId()))
                    .filter(task -> "DRY_RUN".equals(task.getMode()))
                    .max(java.util.Comparator.comparing(ProductListingTaskRecord::getId))
                    .orElse(null);
        }

        @Override
        public int markValidatedDryRunSuperseded(Long taskId, Long ownerUserId) {
            ProductListingTaskRecord task = tasks.get(taskId);
            if (task == null
                    || !ownerUserId.equals(task.getOwnerUserId())
                    || !"DRY_RUN".equals(task.getMode())
                    || !List.of("validated", "validation_failed").contains(task.getStatus())) {
                return 0;
            }
            task.setStatus("superseded");
            return 1;
        }

        @Override
        public int persistRecoveredCreateReference(
                Long taskId,
                Long ownerUserId,
                String expectedNoonResultJson,
                String newNoonResultJson
        ) {
            ProductListingTaskRecord task = tasks.get(taskId);
            if (task == null
                    || !ownerUserId.equals(task.getOwnerUserId())
                    || !java.util.Objects.equals(expectedNoonResultJson, task.getNoonResultJson())) {
                return 0;
            }
            task.setNoonResultJson(newNoonResultJson);
            return 1;
        }

        @Override
        public int markCreateOutcomeLookupAuthenticationRequired(
                Long taskId,
                Long ownerUserId,
                String expectedNoonResultJson,
                String newNoonResultJson
        ) {
            ProductListingTaskRecord task = tasks.get(taskId);
            if (task == null
                    || !ownerUserId.equals(task.getOwnerUserId())
                    || !java.util.Objects.equals(expectedNoonResultJson, task.getNoonResultJson())) {
                return 0;
            }
            task.setNoonResultJson(newNoonResultJson);
            task.setFailureCategory("authentication");
            task.setFailureCode("noon_auth_required");
            return 1;
        }

        @Override
        public int claimRealRunAttempt(Long ownerUserId, Long sourceTaskId, Long attemptTaskId) {
            String key = ownerUserId + ":" + sourceTaskId;
            if (realRunAttemptClaims.containsKey(key)) {
                return 0;
            }
            realRunAttemptClaims.put(key, attemptTaskId);
            return 1;
        }

        @Override
        public ProductListingTaskRecord selectRealWriteAttemptTaskBySourceTaskId(Long ownerUserId, Long sourceTaskId) {
            for (ProductListingTaskRecord task : tasks.values()) {
                if (ownerUserId.equals(task.getOwnerUserId())
                        && sourceTaskId.equals(task.getSourceTaskId())
                        && "REAL_RUN".equals(task.getMode())
                        && isRealWriteAttemptLocked(task)) {
                    return task;
                }
            }
            return null;
        }

        @Override
        public ProductListingTaskRecord selectListedPartnerSkuTask(Long ownerUserId, String storeCode, String partnerSku) {
            ProductListingTaskRecord latest = null;
            for (ProductListingTaskRecord task : tasks.values()) {
                if (!ownerUserId.equals(task.getOwnerUserId())
                        || !storeCode.equals(task.getStoreCode())
                        || !"REAL_RUN".equals(task.getMode())
                        || !isKnownListedPartnerSkuTask(task)
                        || !normalize(partnerSku).equalsIgnoreCase(normalize(readPartnerSku(task)))) {
                    continue;
                }
                if (deletedPartnerSkus.contains(localProductKey(ownerUserId, storeCode, partnerSku))) {
                    continue;
                }
                if (latest == null || task.getId() > latest.getId()) {
                    latest = task;
                }
            }
            return latest;
        }

        @Override
        public ProductListingTaskRecord selectReservedBarcodeTask(Long ownerUserId, String storeCode, String barcode) {
            ProductListingTaskRecord latest = null;
            for (ProductListingTaskRecord task : tasks.values()) {
                if (!ownerUserId.equals(task.getOwnerUserId())
                        || !storeCode.equals(task.getStoreCode())
                        || !"REAL_RUN".equals(task.getMode())
                        || !List.of("submitted", "running", "succeeded", "written_verify_failed").contains(task.getStatus())
                        || !normalize(barcode).equalsIgnoreCase(normalize(readBarcode(task)))) {
                    continue;
                }
                if (deletedPartnerSkus.contains(localProductKey(ownerUserId, storeCode, readPartnerSku(task)))) {
                    continue;
                }
                if (latest == null || task.getId() > latest.getId()) {
                    latest = task;
                }
            }
            return latest;
        }

        @Override
        public Integer acquireIdentityLock(String lockKey, int timeoutSeconds) {
            return 1;
        }

        @Override
        public Integer releaseIdentityLock(String lockKey) {
            return 1;
        }

        @Override
        public Long selectLocalProductIdByPartnerSku(
                Long ownerUserId,
                String storeCode,
                String partnerSku,
                Long excludeListingDraftId
        ) {
            return localProductIdUnlessOwnedByDraft(
                    localPartnerSkuProducts.get(localProductKey(ownerUserId, storeCode, partnerSku)),
                    excludeListingDraftId
            );
        }

        @Override
        public Long selectLocalProductIdByBarcode(
                Long ownerUserId,
                String storeCode,
                String barcode,
                Long excludeListingDraftId
        ) {
            return localProductIdUnlessOwnedByDraft(
                    localBarcodeProducts.get(localProductKey(ownerUserId, storeCode, barcode)),
                    excludeListingDraftId
            );
        }

        @Override
        public ProductListingTaskRecord selectLatestRealRunTaskByDraftSource(
                Long ownerUserId,
                String storeCode,
                String sourceType,
                Long sourceRefId
        ) {
            ProductListingTaskRecord latest = null;
            for (ProductListingTaskRecord task : tasks.values()) {
                ProductListingDraftRecord draft = drafts.get(task.getDraftId());
                if (draft == null
                        || !ownerUserId.equals(draft.getOwnerUserId())
                        || !storeCode.equals(draft.getStoreCode())
                        || !sourceType.equals(draft.getSourceType())
                        || !sourceRefId.equals(draft.getSourceRefId())
                        || !"REAL_RUN".equals(task.getMode())) {
                    continue;
                }
                if (latest == null || task.getId() > latest.getId()) {
                    latest = task;
                }
            }
            return latest;
        }

        @Override
        public List<ProductListingTaskRecord> selectRunnableRealRunTasks(int limit) {
            List<ProductListingTaskRecord> result = new ArrayList<>();
            for (ProductListingTaskRecord task : tasks.values()) {
                if ("REAL_RUN".equals(task.getMode()) && "submitted".equals(task.getStatus())) {
                    result.add(task);
                }
            }
            result.sort((left, right) -> Long.compare(left.getId(), right.getId()));
            if (result.size() <= limit) {
                return result;
            }
            return new ArrayList<>(result.subList(0, limit));
        }

        @Override
        public int recoverStaleRunningRealRunTasks(java.time.LocalDateTime staleBefore) {
            int recovered = 0;
            for (ProductListingTaskRecord task : tasks.values()) {
                if ("REAL_RUN".equals(task.getMode())
                        && "running".equals(task.getStatus())
                        && task.getStartedAt() != null
                        && (task.getGmtUpdated() == null
                        ? task.getStartedAt()
                        : task.getGmtUpdated()).isBefore(staleBefore)) {
                    task.setStatus("written_verify_failed");
                    task.setFailureCategory("recovery");
                    task.setFailureCode("real_run_interrupted");
                    task.setFailureMessage("真实上架任务执行中断，需人工核对。");
                    task.setCompletedAt(java.time.LocalDateTime.now());
                    recovered++;
                }
            }
            return recovered;
        }

        @Override
        public int updateTaskResult(ProductListingTaskRecord task) {
            tasks.put(task.getId(), task);
            return 1;
        }

        @Override
        public int updateRunningTaskResult(ProductListingTaskRecord task) {
            return updateTaskResult(task);
        }

        @Override
        public int heartbeatRunningRealRunTask(Long taskId, java.time.LocalDateTime startedAt) {
            ProductListingTaskRecord task = tasks.get(taskId);
            if (task == null
                    || !"REAL_RUN".equals(task.getMode())
                    || !"running".equals(task.getStatus())
                    || !java.util.Objects.equals(task.getStartedAt(), startedAt)) {
                return 0;
            }
            task.setGmtUpdated(java.time.LocalDateTime.now());
            return 1;
        }

        @Override
        public int markTaskRunning(Long taskId, java.time.LocalDateTime startedAt) {
            ProductListingTaskRecord task = tasks.get(taskId);
            if (task == null
                    || !"REAL_RUN".equals(task.getMode())
                    || !"submitted".equals(task.getStatus())) {
                return 0;
            }
            task.setStatus("running");
            task.setStartedAt(startedAt);
            task.setGmtUpdated(startedAt);
            tasks.put(taskId, task);
            return 1;
        }

        private ProductListingDraftRecord insertedDraft() {
            return insertedDraft;
        }

        private ProductListingTaskRecord insertedTask() {
            return insertedTask;
        }

        private void resetUpdateCount() {
            updateCount = 0;
        }

        private int updateCount() {
            return updateCount;
        }

        private boolean isRealWriteAttemptLocked(ProductListingTaskRecord task) {
            return !"real_run_already_active".equals(task.getFailureCode())
                    && !"real_run_already_attempted".equals(task.getFailureCode());
        }

        private boolean isExplicitlyReopenedNotStarted(ProductListingTaskRecord task) {
            ProductListingTaskRecord source = tasks.get(task.getSourceTaskId());
            if (source == null || !"superseded".equals(source.getStatus())) {
                return false;
            }
            ProductListingTaskView view = new ProductListingTaskView();
            view.setMode(task.getMode());
            view.setStatus(task.getStatus());
            view.setFailureCategory(task.getFailureCategory());
            view.setFailureCode(task.getFailureCode());
            view.setFailureMessage(task.getFailureMessage());
            if (task.getNoonResultJson() != null && !task.getNoonResultJson().isBlank()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode root =
                            objectMapper.readTree(task.getNoonResultJson());
                    com.fasterxml.jackson.databind.JsonNode success = root.get("success");
                    com.fasterxml.jackson.databind.JsonNode steps = root.get("steps");
                    if (!root.isObject()
                            || (success != null && !success.isBoolean())
                            || (steps != null && !steps.isArray())) {
                        return false;
                    }
                    view.setNoonResult(objectMapper.treeToValue(
                            root, ProductListingNoonWriteResult.class));
                } catch (Exception exception) {
                    return false;
                }
            }
            ProductListingWorkflowView workflow =
                    new ProductListingWorkflowProjector().project(null, null, view);
            return ("failed".equals(task.getStatus()) || "rejected".equals(task.getStatus()))
                    && workflow.getWriteCertainty()
                    == ProductListingWorkflowView.WriteCertainty.NOT_STARTED;
        }

        private boolean isKnownListedPartnerSkuTask(ProductListingTaskRecord task) {
            return "succeeded".equals(task.getStatus())
                    || "written_verify_failed".equals(task.getStatus())
                    || ("failed".equals(task.getStatus())
                    && "partner_sku_already_exists".equals(task.getFailureCode()));
        }

        private String readPartnerSku(ProductListingTaskRecord task) {
            try {
                ProductListingDraftCommand command = objectMapper.readValue(
                        task.getInputSnapshotJson(),
                        ProductListingDraftCommand.class
                );
                return normalize(command.getPsku());
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to read test partner SKU.", exception);
            }
        }

        private String readBarcode(ProductListingTaskRecord task) {
            try {
                return normalize(objectMapper.readValue(
                        task.getInputSnapshotJson(), ProductListingDraftCommand.class
                ).getBarcode());
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to read test barcode.", exception);
            }
        }

        private String normalize(String value) {
            return value == null ? "" : value.trim();
        }

        private void seedSuccessfulRealRun(Long ownerUserId, String storeCode, ProductListingDraftCommand command) {
            Long draftId = nextProductListingDraftId();
            command.setStoreCode(storeCode);
            ProductListingDraftRecord draft = new ProductListingDraftRecord();
            draft.setId(draftId);
            draft.setOwnerUserId(ownerUserId);
            draft.setStoreCode(storeCode);
            draft.setDraftNo("PLD-" + draftId);
            draft.setStatus("ready_for_dry_run");
            draft.setDraftJson(writeJson(command));
            drafts.put(draftId, draft);

            Long taskId = nextProductListingTaskId();
            ProductListingTaskRecord task = new ProductListingTaskRecord();
            task.setId(taskId);
            task.setDraftId(draftId);
            task.setOwnerUserId(ownerUserId);
            task.setStoreCode(storeCode);
            task.setTaskNo("PLT-" + taskId);
            task.setMode("REAL_RUN");
            task.setStatus("succeeded");
            task.setInputSnapshotJson(writeJson(command));
            tasks.put(taskId, task);
        }

        private void seedSuccessfulProductDelete(Long ownerUserId, String storeCode, String partnerSku) {
            deletedPartnerSkus.add(localProductKey(ownerUserId, storeCode, partnerSku));
        }

        private void seedLocalPartnerSku(Long ownerUserId, String storeCode, String partnerSku, Long productMasterId) {
            localPartnerSkuProducts.put(localProductKey(ownerUserId, storeCode, partnerSku), productMasterId);
        }

        private void seedLocalPartnerSku(
                Long ownerUserId,
                String storeCode,
                String partnerSku,
                Long productMasterId,
                Long listingDraftId
        ) {
            seedLocalPartnerSku(ownerUserId, storeCode, partnerSku, productMasterId);
            localProductListingDraftIds.put(productMasterId, listingDraftId);
        }

        private void seedLocalBarcode(Long ownerUserId, String storeCode, String barcode, Long productMasterId) {
            localBarcodeProducts.put(localProductKey(ownerUserId, storeCode, barcode), productMasterId);
        }

        private void seedLocalBarcode(
                Long ownerUserId,
                String storeCode,
                String barcode,
                Long productMasterId,
                Long listingDraftId
        ) {
            seedLocalBarcode(ownerUserId, storeCode, barcode, productMasterId);
            localProductListingDraftIds.put(productMasterId, listingDraftId);
        }

        private Long localProductIdUnlessOwnedByDraft(Long productMasterId, Long excludeListingDraftId) {
            if (productMasterId == null) {
                return null;
            }
            Long listingDraftId = localProductListingDraftIds.get(productMasterId);
            if (excludeListingDraftId != null && excludeListingDraftId.equals(listingDraftId)) {
                return null;
            }
            return productMasterId;
        }

        private String localProductKey(Long ownerUserId, String storeCode, String value) {
            return ownerUserId + "|" + normalize(storeCode).toUpperCase() + "|" + normalize(value).toUpperCase();
        }

        private String writeJson(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to write test JSON.", exception);
            }
        }
    }
}
