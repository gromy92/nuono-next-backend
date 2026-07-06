package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccountType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        assertWarning(task, "warehouseStock", "warehouse_stock_not_written");
        assertTrue(mapper.insertedTask().getValidationJson().contains("offer_price_not_written"));
        assertTrue(mapper.insertedTask().getValidationJson().contains("offer_note_active_not_written"));
        assertTrue(mapper.insertedTask().getValidationJson().contains("warehouse_stock_not_written"));
    }

    @Test
    void dryRunOmitsSupportedOfferWarningsWhenSplitOfferWriteIsEnabled() {
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setOfferUpsertEnabled(true);
        properties.setOfferSplitWriteEnabled(true);
        service = new ProductListingService(mapper, new ObjectMapper(), new ProductListingValidator(), properties);
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand listing = validCommand();
        listing.setFbp(null);
        listing.setWarehouseId(null);
        listing.setWarehouseCode(null);
        listing.setQuantity(null);
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
        assertNoWarning(task, "warehouse_stock_not_written");
        assertNoWarning(task, "offer_stock_not_written");
    }

    @Test
    void dryRunKeepsWarehouseStockWarningEvenWhenSplitOfferWriteIsEnabled() {
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setOfferUpsertEnabled(true);
        properties.setOfferSplitWriteEnabled(true);
        service = new ProductListingService(mapper, new ObjectMapper(), new ProductListingValidator(), properties);
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validated", task.getStatus());
        assertWarning(task, "warehouseStock", "warehouse_stock_not_written");
        assertNoWarning(task, "offer_price_not_written");
        assertNoWarning(task, "offer_note_active_not_written");
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
    void dryRunTaskFailsWhenHardIssuesExist() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand invalid = validCommand();
        invalid.setPurchasePrice(null);
        ProductListingDraftView draft = service.saveDraft(context, invalid);
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");

        ProductListingTaskView task = service.submitDryRun(context, command);

        assertEquals("validation_failed", task.getStatus());
        assertTrue(task.getValidationIssues().stream()
                .anyMatch(issue -> "purchasePrice".equals(issue.getFieldKey())));
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
        command.setPrice(new BigDecimal("49.90"));
        command.setPurchasePrice(new BigDecimal("19.90"));
        command.setSupplyEvidenceType("1688_OFFER");
        command.setSupplyEvidenceRefId(43101L);
        command.setOptionalPurchaseOrderId(70001L);
        command.setFbp(true);
        command.setWarehouseId("W00752151SA");
        command.setQuantity(100);
        command.setIdWarranty(24);
        command.setBarcode("6290000000001");
        return command;
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

    private static class FakeProductListingMapper implements ProductListingMapper {

        private long nextDraftId = 10001L;
        private long nextTaskId = 20001L;
        private final Map<Long, ProductListingDraftRecord> drafts = new LinkedHashMap<>();
        private final Map<Long, ProductListingTaskRecord> tasks = new LinkedHashMap<>();
        private ProductListingDraftRecord insertedDraft;
        private ProductListingTaskRecord insertedTask;
        private int updateCount;

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
        public ProductListingTaskRecord selectRealWriteAttemptTaskBySourceTaskId(Long ownerUserId, Long sourceTaskId) {
            for (ProductListingTaskRecord task : tasks.values()) {
                if (ownerUserId.equals(task.getOwnerUserId())
                        && sourceTaskId.equals(task.getSourceTaskId())
                        && "REAL_RUN".equals(task.getMode())
                        && ("running".equals(task.getStatus())
                        || "submitted".equals(task.getStatus())
                        || "succeeded".equals(task.getStatus())
                        || "failed".equals(task.getStatus())
                        || "written_verify_failed".equals(task.getStatus()))) {
                    return task;
                }
            }
            return null;
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
        public int updateTaskResult(ProductListingTaskRecord task) {
            tasks.put(task.getId(), task);
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
    }
}
