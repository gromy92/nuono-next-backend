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
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
final class ProductListingTestFixtures {
    private ProductListingTestFixtures() {}
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
                adapter,
                null,
                successfulProjectionProvider()
        );
    }
    private static ObjectProvider<ProductListingProjectionBackfill> successfulProjectionProvider() {
        ProductListingProjectionBackfill backfill = new ProductListingProjectionBackfill() {
            @Override
            public void backfillDraftListing(
                    ProductListingDraftRecord record,
                    ProductListingDraftCommand draft
            ) {}
            @Override
            public boolean backfillSuccessfulListing(
                    ProductListingTaskRecord task,
                    ProductListingDraftCommand draft,
                    ProductListingNoonWriteResult result
            ) {
                return true;
            }
        };
        return new ObjectProvider<>() {
            @Override
            public ProductListingProjectionBackfill getObject(Object... args) {
                return backfill;
            }
            @Override
            public ProductListingProjectionBackfill getIfAvailable() {
                return backfill;
            }
            @Override
            public ProductListingProjectionBackfill getIfUnique() {
                return backfill;
            }
            @Override
            public ProductListingProjectionBackfill getObject() {
                return backfill;
            }
        };
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
        command.setIdWarranty(24);
        command.setIsActive(Boolean.TRUE);
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
    static class TrackingNoonWriteAdapter extends ProductListingTrackingNoonWriteAdapter {
        TrackingNoonWriteAdapter(ProductListingNoonWriteResult result) { super(result); }
        TrackingNoonWriteAdapter(ProductListingNoonWriteResult result, ProductListingNoonWriteStepResult readBackStep) {
            super(result, readBackStep);
        }
        TrackingNoonWriteAdapter(ProductListingNoonWriteResult result, ProductListingNoonWriteResult continuationResult, ProductListingNoonWriteStepResult readBackStep) {
            super(result, continuationResult, readBackStep);
        }
        @Override
        TrackingNoonWriteAdapter withCreateReferenceStep(ProductListingNoonWriteStepResult step) {
            super.withCreateReferenceStep(step);
            return this;
        }
    }
    static class FakeProductListingMapper implements ProductListingMapper {
        private long nextDraftId = 10001L;
        private long nextTaskId = 20001L;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final Map<Long, ProductListingDraftRecord> drafts = new LinkedHashMap<>();
        private final Map<Long, ProductListingTaskRecord> tasks = new LinkedHashMap<>();
        private final Map<String, Long> realRunAttemptClaims = new LinkedHashMap<>();
        private final ProductListingFakeLeaseSupport leaseSupport =
                new ProductListingFakeLeaseSupport(tasks);
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
        public ProductListingDraftRecord selectDraftByIdForUpdate(Long draftId, Long ownerUserId) {
            return selectDraftById(draftId, ownerUserId);
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
            ProductListingTaskRecord current = null;
            int currentPriority = Integer.MAX_VALUE;
            for (ProductListingTaskRecord task : tasks.values()) {
                if (!ownerUserId.equals(task.getOwnerUserId())
                        || !draftId.equals(task.getDraftId())
                        || !"REAL_RUN".equals(task.getMode())
                        || isExplicitlyReopenedNotStarted(task)) {
                    continue;
                }
                int priority = workflowRealRunPriority(task);
                if (current == null
                        || priority < currentPriority
                        || (priority == currentPriority && task.getId() > current.getId())) {
                    current = task;
                    currentPriority = priority;
                }
            }
            return current;
        }
        @Override
        public ProductListingTaskRecord selectLatestDryRunTaskByDraftId(Long ownerUserId, Long draftId) {
            ProductListingTaskRecord latest = null;
            for (ProductListingTaskRecord task : tasks.values()) {
                if (ownerUserId.equals(task.getOwnerUserId())
                        && draftId.equals(task.getDraftId())
                        && "DRY_RUN".equals(task.getMode())
                        && (latest == null || task.getId() > latest.getId())) {
                    latest = task;
                }
            }
            return latest;
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
            task.setFailureCategory("workflow");
            task.setFailureCode("review_reopened");
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
                    || !Objects.equals(expectedNoonResultJson, task.getNoonResultJson())) {
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
                    || !Objects.equals(expectedNoonResultJson, task.getNoonResultJson())) {
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
                        || !isReservedIdentityTask(task, false)
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
        public ProductListingTaskRecord selectReservedBarcodeTask(Long ownerUserId, String storeCode, String barcode) {
            ProductListingTaskRecord latest = null;
            for (ProductListingTaskRecord task : tasks.values()) {
                if (!ownerUserId.equals(task.getOwnerUserId())
                        || !storeCode.equals(task.getStoreCode())
                        || !"REAL_RUN".equals(task.getMode())
                        || !isReservedIdentityTask(task, true)
                        || !normalize(barcode).equalsIgnoreCase(normalize(readBarcode(task)))) {
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
            return leaseSupport.recoverStale(staleBefore);
        }
        @Override
        public int updateTaskResult(ProductListingTaskRecord task) {
            updatedTask = task;
            leaseSupport.release(task.getId());
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
            return leaseSupport.markRunning(taskId, startedAt);
        }
        @Override
        public int markTaskRecoveryRunning(
                Long taskId, Long ownerUserId, String expectedStatus,
                java.time.LocalDateTime startedAt
        ) {
            return leaseSupport.markRecovery(taskId, ownerUserId, expectedStatus, startedAt);
        }
        @Override
        public int heartbeatRunningTask(
                Long taskId, Long ownerUserId, java.time.LocalDateTime startedAt) {
            return leaseSupport.heartbeat(taskId, ownerUserId, startedAt);
        }
        @Override
        public int checkpointRunningTaskNoonResult(
                Long taskId, Long ownerUserId, String noonResultJson, java.time.LocalDateTime startedAt) {
            return leaseSupport.checkpoint(taskId, ownerUserId, noonResultJson, startedAt);
        }
        @Override
        public int completeRunningTaskResult(
                ProductListingTaskRecord task, java.time.LocalDateTime expectedStartedAt) {
            int completed = leaseSupport.complete(task, expectedStartedAt);
            if (completed == 1) {
                updatedTask = task;
            }
            return completed;
        }
        ProductListingTaskRecord insertedTask() {
            return insertedTask;
        }
        ProductListingTaskRecord updatedTask() {
            return updatedTask;
        }
        void forceRunning(Long taskId, java.time.LocalDateTime startedAt) {
            leaseSupport.forceRunning(taskId, startedAt);
        }
        void forceLeaseLoss(Long taskId) {
            leaseSupport.forceLoss(taskId);
        }
        private boolean isRealWriteAttemptLocked(ProductListingTaskRecord task) {
            return !"real_run_already_active".equals(task.getFailureCode())
                    && !"real_run_already_attempted".equals(task.getFailureCode());
        }
        private int workflowRealRunPriority(ProductListingTaskRecord task) {
            if (List.of("submitted", "running", "written_verify_failed").contains(task.getStatus())) {
                return 0;
            }
            if ("succeeded".equals(task.getStatus())) {
                return 1;
            }
            return 2;
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
        private boolean isReservedIdentityTask(ProductListingTaskRecord task, boolean barcode) {
            return List.of("submitted", "running", "succeeded", "written_verify_failed")
                    .contains(task.getStatus())
                    || ("failed".equals(task.getStatus())
                    && (ProductListingWriteAuthRecovery.FAILURE_CODE.equals(task.getFailureCode())
                    || (!barcode && "partner_sku_already_exists".equals(task.getFailureCode()))));
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
    }
}
