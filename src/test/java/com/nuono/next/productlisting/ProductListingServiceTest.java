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

abstract class ProductListingServiceTest {

    protected FakeProductListingMapper mapper;
    protected ProductListingService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeProductListingMapper();
        service = new ProductListingService(mapper, new ObjectMapper(), new ProductListingValidator());
    }

    protected ProductListingDraftCommand validCommand() {
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

    protected ProductListingDraftCommand validProductRebuildCommand(Long productMasterId) {
        ProductListingDraftCommand command = validCommand();
        command.setSourceType("PRODUCT_REBUILD");
        command.setSourceRefId(productMasterId);
        command.setRebuildSourceProductMasterId(productMasterId);
        return command;
    }

    protected static byte[] jpeg(int width, int height) {
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

    protected static final class CountingImageDownloader
            implements ProductListingImageDownloader {
        final byte[] imageBytes;
        int downloadCount;

        CountingImageDownloader(byte[] imageBytes) {
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

    protected BusinessAccessContext businessContext(Long ownerUserId, Long sessionUserId, String storeCode) {
        return businessContext(ownerUserId, sessionUserId, Set.of(storeCode));
    }

    protected BusinessAccessContext businessContext(Long ownerUserId, Long sessionUserId, Set<String> storeCodes) {
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

    protected void assertWarning(ProductListingTaskView task, String fieldKey, String code) {
        assertTrue(task.getValidationIssues().stream().anyMatch(issue ->
                        fieldKey.equals(issue.getFieldKey())
                                && code.equals(issue.getCode())
                                && "warning".equals(issue.getSeverity())),
                "Expected warning " + fieldKey + "/" + code);
    }

    protected void assertNoWarning(ProductListingTaskView task, String code) {
        assertTrue(task.getValidationIssues().stream().noneMatch(issue -> code.equals(issue.getCode())),
                "Unexpected warning " + code);
    }

    protected void assertIssue(List<ProductListingValidationIssue> issues, String fieldKey, String code) {
        assertTrue(issues.stream().anyMatch(issue ->
                        fieldKey.equals(issue.getFieldKey())
                                && code.equals(issue.getCode())
                                && "error".equals(issue.getSeverity())),
                "Expected issue " + fieldKey + "/" + code);
    }

    protected static class FakeProductListingMapper implements ProductListingMapper {

        long nextDraftId = 10001L;
        long nextTaskId = 20001L;
        final Map<Long, ProductListingDraftRecord> drafts = new LinkedHashMap<>();
        final Map<Long, ProductListingTaskRecord> tasks = new LinkedHashMap<>();
        final Map<String, Long> realRunAttemptClaims = new LinkedHashMap<>();
        final Map<String, Long> localPartnerSkuProducts = new LinkedHashMap<>();
        final Map<String, Long> localBarcodeProducts = new LinkedHashMap<>();
        final Map<Long, Long> localProductListingDraftIds = new LinkedHashMap<>();
        final Set<String> deletedPartnerSkus = new java.util.LinkedHashSet<>();
        final ObjectMapper objectMapper = new ObjectMapper();
        ProductListingDraftRecord insertedDraft;
        ProductListingTaskRecord insertedTask;
        int updateCount;

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

        ProductListingDraftRecord insertedDraft() {
            return insertedDraft;
        }

        ProductListingTaskRecord insertedTask() {
            return insertedTask;
        }

        void resetUpdateCount() {
            updateCount = 0;
        }

        int updateCount() {
            return updateCount;
        }

        boolean isRealWriteAttemptLocked(ProductListingTaskRecord task) {
            return !"real_run_already_active".equals(task.getFailureCode())
                    && !"real_run_already_attempted".equals(task.getFailureCode());
        }

        boolean isExplicitlyReopenedNotStarted(ProductListingTaskRecord task) {
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

        boolean isKnownListedPartnerSkuTask(ProductListingTaskRecord task) {
            return "succeeded".equals(task.getStatus())
                    || "written_verify_failed".equals(task.getStatus())
                    || ("failed".equals(task.getStatus())
                    && "partner_sku_already_exists".equals(task.getFailureCode()));
        }

        String readPartnerSku(ProductListingTaskRecord task) {
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

        String readBarcode(ProductListingTaskRecord task) {
            try {
                return normalize(objectMapper.readValue(
                        task.getInputSnapshotJson(), ProductListingDraftCommand.class
                ).getBarcode());
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to read test barcode.", exception);
            }
        }

        String normalize(String value) {
            return value == null ? "" : value.trim();
        }

        void seedSuccessfulRealRun(Long ownerUserId, String storeCode, ProductListingDraftCommand command) {
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

        void seedSuccessfulProductDelete(Long ownerUserId, String storeCode, String partnerSku) {
            deletedPartnerSkus.add(localProductKey(ownerUserId, storeCode, partnerSku));
        }

        void seedLocalPartnerSku(Long ownerUserId, String storeCode, String partnerSku, Long productMasterId) {
            localPartnerSkuProducts.put(localProductKey(ownerUserId, storeCode, partnerSku), productMasterId);
        }

        void seedLocalPartnerSku(
                Long ownerUserId,
                String storeCode,
                String partnerSku,
                Long productMasterId,
                Long listingDraftId
        ) {
            seedLocalPartnerSku(ownerUserId, storeCode, partnerSku, productMasterId);
            localProductListingDraftIds.put(productMasterId, listingDraftId);
        }

        void seedLocalBarcode(Long ownerUserId, String storeCode, String barcode, Long productMasterId) {
            localBarcodeProducts.put(localProductKey(ownerUserId, storeCode, barcode), productMasterId);
        }

        void seedLocalBarcode(
                Long ownerUserId,
                String storeCode,
                String barcode,
                Long productMasterId,
                Long listingDraftId
        ) {
            seedLocalBarcode(ownerUserId, storeCode, barcode, productMasterId);
            localProductListingDraftIds.put(productMasterId, listingDraftId);
        }

        Long localProductIdUnlessOwnedByDraft(Long productMasterId, Long excludeListingDraftId) {
            if (productMasterId == null) {
                return null;
            }
            Long listingDraftId = localProductListingDraftIds.get(productMasterId);
            if (excludeListingDraftId != null && excludeListingDraftId.equals(listingDraftId)) {
                return null;
            }
            return productMasterId;
        }

        String localProductKey(Long ownerUserId, String storeCode, String value) {
            return ownerUserId + "|" + normalize(storeCode).toUpperCase() + "|" + normalize(value).toUpperCase();
        }

        String writeJson(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to write test JSON.", exception);
            }
        }
    }
}
