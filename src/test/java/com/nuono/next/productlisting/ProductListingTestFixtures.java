package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProductListingTestFixtures {

    private ProductListingTestFixtures() {
    }

    static ProductListingService service(
            FakeProductListingMapper mapper,
            boolean realWriteEnabled,
            ProductListingNoonWriteAdapter adapter
    ) {
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setEnabled(realWriteEnabled);
        return new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                properties,
                adapter
        );
    }

    static ProductListingTaskView validatedDryRun(
            ProductListingService service,
            BusinessAccessContext context
    ) {
        ProductListingDraftView draft = service.saveDraft(context, validCommand());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");
        return service.submitDryRun(context, command);
    }

    static ProductListingRealRunCommand confirmedCommand() {
        ProductListingRealRunCommand command = new ProductListingRealRunCommand();
        command.setConfirmRealNoonWrite(true);
        command.setConfirmationNote("I understand this will write to Noon.");
        return command;
    }

    static ProductListingDraftCommand validCommand() {
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
        command.setFbp(true);
        command.setWarehouseId("73001");
        command.setWarehouseCode("W00752151SA");
        command.setQuantity(100);
        command.setIdWarranty(24);
        command.setBarcode("6290000000001");
        return command;
    }

    static BusinessAccessContext businessContext(Long ownerUserId, Long sessionUserId, String storeCode) {
        return businessContext(ownerUserId, sessionUserId, Set.of(storeCode));
    }

    static BusinessAccessContext businessContext(Long ownerUserId, Long sessionUserId, Set<String> storeCodes) {
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

    static class TrackingNoonWriteAdapter implements ProductListingNoonWriteAdapter {

        private final ProductListingNoonWriteResult result;
        private final ProductListingNoonWriteResult continuationResult;
        private final ProductListingNoonWriteStepResult readBackStep;
        private int callCount;
        private int continueAfterCreateCallCount;
        private int verifyReadBackCallCount;
        private ProductListingNoonWriteRequest lastRequest;
        private String lastContinueSkuParent;
        private String lastContinuePskuCode;
        private String lastReadBackSkuParent;
        private String lastReadBackPskuCode;

        TrackingNoonWriteAdapter(ProductListingNoonWriteResult result) {
            this(result, null, null);
        }

        TrackingNoonWriteAdapter(
                ProductListingNoonWriteResult result,
                ProductListingNoonWriteStepResult readBackStep
        ) {
            this(result, null, readBackStep);
        }

        TrackingNoonWriteAdapter(
                ProductListingNoonWriteResult result,
                ProductListingNoonWriteResult continuationResult,
                ProductListingNoonWriteStepResult readBackStep
        ) {
            this.result = result;
            this.continuationResult = continuationResult;
            this.readBackStep = readBackStep;
        }

        @Override
        public ProductListingNoonWriteResult execute(ProductListingNoonWriteRequest request) {
            callCount++;
            lastRequest = request;
            return result;
        }

        @Override
        public ProductListingNoonWriteResult continueAfterCreate(
                ProductListingNoonWriteRequest request,
                String skuParent,
                String pskuCode
        ) {
            continueAfterCreateCallCount++;
            lastRequest = request;
            lastContinueSkuParent = skuParent;
            lastContinuePskuCode = pskuCode;
            return continuationResult;
        }

        @Override
        public ProductListingNoonWriteStepResult verifyReadBack(
                ProductListingNoonWriteRequest request,
                String skuParent,
                String pskuCode,
                List<String> expectedImageValues
        ) {
            verifyReadBackCallCount++;
            lastRequest = request;
            lastReadBackSkuParent = skuParent;
            lastReadBackPskuCode = pskuCode;
            return readBackStep;
        }

        int callCount() {
            return callCount;
        }

        int continueAfterCreateCallCount() {
            return continueAfterCreateCallCount;
        }

        int verifyReadBackCallCount() {
            return verifyReadBackCallCount;
        }

        String lastContinueSkuParent() {
            return lastContinueSkuParent;
        }

        String lastContinuePskuCode() {
            return lastContinuePskuCode;
        }

        String lastReadBackSkuParent() {
            return lastReadBackSkuParent;
        }

        String lastReadBackPskuCode() {
            return lastReadBackPskuCode;
        }

        ProductListingNoonWriteRequest lastRequest() {
            return lastRequest;
        }
    }

    static class FakeProductListingMapper implements ProductListingMapper {

        private long nextDraftId = 10001L;
        private long nextTaskId = 20001L;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final Map<Long, ProductListingDraftRecord> drafts = new LinkedHashMap<>();
        private final Map<Long, ProductListingTaskRecord> tasks = new LinkedHashMap<>();
        private ProductListingTaskRecord insertedTask;
        private ProductListingTaskRecord updatedTask;

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
            drafts.put(draft.getId(), draft);
            return 1;
        }

        @Override
        public int updateDraft(ProductListingDraftRecord draft) {
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
                if (latest == null || task.getId() > latest.getId()) {
                    latest = task;
                }
            }
            return latest;
        }

        @Override
        public Long selectLocalProductIdByPartnerSku(
                Long ownerUserId,
                String storeCode,
                String partnerSku,
                Long excludeListingDraftId
        ) {
            return null;
        }

        @Override
        public Long selectLocalProductIdByBarcode(
                Long ownerUserId,
                String storeCode,
                String barcode,
                Long excludeListingDraftId
        ) {
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
                        && task.getStartedAt().isBefore(staleBefore)) {
                    task.setStatus("submitted");
                    task.setStartedAt(null);
                    recovered++;
                }
            }
            return recovered;
        }

        @Override
        public int updateTaskResult(ProductListingTaskRecord task) {
            updatedTask = task;
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

        ProductListingTaskRecord insertedTask() {
            return insertedTask;
        }

        ProductListingTaskRecord updatedTask() {
            return updatedTask;
        }

        void forceRunning(Long taskId, java.time.LocalDateTime startedAt) {
            ProductListingTaskRecord task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }
            task.setStatus("running");
            task.setStartedAt(startedAt);
            tasks.put(taskId, task);
        }

        private boolean isRealWriteAttemptLocked(ProductListingTaskRecord task) {
            return "running".equals(task.getStatus())
                    || "submitted".equals(task.getStatus())
                    || "succeeded".equals(task.getStatus())
                    || "written_verify_failed".equals(task.getStatus())
                    || ("failed".equals(task.getStatus())
                    && "partner_sku_already_exists".equals(task.getFailureCode()));
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

        private String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
