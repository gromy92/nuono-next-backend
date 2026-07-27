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

class ProductListingDraftServiceTest extends ProductListingServiceTest {
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

}
