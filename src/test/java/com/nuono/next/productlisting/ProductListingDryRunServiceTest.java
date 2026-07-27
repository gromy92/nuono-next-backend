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

class ProductListingDryRunServiceTest extends ProductListingServiceTest {
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

}
