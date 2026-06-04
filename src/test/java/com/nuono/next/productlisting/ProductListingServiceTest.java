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
        useRealWrite(false);
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

    @Test
    void confirmRealRunPersistsRejectedTaskWithoutConfirmation() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun = validatedDryRun(context);
        mapper.resetInsertedTask();

        ProductListingTaskView realRun = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                new ProductListingRealRunCommand()
        );

        assertEquals("REAL_RUN", realRun.getMode());
        assertEquals("rejected", realRun.getStatus());
        assertEquals(dryRun.getTaskId(), realRun.getSourceTaskId());
        assertEquals("guard", realRun.getFailureCategory());
        assertEquals("confirmation_required", realRun.getFailureCode());
        assertEquals("REAL_RUN", mapper.insertedTask().getMode());
        assertEquals("rejected", mapper.insertedTask().getStatus());
        assertEquals(dryRun.getTaskId(), mapper.insertedTask().getSourceTaskId());
        assertTrue(mapper.insertedTask().getConfirmationJson().contains("confirmRealNoonWrite"));
    }

    @Test
    void confirmRealRunPersistsRejectedTaskWhenKillSwitchIsDisabled() {
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun = validatedDryRun(context);
        mapper.resetInsertedTask();

        ProductListingTaskView realRun = service.confirmRealRun(context, dryRun.getTaskId(), confirmedCommand());

        assertEquals("rejected", realRun.getStatus());
        assertEquals("guard", realRun.getFailureCategory());
        assertEquals("real_write_disabled", realRun.getFailureCode());
        assertEquals("real_write_disabled", mapper.insertedTask().getFailureCode());
    }

    @Test
    void confirmRealRunRejectsNonValidatedDryRunTask() {
        useRealWrite(true);
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingDraftCommand invalid = validCommand();
        invalid.setPurchasePrice(null);
        ProductListingDraftView draft = service.saveDraft(context, invalid);
        ProductListingDryRunSubmitCommand dryRunCommand = new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draft.getDraftId());
        dryRunCommand.setStoreCode("STR245027-NAE");
        ProductListingTaskView dryRun = service.submitDryRun(context, dryRunCommand);
        mapper.resetInsertedTask();

        ProductListingTaskView realRun = service.confirmRealRun(context, dryRun.getTaskId(), confirmedCommand());

        assertEquals("rejected", realRun.getStatus());
        assertEquals("validation", realRun.getFailureCategory());
        assertEquals("dry_run_not_validated", realRun.getFailureCode());
        assertEquals(dryRun.getTaskId(), mapper.insertedTask().getSourceTaskId());
    }

    @Test
    void confirmRealRunRejectsDuplicateActiveRealRunTask() {
        useRealWrite(true);
        BusinessAccessContext context = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun = validatedDryRun(context);
        mapper.addActiveRealRun(dryRun);
        mapper.resetInsertedTask();

        ProductListingTaskView realRun = service.confirmRealRun(context, dryRun.getTaskId(), confirmedCommand());

        assertEquals("rejected", realRun.getStatus());
        assertEquals("guard", realRun.getFailureCategory());
        assertEquals("real_run_already_active", realRun.getFailureCode());
        assertEquals(dryRun.getTaskId(), mapper.insertedTask().getSourceTaskId());
    }

    @Test
    void confirmRealRunRejectsStoreOutsideSessionScopeBeforeAuditing() {
        useRealWrite(true);
        BusinessAccessContext storeAeContext = businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun = validatedDryRun(storeAeContext);
        BusinessAccessContext storeSaContext = businessContext(10002L, 90002L, "STR245027-NSA");
        mapper.resetInsertedTask();

        assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.confirmRealRun(storeSaContext, dryRun.getTaskId(), confirmedCommand())
        );

        assertEquals(null, mapper.insertedTask());
    }

    private void useRealWrite(boolean enabled) {
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setEnabled(enabled);
        service = new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                properties
        );
    }

    private ProductListingTaskView validatedDryRun(BusinessAccessContext context) {
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");
        return service.submitDryRun(context, command);
    }

    private ProductListingRealRunCommand confirmedCommand() {
        ProductListingRealRunCommand command = new ProductListingRealRunCommand();
        command.setConfirmRealNoonWrite(true);
        command.setConfirmationNote("I understand this will write to Noon.");
        return command;
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
        command.setWarehouseId("73001");
        command.setWarehouseCode("W00752151SA");
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
            return null;
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
                        || "failed".equals(task.getStatus()))) {
                    return task;
                }
            }
            return null;
        }

        @Override
        public int updateTaskResult(ProductListingTaskRecord task) {
            tasks.put(task.getId(), task);
            return 1;
        }

        private ProductListingDraftRecord insertedDraft() {
            return insertedDraft;
        }

        private ProductListingTaskRecord insertedTask() {
            return insertedTask;
        }

        private void resetInsertedTask() {
            insertedTask = null;
        }

        private void addActiveRealRun(ProductListingTaskView dryRun) {
            ProductListingTaskRecord task = new ProductListingTaskRecord();
            task.setId(nextTaskId++);
            task.setDraftId(dryRun.getDraftId());
            task.setOwnerUserId(dryRun.getOwnerUserId());
            task.setStoreCode(dryRun.getStoreCode());
            task.setTaskNo("PLT-" + task.getId());
            task.setMode("REAL_RUN");
            task.setStatus("running");
            task.setSourceTaskId(dryRun.getTaskId());
            tasks.put(task.getId(), task);
        }

        private void resetUpdateCount() {
            updateCount = 0;
        }

        private int updateCount() {
            return updateCount;
        }
    }
}
