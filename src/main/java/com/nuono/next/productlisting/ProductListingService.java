package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class ProductListingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductListingService.class);
    private static final String DRY_RUN_MODE = "DRY_RUN";
    private static final String REAL_RUN_MODE = "REAL_RUN";
    private static final String REAL_RUN_STATUS_WRITTEN_VERIFY_FAILED = "written_verify_failed";
    private static final String PARTNER_SKU_ALREADY_EXISTS_CODE = "partner_sku_already_exists";
    private static final int IDENTITY_LOCK_TIMEOUT_SECONDS = 5;
    private static final Pattern PARTNER_SKU_ALREADY_EXISTS_PATTERN = Pattern.compile(
            "Partner skus? already exists:\\s*\\[\\[\\s*['\"]?([^'\"\\],\\[]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final ProductListingMapper mapper;
    private final ObjectMapper objectMapper;
    private final ProductListingValidator validator;
    private final ProductListingRealWriteProperties realWriteProperties;
    private final ProductListingNoonWriteAdapter noonWriteAdapter;
    private final ApplicationEventPublisher eventPublisher;
    private final ProductListingProjectionBackfill projectionBackfill;

    @Autowired
    public ProductListingService(
            ProductListingMapper mapper,
            ObjectMapper objectMapper,
            ProductListingValidator validator,
            ProductListingRealWriteProperties realWriteProperties,
            ProductListingNoonWriteAdapter noonWriteAdapter,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<ProductListingProjectionBackfill> projectionBackfillProvider
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.realWriteProperties = realWriteProperties == null
                ? new ProductListingRealWriteProperties()
                : realWriteProperties;
        this.noonWriteAdapter = noonWriteAdapter == null
                ? new UnavailableProductListingNoonWriteAdapter()
                : noonWriteAdapter;
        this.eventPublisher = eventPublisher == null ? event -> {
        } : eventPublisher;
        this.projectionBackfill = projectionBackfillProvider == null
                ? ProductListingProjectionBackfill.noop()
                : projectionBackfillProvider.getIfAvailable(ProductListingProjectionBackfill::noop);
    }

    public ProductListingService(
            ProductListingMapper mapper,
            ObjectMapper objectMapper,
            ProductListingValidator validator,
            ProductListingRealWriteProperties realWriteProperties,
            ProductListingNoonWriteAdapter noonWriteAdapter,
            ApplicationEventPublisher eventPublisher
    ) {
        this(mapper, objectMapper, validator, realWriteProperties, noonWriteAdapter, eventPublisher, null);
    }

    public ProductListingService(
            ProductListingMapper mapper,
            ObjectMapper objectMapper,
            ProductListingValidator validator,
            ProductListingRealWriteProperties realWriteProperties,
            ProductListingNoonWriteAdapter noonWriteAdapter
    ) {
        this(mapper, objectMapper, validator, realWriteProperties, noonWriteAdapter, null);
    }

    public ProductListingService(
            ProductListingMapper mapper,
            ObjectMapper objectMapper,
            ProductListingValidator validator,
            ProductListingRealWriteProperties realWriteProperties
    ) {
        this(mapper, objectMapper, validator, realWriteProperties, new UnavailableProductListingNoonWriteAdapter());
    }

    public ProductListingService(
            ProductListingMapper mapper,
            ObjectMapper objectMapper,
            ProductListingValidator validator
    ) {
        this(mapper, objectMapper, validator, new ProductListingRealWriteProperties());
    }

    public ProductListingDraftView saveDraft(
            BusinessAccessContext context,
            ProductListingDraftCommand command
    ) {
        requireContext(context);
        ProductListingDraftCommand safeCommand = requireCommand(command);
        String storeCode = requireStoreCode(safeCommand.getStoreCode());
        requireStoreAccess(context, storeCode);
        Long ownerUserId = resolveOwnerUserId(context, storeCode);
        Long operatorUserId = requireOperatorUserId(context);

        ProductListingDraftRecord existing = null;
        Long draftId = safeCommand.getDraftId();
        if (draftId == null) {
            draftId = activeSourceDraftId(ownerUserId, storeCode, safeCommand);
            if (draftId != null) {
                existing = mapper.selectDraftById(draftId, ownerUserId);
                if (existing == null) {
                    draftId = null;
                }
            }
            if (draftId == null) {
                draftId = mapper.nextProductListingDraftId();
            }
            safeCommand.setDraftId(draftId);
        } else {
            existing = mapper.selectDraftById(draftId, ownerUserId);
            if (existing == null) {
                throw new IllegalArgumentException("Product listing draft not found.");
            }
            requireStoreAccess(context, existing.getStoreCode());
            if (!storeCode.equalsIgnoreCase(existing.getStoreCode())) {
                throw new IllegalArgumentException("Product listing draft store cannot be changed.");
            }
        }
        preserveExistingStableDraftFields(safeCommand, existing);
        List<ProductListingValidationIssue> issues = validateWithRuntimeWarnings(safeCommand, ownerUserId, storeCode);
        String status = hasHardIssues(issues) ? "draft" : "ready_for_dry_run";

        ProductListingDraftRecord record = new ProductListingDraftRecord();
        record.setId(draftId);
        record.setOwnerUserId(ownerUserId);
        record.setStoreCode(storeCode);
        record.setDraftNo(existing == null ? draftNo(draftId) : existing.getDraftNo());
        String sourceType = resolveSourceType(safeCommand, existing);
        Long sourceRefId = resolveSourceRefId(safeCommand, existing);
        safeCommand.setSourceType(sourceType);
        safeCommand.setSourceRefId(sourceRefId);
        record.setSourceType(sourceType);
        record.setSourceRefId(sourceRefId);
        record.setOptionalPurchaseOrderId(safeCommand.getOptionalPurchaseOrderId());
        record.setStatus(status);
        record.setDraftJson(writeJson(safeCommand));
        record.setValidationJson(writeJson(issues));
        record.setCreatedBy(existing == null ? operatorUserId : existing.getCreatedBy());
        record.setUpdatedBy(operatorUserId);

        if (existing == null) {
            mapper.insertDraft(record);
        } else {
            mapper.updateDraft(record);
        }
        backfillDraftProjection(record, safeCommand);
        return draftView(record, safeCommand, issues);
    }

    private Long activeSourceDraftId(
            Long ownerUserId,
            String storeCode,
            ProductListingDraftCommand command
    ) {
        if (!StringUtils.hasText(command.getSourceType()) || command.getSourceRefId() == null) {
            return null;
        }
        return mapper.findActiveDraftId(
                ownerUserId,
                storeCode,
                command.getSourceType().trim(),
                command.getSourceRefId()
        );
    }

    public ProductListingDraftView validateDraft(BusinessAccessContext context, Long draftId) {
        requireContext(context);
        Long ownerUserId = requireOwnerUserId(context);
        ProductListingDraftRecord record = requireDraft(draftId, ownerUserId);
        requireStoreAccess(context, record.getStoreCode());
        ProductListingDraftCommand command = readDraft(record.getDraftJson());
        List<ProductListingValidationIssue> issues = validateWithRuntimeWarnings(command, ownerUserId, record.getStoreCode());
        record.setStatus(hasHardIssues(issues) ? "draft" : "ready_for_dry_run");
        record.setValidationJson(writeJson(issues));
        record.setUpdatedBy(requireOperatorUserId(context));
        mapper.updateDraft(record);
        return draftView(record, command, issues);
    }

    public ProductListingDraftView loadDraft(BusinessAccessContext context, Long draftId) {
        requireContext(context);
        Long ownerUserId = requireOwnerUserId(context);
        ProductListingDraftRecord record = requireDraft(draftId, ownerUserId);
        requireStoreAccess(context, record.getStoreCode());
        return draftView(record, readDraft(record.getDraftJson()), readIssues(record.getValidationJson()));
    }

    public List<ProductListingDraftView> listDrafts(
            BusinessAccessContext context,
            String storeCode,
            int limit
    ) {
        requireContext(context);
        String safeStoreCode = requireStoreCode(storeCode);
        requireStoreAccess(context, safeStoreCode);
        Long ownerUserId = resolveOwnerUserId(context, safeStoreCode);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return mapper.selectRecentDrafts(ownerUserId, safeStoreCode, safeLimit).stream()
                .map(record -> draftView(record, readDraft(record.getDraftJson()), readIssues(record.getValidationJson())))
                .collect(Collectors.toList());
    }

    public ProductListingFieldValidationView validateFields(
            BusinessAccessContext context,
            ProductListingDraftCommand command
    ) {
        requireContext(context);
        ProductListingDraftCommand safeCommand = command == null ? new ProductListingDraftCommand() : command;
        String storeCode = requireStoreCode(safeCommand.getStoreCode());
        requireStoreAccess(context, storeCode);
        Long ownerUserId = resolveOwnerUserId(context, storeCode);
        List<ProductListingValidationIssue> issues = validateDuplicateIdentityFields(
                ownerUserId,
                storeCode,
                safeCommand
        );
        ProductListingFieldValidationView view = new ProductListingFieldValidationView();
        view.setIssues(issues);
        return view;
    }

    public ProductListingTaskView submitDryRun(
            BusinessAccessContext context,
            ProductListingDryRunSubmitCommand command
    ) {
        requireContext(context);
        if (command == null || command.getDraftId() == null) {
            throw new IllegalArgumentException("Product listing draft ID is required.");
        }
        String storeCode = requireStoreCode(command.getStoreCode());
        requireStoreAccess(context, storeCode);
        Long ownerUserId = resolveOwnerUserId(context, storeCode);
        ProductListingDraftRecord draft = requireDraft(command.getDraftId(), ownerUserId);
        if (!storeCode.equalsIgnoreCase(draft.getStoreCode())) {
            throw new IllegalArgumentException("Dry-run store does not match the draft store.");
        }

        ProductListingDraftCommand draftCommand = readDraft(draft.getDraftJson());
        List<ProductListingValidationIssue> issues = validateWithRuntimeWarnings(draftCommand, ownerUserId, draft.getStoreCode());
        boolean failed = hasHardIssues(issues);
        LocalDateTime now = LocalDateTime.now();
        Long taskId = mapper.nextProductListingTaskId();

        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setId(taskId);
        task.setDraftId(draft.getId());
        task.setOwnerUserId(ownerUserId);
        task.setStoreCode(draft.getStoreCode());
        task.setTaskNo(taskNo(taskId));
        task.setMode(DRY_RUN_MODE);
        task.setStatus(failed ? "validation_failed" : "validated");
        task.setInputSnapshotJson(draft.getDraftJson());
        task.setValidationJson(writeJson(issues));
        task.setFailureCode(failed ? "validation_failed" : null);
        task.setFailureMessage(failed ? "Product listing draft has hard validation issues." : null);
        task.setSubmittedBy(requireOperatorUserId(context));
        task.setSubmittedAt(now);
        task.setCompletedAt(now);
        mapper.insertTask(task);
        return taskView(task, issues);
    }

    public ProductListingTaskView loadTask(BusinessAccessContext context, Long taskId) {
        requireContext(context);
        ProductListingTaskRecord task = requireTask(taskId, requireOwnerUserId(context));
        requireStoreAccess(context, task.getStoreCode());
        return taskView(task, readIssues(task.getValidationJson()));
    }

    @Transactional
    public ProductListingRealRunSubmission submitConfirmedRealRunFromDraft(
            BusinessAccessContext context,
            ProductListingDraftCommand draft,
            String confirmationNote
    ) {
        requireContext(context);
        ProductListingDraftView draftView = saveDraft(context, draft);
        ProductListingDryRunSubmitCommand dryRunCommand = new ProductListingDryRunSubmitCommand();
        dryRunCommand.setDraftId(draftView.getDraftId());
        dryRunCommand.setStoreCode(draftView.getStoreCode());
        ProductListingTaskView dryRun = submitDryRun(context, dryRunCommand);
        if (dryRun == null || !"validated".equalsIgnoreCase(dryRun.getStatus())) {
            return new ProductListingRealRunSubmission(draftView, dryRun, null);
        }

        ProductListingRealRunCommand realRunCommand = new ProductListingRealRunCommand();
        realRunCommand.setConfirmRealNoonWrite(true);
        realRunCommand.setConfirmationNote(confirmationNote);
        ProductListingTaskView realRun = confirmRealRun(context, dryRun.getTaskId(), realRunCommand);
        return new ProductListingRealRunSubmission(draftView, dryRun, realRun);
    }

    @Transactional
    public ProductListingTaskView confirmRealRun(
            BusinessAccessContext context,
            Long dryRunTaskId,
            ProductListingRealRunCommand command
    ) {
        requireContext(context);
        Long ownerUserId = requireOwnerUserId(context);
        ProductListingTaskRecord dryRunTask = requireTask(dryRunTaskId, ownerUserId);
        requireStoreAccess(context, dryRunTask.getStoreCode());

        if (!DRY_RUN_MODE.equals(dryRunTask.getMode()) || !"validated".equals(dryRunTask.getStatus())) {
            return insertRejectedRealRunTask(
                    context,
                    dryRunTask,
                    command,
                    "validation",
                    "dry_run_not_validated",
                    "Only validated product listing dry-run tasks can be promoted to real Noon listing."
            );
        }
        ProductListingDraftCommand dryRunDraft = readDraft(dryRunTask.getInputSnapshotJson());
        String partnerSku = normalizeText(dryRunDraft.getPsku());
        String barcode = normalizeText(dryRunDraft.getBarcode());
        List<String> identityLocks = acquireIdentityLocks(
                ownerUserId,
                dryRunTask.getStoreCode(),
                partnerSku,
                barcode
        );
        boolean deferredLockRelease = registerIdentityLockReleaseAfterCommit(identityLocks);
        try {
            ProductListingTaskRecord existingAttempt = mapper.selectRealWriteAttemptTaskBySourceTaskId(
                    ownerUserId,
                    dryRunTask.getId()
            );
            if (existingAttempt != null) {
                return rejectExistingRealWriteAttempt(context, dryRunTask, command, existingAttempt);
            }
            if (StringUtils.hasText(partnerSku)) {
                Long existingProductId = mapper.selectLocalProductIdByPartnerSku(
                        ownerUserId,
                        dryRunTask.getStoreCode(),
                        partnerSku,
                        dryRunTask.getDraftId()
                );
                ProductListingTaskRecord existingPartnerSkuTask = mapper.selectListedPartnerSkuTask(
                        ownerUserId,
                        dryRunTask.getStoreCode(),
                        partnerSku
                );
                if ((existingProductId != null && !isSameRebuildSourceProduct(existingProductId, dryRunDraft))
                        || (existingPartnerSkuTask != null
                        && !isHistoricalRebuildPartnerSkuTask(dryRunTask, dryRunDraft, existingPartnerSkuTask))) {
                    return insertRejectedRealRunTask(
                            context,
                            dryRunTask,
                            command,
                            "validation",
                            PARTNER_SKU_ALREADY_EXISTS_CODE,
                            partnerSkuAlreadyExistsMessage(partnerSku)
                    );
                }
            }
            if (StringUtils.hasText(barcode)) {
                Long existingProductId = mapper.selectLocalProductIdByBarcode(
                        ownerUserId,
                        dryRunTask.getStoreCode(),
                        barcode,
                        dryRunTask.getDraftId()
                );
                ProductListingTaskRecord existingBarcodeTask = mapper.selectReservedBarcodeTask(
                        ownerUserId,
                        dryRunTask.getStoreCode(),
                        barcode
                );
                if ((existingProductId != null && !isSameRebuildSourceProduct(existingProductId, dryRunDraft))
                        || (existingBarcodeTask != null
                        && !isHistoricalRebuildPartnerSkuTask(dryRunTask, dryRunDraft, existingBarcodeTask))) {
                    return insertRejectedRealRunTask(
                            context,
                            dryRunTask,
                            command,
                            "validation",
                            "barcode_already_exists",
                            barcodeAlreadyExistsMessage(barcode)
                    );
                }
            }
            if (command == null || !Boolean.TRUE.equals(command.getConfirmRealNoonWrite())) {
                return insertRejectedRealRunTask(
                        context,
                        dryRunTask,
                        command,
                        "guard",
                        "confirmation_required",
                        "Real Noon listing confirmation is required."
                );
            }
            if (!realWriteProperties.isEnabled()) {
                return insertRejectedRealRunTask(
                        context,
                        dryRunTask,
                        command,
                        "guard",
                        "real_write_disabled",
                        "Real Noon listing writes are disabled by kill switch."
                );
            }

            ProductListingTaskRecord task = newRealRunTask(
                    context,
                    dryRunTask,
                    command,
                    "submitted",
                    null,
                    null,
                    null
            );
            try {
                mapper.insertTask(task);
            } catch (DuplicateKeyException exception) {
                ProductListingTaskRecord duplicateAttempt = mapper.selectRealWriteAttemptTaskBySourceTaskId(
                        ownerUserId,
                        dryRunTask.getId()
                );
                if (duplicateAttempt == null) {
                    throw exception;
                }
                return rejectExistingRealWriteAttempt(context, dryRunTask, command, duplicateAttempt);
            }
            eventPublisher.publishEvent(new ProductListingRealRunSubmittedEvent(task.getId()));
            return taskView(task, readIssues(task.getValidationJson()));
        } finally {
            if (!deferredLockRelease) {
                releaseIdentityLocks(identityLocks);
            }
        }
    }

    public ProductListingTaskView executeSubmittedRealRunTask(Long realRunTaskId) {
        if (realRunTaskId == null) {
            throw new IllegalArgumentException("Product listing real-run task ID is required.");
        }
        ProductListingTaskRecord task = mapper.selectTaskByIdForWorker(realRunTaskId);
        if (task == null) {
            throw new IllegalArgumentException("Product listing real-run task not found.");
        }
        if (!REAL_RUN_MODE.equals(task.getMode())) {
            throw new IllegalArgumentException("Only product listing real-run tasks can be executed.");
        }
        if (!"submitted".equals(task.getStatus())) {
            return taskView(task, readIssues(task.getValidationJson()));
        }
        LocalDateTime startedAt = LocalDateTime.now();
        int claimed = mapper.markTaskRunning(task.getId(), startedAt);
        task = mapper.selectTaskByIdForWorker(realRunTaskId);
        if (task == null) {
            throw new IllegalArgumentException("Product listing real-run task not found.");
        }
        if (claimed == 0) {
            return taskView(task, readIssues(task.getValidationJson()));
        }
        task.setStatus("running");
        if (task.getStartedAt() == null) {
            task.setStartedAt(startedAt);
        }
        ProductListingNoonWriteResult result = executeNoonWrite(task);
        applyNoonWriteResult(task, result);
        mapper.updateTaskResult(task);
        return taskView(task, readIssues(task.getValidationJson()));
    }

    public int recoverStaleRunningRealRunTasks(Duration maxRunningAge) {
        Duration safeMaxRunningAge = maxRunningAge == null || maxRunningAge.isNegative() || maxRunningAge.isZero()
                ? Duration.ofMinutes(30)
                : maxRunningAge;
        LocalDateTime staleBefore = LocalDateTime.now().minus(safeMaxRunningAge);
        return mapper.recoverStaleRunningRealRunTasks(staleBefore);
    }

    private List<String> acquireIdentityLocks(
            Long ownerUserId,
            String storeCode,
            String partnerSku,
            String barcode
    ) {
        List<String> lockKeys = new ArrayList<>();
        if (StringUtils.hasText(partnerSku)) {
            lockKeys.add(identityLockKey("psku", ownerUserId, storeCode, partnerSku));
        }
        if (StringUtils.hasText(barcode)) {
            lockKeys.add(identityLockKey("barcode", ownerUserId, storeCode, barcode));
        }
        Collections.sort(lockKeys);
        List<String> acquired = new ArrayList<>();
        try {
            for (String lockKey : lockKeys) {
                Integer result = mapper.acquireIdentityLock(lockKey, IDENTITY_LOCK_TIMEOUT_SECONDS);
                if (!Integer.valueOf(1).equals(result)) {
                    throw new IllegalArgumentException("商品身份正在被其他上架任务校验，请稍后重试。");
                }
                acquired.add(lockKey);
            }
            return acquired;
        } catch (RuntimeException exception) {
            releaseIdentityLocks(acquired);
            throw exception;
        }
    }

    private void releaseIdentityLocks(List<String> lockKeys) {
        if (lockKeys == null || lockKeys.isEmpty()) {
            return;
        }
        for (int index = lockKeys.size() - 1; index >= 0; index--) {
            try {
                mapper.releaseIdentityLock(lockKeys.get(index));
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to release product listing identity lock", exception);
            }
        }
    }

    private boolean registerIdentityLockReleaseAfterCommit(List<String> lockKeys) {
        if (lockKeys == null || lockKeys.isEmpty()
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        AtomicBoolean released = new AtomicBoolean(false);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (released.compareAndSet(false, true)) {
                    releaseIdentityLocks(lockKeys);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (released.compareAndSet(false, true)) {
                    releaseIdentityLocks(lockKeys);
                }
            }
        });
        return true;
    }

    private String identityLockKey(String type, Long ownerUserId, String storeCode, String value) {
        return type + ":" + ownerUserId + ":" + normalizeText(storeCode).toUpperCase(Locale.ROOT)
                + ":" + normalizeText(value).toUpperCase(Locale.ROOT);
    }

    public List<ProductListingTaskView> executeRunnableRealRunTasks(int limit) {
        int safeLimit = Math.max(1, limit);
        List<ProductListingTaskRecord> tasks = mapper.selectRunnableRealRunTasks(safeLimit);
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<ProductListingTaskView> executed = new ArrayList<>();
        for (ProductListingTaskRecord task : tasks) {
            if (task == null || task.getId() == null) {
                continue;
            }
            executed.add(executeSubmittedRealRunTask(task.getId()));
        }
        return executed;
    }

    public ProductListingTaskView verifyRealRunReadBack(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        requireContext(context);
        Long ownerUserId = requireOwnerUserId(context);
        ProductListingTaskRecord task = requireTask(realRunTaskId, ownerUserId);
        requireStoreAccess(context, task.getStoreCode());
        if (!REAL_RUN_MODE.equals(task.getMode())
                || !REAL_RUN_STATUS_WRITTEN_VERIFY_FAILED.equals(task.getStatus())) {
            throw new IllegalArgumentException("Only written-verify-failed product listing real-run tasks can be verified.");
        }
        ProductListingNoonWriteResult previousResult = readNoonResult(task.getNoonResultJson());
        NoonWriteReferences references = requireNoonWriteReferences(previousResult);
        ProductListingNoonWriteStepResult readBack = noonWriteAdapter.verifyReadBack(
                noonWriteRequest(context, task),
                references.skuParent,
                references.pskuCode,
                references.uploadedImagePaths
        );
        ProductListingNoonWriteResult result = resultWithReadBack(previousResult, readBack);
        applyNoonWriteResult(task, result);
        mapper.updateTaskResult(task);
        return taskView(task, readIssues(task.getValidationJson()));
    }

    public ProductListingTaskView continueRealRunAfterCreate(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        requireContext(context);
        Long ownerUserId = requireOwnerUserId(context);
        ProductListingTaskRecord task = requireTask(realRunTaskId, ownerUserId);
        requireStoreAccess(context, task.getStoreCode());
        if (!REAL_RUN_MODE.equals(task.getMode())
                || (!REAL_RUN_STATUS_WRITTEN_VERIFY_FAILED.equals(task.getStatus()) && !"failed".equals(task.getStatus()))) {
            throw new IllegalArgumentException("Only failed product listing real-run tasks can be continued after Noon create.");
        }
        if (!realWriteProperties.isEnabled()) {
            throw new IllegalArgumentException("Real Noon listing writes are disabled by kill switch.");
        }
        ProductListingNoonWriteResult previousResult = readNoonResult(task.getNoonResultJson());
        NoonWriteReferences references = noonWriteReferences(previousResult);
        if (!StringUtils.hasText(references.skuParent) || !StringUtils.hasText(references.pskuCode)) {
            ProductListingNoonWriteStepResult lookup = noonWriteAdapter.resolveCreateReference(
                    noonWriteRequest(context, task)
            );
            if (lookup == null || !"succeeded".equals(lookup.getStatus())) {
                throw new IllegalArgumentException(lookup != null && StringUtils.hasText(lookup.getFailureMessage())
                        ? lookup.getFailureMessage()
                        : "Noon 中暂未找到该 PSKU 的创建结果，请稍后重新核对，不要重复上架。");
            }
            previousResult = resultWithCreateReference(previousResult, lookup);
            references = requireNoonWriteReferences(previousResult);
        }
        if (!StringUtils.hasText(references.pskuCode)) {
            throw new IllegalArgumentException("Product listing real-run task is missing Noon pskuCode reference.");
        }
        ProductListingNoonWriteResult continuationResult = noonWriteAdapter.continueAfterCreate(
                noonWriteRequest(context, task),
                references.skuParent,
                references.pskuCode
        );
        ProductListingNoonWriteResult result = resultWithContinuation(previousResult, continuationResult);
        applyNoonWriteResult(task, result);
        mapper.updateTaskResult(task);
        return taskView(task, readIssues(task.getValidationJson()));
    }

    public ProductListingTaskView replaySuccessfulProjectionBackfill(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        requireContext(context);
        Long ownerUserId = requireOwnerUserId(context);
        ProductListingTaskRecord task = requireTask(realRunTaskId, ownerUserId);
        requireStoreAccess(context, task.getStoreCode());
        boolean projectionRecovery = REAL_RUN_STATUS_WRITTEN_VERIFY_FAILED.equals(task.getStatus())
                && "projection_backfill_failed".equals(task.getFailureCode());
        if (!REAL_RUN_MODE.equals(task.getMode())
                || (!("succeeded".equals(task.getStatus()) || projectionRecovery))) {
            throw new IllegalArgumentException("Only Noon-succeeded product listing tasks can replay projection backfill.");
        }
        ProductListingNoonWriteResult result = readNoonResult(task.getNoonResultJson());
        if (result == null || !result.isSuccess()) {
            throw new IllegalArgumentException("Product listing real-run task does not contain a successful Noon write result.");
        }
        if (!backfillProductProjection(task, result)) {
            throw new IllegalStateException("Product listing projection backfill failed.");
        }
        if (projectionRecovery) {
            task.setStatus("succeeded");
            task.setFailureCategory(null);
            task.setFailureCode(null);
            task.setFailureMessage(null);
            task.setCompletedAt(LocalDateTime.now());
            mapper.updateTaskResult(task);
        }
        return taskView(task, readIssues(task.getValidationJson()));
    }

    public List<ProductListingTaskView> recentTasks(
            BusinessAccessContext context,
            String storeCode,
            Long draftId,
            int limit
    ) {
        requireContext(context);
        String safeStoreCode = requireStoreCode(storeCode);
        requireStoreAccess(context, safeStoreCode);
        Long ownerUserId = resolveOwnerUserId(context, safeStoreCode);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<ProductListingTaskRecord> tasks = draftId == null
                ? mapper.selectRecentTasks(ownerUserId, safeStoreCode, safeLimit)
                : mapper.selectRecentTasksByDraftId(ownerUserId, safeStoreCode, draftId, safeLimit);
        return tasks.stream()
                .map(task -> taskView(task, readIssues(task.getValidationJson())))
                .collect(Collectors.toList());
    }

    private ProductListingDraftRecord requireDraft(Long draftId, Long ownerUserId) {
        if (draftId == null) {
            throw new IllegalArgumentException("Product listing draft ID is required.");
        }
        ProductListingDraftRecord draft = mapper.selectDraftById(draftId, ownerUserId);
        if (draft == null) {
            throw new IllegalArgumentException("Product listing draft not found.");
        }
        return draft;
    }

    private ProductListingTaskRecord requireTask(Long taskId, Long ownerUserId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Product listing task ID is required.");
        }
        ProductListingTaskRecord task = mapper.selectTaskById(taskId, ownerUserId);
        if (task == null) {
            throw new IllegalArgumentException("Product listing task not found.");
        }
        return task;
    }

    private ProductListingTaskView insertRejectedRealRunTask(
            BusinessAccessContext context,
            ProductListingTaskRecord dryRunTask,
            ProductListingRealRunCommand command,
            String failureCategory,
            String failureCode,
            String failureMessage
    ) {
        ProductListingTaskRecord task = newRealRunTask(
                context,
                dryRunTask,
                command,
                "rejected",
                failureCategory,
                failureCode,
                failureMessage
        );
        task.setCompletedAt(task.getSubmittedAt());
        mapper.insertTask(task);
        return taskView(task, readIssues(task.getValidationJson()));
    }

    private ProductListingTaskView rejectExistingRealWriteAttempt(
            BusinessAccessContext context,
            ProductListingTaskRecord dryRunTask,
            ProductListingRealRunCommand command,
            ProductListingTaskRecord existingAttempt
    ) {
        if (PARTNER_SKU_ALREADY_EXISTS_CODE.equals(existingAttempt.getFailureCode())) {
            String message = StringUtils.hasText(existingAttempt.getFailureMessage())
                    ? existingAttempt.getFailureMessage()
                    : partnerSkuAlreadyExistsMessage(readPartnerSku(dryRunTask.getInputSnapshotJson()));
            return insertRejectedRealRunTask(
                    context,
                    dryRunTask,
                    command,
                    "validation",
                    PARTNER_SKU_ALREADY_EXISTS_CODE,
                    message
            );
        }
        boolean active = isActiveRealRunStatus(existingAttempt.getStatus());
        return insertRejectedRealRunTask(
                context,
                dryRunTask,
                command,
                "guard",
                active ? "real_run_already_active" : "real_run_already_attempted",
                active
                        ? "A real Noon listing task is already active for this dry-run."
                        : "A real Noon listing write has already been attempted for this dry-run."
        );
    }

    private ProductListingTaskRecord newRealRunTask(
            BusinessAccessContext context,
            ProductListingTaskRecord dryRunTask,
            ProductListingRealRunCommand command,
            String status,
            String failureCategory,
            String failureCode,
            String failureMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        Long taskId = mapper.nextProductListingTaskId();
        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setId(taskId);
        task.setDraftId(dryRunTask.getDraftId());
        task.setOwnerUserId(dryRunTask.getOwnerUserId());
        task.setStoreCode(dryRunTask.getStoreCode());
        task.setTaskNo(taskNo(taskId));
        task.setMode(REAL_RUN_MODE);
        task.setStatus(status);
        task.setSourceTaskId(dryRunTask.getId());
        task.setInputSnapshotJson(dryRunTask.getInputSnapshotJson());
        task.setValidationJson(dryRunTask.getValidationJson());
        task.setConfirmationJson(writeJson(command == null ? new ProductListingRealRunCommand() : command));
        task.setFailureCategory(failureCategory);
        task.setFailureCode(failureCode);
        task.setFailureMessage(failureMessage);
        task.setSubmittedBy(requireOperatorUserId(context));
        task.setSubmittedAt(now);
        return task;
    }

    private ProductListingNoonWriteResult executeNoonWrite(ProductListingTaskRecord realRunTask) {
        try {
            ProductListingNoonWriteResult result = noonWriteAdapter.execute(noonWriteRequest(realRunTask));
            if (result == null) {
                return ProductListingNoonWriteResult.failed(
                        "configuration",
                        "noon_write_adapter_empty_result",
                        "Product listing Noon write adapter returned an empty result.",
                        List.of()
                );
            }
            return result;
        } catch (RuntimeException exception) {
            return ProductListingNoonWriteResult.failed(
                    "noon_api",
                    "noon_write_exception",
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Product listing Noon write failed.",
                    List.of()
            );
        }
    }

    private ProductListingNoonWriteRequest noonWriteRequest(
            BusinessAccessContext context,
            ProductListingTaskRecord realRunTask
    ) {
        ProductListingNoonWriteRequest request = new ProductListingNoonWriteRequest();
        request.setOwnerUserId(realRunTask.getOwnerUserId());
        request.setStoreCode(realRunTask.getStoreCode());
        request.setDraftId(realRunTask.getDraftId());
        request.setDryRunTaskId(realRunTask.getSourceTaskId());
        request.setRealRunTaskId(realRunTask.getId());
        request.setSubmittedBy(requireOperatorUserId(context));
        request.setDraft(readDraft(realRunTask.getInputSnapshotJson()));
        request.setValidationIssues(readIssues(realRunTask.getValidationJson()));
        request.setConfirmation(readConfirmation(realRunTask.getConfirmationJson()));
        return request;
    }

    private ProductListingNoonWriteRequest noonWriteRequest(ProductListingTaskRecord realRunTask) {
        ProductListingNoonWriteRequest request = new ProductListingNoonWriteRequest();
        request.setOwnerUserId(realRunTask.getOwnerUserId());
        request.setStoreCode(realRunTask.getStoreCode());
        request.setDraftId(realRunTask.getDraftId());
        request.setDryRunTaskId(realRunTask.getSourceTaskId());
        request.setRealRunTaskId(realRunTask.getId());
        request.setSubmittedBy(realRunTask.getSubmittedBy());
        request.setDraft(readDraft(realRunTask.getInputSnapshotJson()));
        request.setValidationIssues(readIssues(realRunTask.getValidationJson()));
        request.setConfirmation(readConfirmation(realRunTask.getConfirmationJson()));
        return request;
    }

    private ProductListingNoonWriteResult resultWithReadBack(
            ProductListingNoonWriteResult previousResult,
            ProductListingNoonWriteStepResult readBack
    ) {
        ProductListingNoonWriteStepResult safeReadBack = readBack == null
                ? failedReadBackStep("noon_listing_readback_failed", "Noon listing read-back failed.")
                : readBack;
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        if (previousResult != null && previousResult.getSteps() != null) {
            steps.addAll(previousResult.getSteps());
        }
        steps.add(safeReadBack);
        if ("succeeded".equals(safeReadBack.getStatus())) {
            if (hasFailedWriteStep(previousResult)) {
                return ProductListingNoonWriteResult.failed(
                        StringUtils.hasText(previousResult.getFailureCategory())
                                ? previousResult.getFailureCategory()
                                : "noon_api",
                        StringUtils.hasText(previousResult.getFailureCode())
                                ? previousResult.getFailureCode()
                                : "noon_partial_write_failed",
                        StringUtils.hasText(previousResult.getFailureMessage())
                                ? previousResult.getFailureMessage()
                                : "Noon listing read-back passed, but an earlier write step failed.",
                        steps
                );
            }
            return ProductListingNoonWriteResult.succeeded(steps);
        }
        return ProductListingNoonWriteResult.failed(
                "noon_readback",
                StringUtils.hasText(safeReadBack.getFailureCode())
                        ? safeReadBack.getFailureCode()
                        : "noon_listing_readback_failed",
                StringUtils.hasText(safeReadBack.getFailureMessage())
                        ? safeReadBack.getFailureMessage()
                        : "Noon listing read-back failed.",
                steps
        );
    }

    private ProductListingNoonWriteResult resultWithContinuation(
            ProductListingNoonWriteResult previousResult,
            ProductListingNoonWriteResult continuationResult
    ) {
        ProductListingNoonWriteResult safeContinuation = continuationResult == null
                ? ProductListingNoonWriteResult.failed(
                "noon_api",
                "noon_write_continuation_failed",
                "Product listing Noon write continuation failed.",
                List.of()
        )
                : continuationResult;
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        if (previousResult != null && previousResult.getSteps() != null) {
            steps.addAll(previousResult.getSteps());
        }
        if (safeContinuation.getSteps() != null) {
            steps.addAll(safeContinuation.getSteps());
        }
        if (safeContinuation.isSuccess()) {
            return ProductListingNoonWriteResult.succeeded(steps);
        }
        return ProductListingNoonWriteResult.failed(
                StringUtils.hasText(safeContinuation.getFailureCategory())
                        ? safeContinuation.getFailureCategory()
                        : "noon_api",
                StringUtils.hasText(safeContinuation.getFailureCode())
                        ? safeContinuation.getFailureCode()
                        : "noon_write_continuation_failed",
                StringUtils.hasText(safeContinuation.getFailureMessage())
                        ? safeContinuation.getFailureMessage()
                        : "Product listing Noon write continuation failed.",
                steps
        );
    }

    private ProductListingNoonWriteResult resultWithCreateReference(
            ProductListingNoonWriteResult previousResult,
            ProductListingNoonWriteStepResult lookup
    ) {
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        if (previousResult != null && previousResult.getSteps() != null) {
            steps.addAll(previousResult.getSteps());
        }
        steps.add(lookup);
        return ProductListingNoonWriteResult.failed(
                previousResult != null && StringUtils.hasText(previousResult.getFailureCategory())
                        ? previousResult.getFailureCategory()
                        : "noon_uncertain_write",
                previousResult != null && StringUtils.hasText(previousResult.getFailureCode())
                        ? previousResult.getFailureCode()
                        : "real_run_interrupted",
                previousResult != null && StringUtils.hasText(previousResult.getFailureMessage())
                        ? previousResult.getFailureMessage()
                        : "Noon create reference recovered after interrupted real-run.",
                steps
        );
    }

    private ProductListingNoonWriteStepResult failedReadBackStep(String failureCode, String failureMessage) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("verify_noon_readback");
        step.setStatus("failed");
        step.setFailureCode(failureCode);
        step.setFailureMessage(failureMessage);
        return step;
    }

    private void applyNoonWriteResult(
            ProductListingTaskRecord task,
            ProductListingNoonWriteResult result
    ) {
        result = normalizeNoonWriteFailure(task, result);
        task.setNoonResultJson(writeJson(result));
        task.setCompletedAt(LocalDateTime.now());
        if (result.isSuccess()) {
            if (!backfillProductProjection(task, result)) {
                task.setStatus(REAL_RUN_STATUS_WRITTEN_VERIFY_FAILED);
                task.setFailureCategory("local_projection");
                task.setFailureCode("projection_backfill_failed");
                task.setFailureMessage("Noon 已完成上架，但本地商品列表同步失败；请重试本地投影恢复，不要重复上架。");
                return;
            }
            task.setStatus("succeeded");
            task.setFailureCategory(null);
            task.setFailureCode(null);
            task.setFailureMessage(null);
            return;
        }
        if (isWrittenButVerificationFailed(result)) {
            task.setStatus(REAL_RUN_STATUS_WRITTEN_VERIFY_FAILED);
            task.setFailureCategory(StringUtils.hasText(result.getFailureCategory())
                    ? result.getFailureCategory()
                    : "noon_readback");
            task.setFailureCode(StringUtils.hasText(result.getFailureCode())
                    ? result.getFailureCode()
                    : "noon_readback_verification_failed");
            task.setFailureMessage(StringUtils.hasText(result.getFailureMessage())
                    ? result.getFailureMessage()
                    : "Noon product was written, but readback verification failed.");
            return;
        }
        if (isCreateOutcomeUnknown(result)) {
            task.setStatus(REAL_RUN_STATUS_WRITTEN_VERIFY_FAILED);
            task.setFailureCategory("noon_uncertain_write");
            task.setFailureCode("noon_create_outcome_unknown");
            task.setFailureMessage("Noon 创建请求结果未知；请核对创建结果并继续后续步骤，不要重复提交上架。");
            return;
        }
        task.setStatus("failed");
        task.setFailureCategory(StringUtils.hasText(result.getFailureCategory())
                ? result.getFailureCategory()
                : "noon_api");
        task.setFailureCode(StringUtils.hasText(result.getFailureCode())
                ? result.getFailureCode()
                : "noon_write_failed");
        task.setFailureMessage(StringUtils.hasText(result.getFailureMessage())
                ? result.getFailureMessage()
                : "Product listing Noon write failed.");
    }

    private boolean backfillProductProjection(
            ProductListingTaskRecord task,
            ProductListingNoonWriteResult result
    ) {
        try {
            return projectionBackfill.backfillSuccessfulListing(
                    task,
                    readDraft(task.getInputSnapshotJson()),
                    result
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Product listing projection backfill failed: taskId={}, taskNo={}",
                    task == null ? null : task.getId(),
                    task == null ? null : task.getTaskNo(),
                    exception
            );
            return false;
        }
    }

    private void backfillDraftProjection(
            ProductListingDraftRecord record,
            ProductListingDraftCommand draft
    ) {
        try {
            projectionBackfill.backfillDraftListing(record, draft);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Product listing draft projection backfill failed: draftId={}, draftNo={}",
                    record == null ? null : record.getId(),
                    record == null ? null : record.getDraftNo(),
                    exception
            );
        }
    }

    private boolean isWrittenButVerificationFailed(ProductListingNoonWriteResult result) {
        if (result == null || result.isSuccess()) {
            return false;
        }
        boolean created = result.getSteps().stream().anyMatch((step) ->
                "create_product".equals(step.getStepKey())
                        && "succeeded".equals(step.getStatus())
                        && StringUtils.hasText(step.getExternalReference()));
        return created;
    }

    private boolean isCreateOutcomeUnknown(ProductListingNoonWriteResult result) {
        if (result == null || result.getSteps() == null) {
            return false;
        }
        return result.getSteps().stream().anyMatch(step -> step != null
                && "create_product".equals(step.getStepKey())
                && "failed".equals(step.getStatus())
                && "noon_create_outcome_unknown".equals(step.getFailureCode()));
    }

    private boolean hasFailedWriteStep(ProductListingNoonWriteResult result) {
        if (result == null || result.getSteps() == null) {
            return false;
        }
        return result.getSteps().stream().anyMatch((step) ->
                step != null
                        && "failed".equals(step.getStatus())
                        && !"verify_noon_readback".equals(step.getStepKey()));
    }

    private ProductListingDraftView draftView(
            ProductListingDraftRecord record,
            ProductListingDraftCommand command,
            List<ProductListingValidationIssue> issues
    ) {
        ProductListingDraftView view = new ProductListingDraftView();
        view.setDraftId(record.getId());
        view.setDraftNo(record.getDraftNo());
        view.setOwnerUserId(record.getOwnerUserId());
        view.setStoreCode(record.getStoreCode());
        view.setStatus(record.getStatus());
        view.setDraft(command);
        view.setValidationIssues(issues);
        return view;
    }

    private ProductListingTaskView taskView(
            ProductListingTaskRecord record,
            List<ProductListingValidationIssue> issues
    ) {
        ProductListingTaskView view = new ProductListingTaskView();
        view.setTaskId(record.getId());
        view.setTaskNo(record.getTaskNo());
        view.setDraftId(record.getDraftId());
        view.setOwnerUserId(record.getOwnerUserId());
        view.setStoreCode(record.getStoreCode());
        view.setPartnerSku(readPartnerSku(record.getInputSnapshotJson()));
        view.setMode(record.getMode());
        view.setStatus(record.getStatus());
        view.setSourceTaskId(record.getSourceTaskId());
        view.setValidationIssues(issues);
        view.setFailureCategory(record.getFailureCategory());
        view.setFailureCode(record.getFailureCode());
        view.setFailureMessage(record.getFailureMessage());
        view.setNoonResult(readNoonResult(record.getNoonResultJson()));
        view.setSubmittedAt(record.getSubmittedAt());
        view.setStartedAt(record.getStartedAt());
        view.setCompletedAt(record.getCompletedAt());
        return view;
    }

    private ProductListingDraftCommand requireCommand(ProductListingDraftCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Product listing draft payload is required.");
        }
        return command;
    }

    private void requireContext(BusinessAccessContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Business access context is required.");
        }
    }

    private String requireStoreCode(String storeCode) {
        if (!StringUtils.hasText(storeCode)) {
            throw new IllegalArgumentException("Store code is required.");
        }
        return storeCode.trim();
    }

    private String resolveSourceType(ProductListingDraftCommand command, ProductListingDraftRecord existing) {
        if (StringUtils.hasText(command.getSourceType())) {
            return command.getSourceType().trim();
        }
        return existing == null ? null : existing.getSourceType();
    }

    private Long resolveSourceRefId(ProductListingDraftCommand command, ProductListingDraftRecord existing) {
        if (command.getSourceRefId() != null) {
            return command.getSourceRefId();
        }
        return existing == null ? null : existing.getSourceRefId();
    }

    private void preserveExistingStableDraftFields(
            ProductListingDraftCommand command,
            ProductListingDraftRecord existing
    ) {
        if (command == null || existing == null || !StringUtils.hasText(existing.getDraftJson())) {
            return;
        }
        ProductListingDraftCommand previous = readDraft(existing.getDraftJson());
        boolean incomingHasProductFullType = StringUtils.hasText(command.getProductFullType());
        if (incomingHasProductFullType) {
            command.setIdProductFullType(null);
            if (looksLikeProductFullTypeCode(command.getProductFullType())) {
                command.setFamily(null);
                command.setProductType(null);
                command.setProductSubType(null);
            }
        } else {
            command.setProductFullType(previous.getProductFullType());
            boolean sameProductFullType = sameStableText(command.getProductFullType(), previous.getProductFullType());
            if (sameProductFullType && command.getIdProductFullType() == null) {
                command.setIdProductFullType(previous.getIdProductFullType());
            }
            if (sameProductFullType && !StringUtils.hasText(command.getFamily())) {
                command.setFamily(previous.getFamily());
            }
            if (sameProductFullType && !StringUtils.hasText(command.getProductType())) {
                command.setProductType(previous.getProductType());
            }
            if (sameProductFullType && !StringUtils.hasText(command.getProductSubType())) {
                command.setProductSubType(previous.getProductSubType());
            }
        }
        if (!StringUtils.hasText(command.getProductBrand())) {
            command.setProductBrand(previous.getProductBrand());
        }
        if (!StringUtils.hasText(command.getProductBrandCode())) {
            command.setProductBrandCode(previous.getProductBrandCode());
        }
    }

    private boolean sameStableText(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean looksLikeProductFullTypeCode(String value) {
        return StringUtils.hasText(value) && value.contains("-") && value.contains("_");
    }

    private void requireStoreAccess(BusinessAccessContext context, String storeCode) {
        if (!context.canAccessStore(storeCode)) {
            throw new BusinessAccessDeniedException("当前账号不能操作该店铺。");
        }
    }

    private Long resolveOwnerUserId(BusinessAccessContext context, String storeCode) {
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        if (ownerUserId == null) {
            throw new IllegalArgumentException("Business owner user ID is required.");
        }
        return ownerUserId;
    }

    private Long requireOwnerUserId(BusinessAccessContext context) {
        Long ownerUserId = context.getBusinessOwnerUserId();
        if (ownerUserId == null) {
            throw new IllegalArgumentException("Business owner user ID is required.");
        }
        return ownerUserId;
    }

    private Long requireOperatorUserId(BusinessAccessContext context) {
        Long operatorUserId = context.getSessionUserId();
        if (operatorUserId == null) {
            throw new IllegalArgumentException("Session user ID is required.");
        }
        return operatorUserId;
    }

    private boolean hasHardIssues(List<ProductListingValidationIssue> issues) {
        return issues != null && issues.stream()
                .filter(Objects::nonNull)
                .anyMatch(issue -> !"warning".equalsIgnoreCase(issue.getSeverity()));
    }

    private List<ProductListingValidationIssue> validateWithRuntimeWarnings(
            ProductListingDraftCommand command,
            Long ownerUserId,
            String storeCode
    ) {
        ProductListingDraftCommand safeCommand = command == null ? new ProductListingDraftCommand() : command;
        List<ProductListingValidationIssue> issues = new ArrayList<>(validator.validateWithWarnings(safeCommand));
        issues.addAll(validateDuplicateIdentityFields(ownerUserId, storeCode, safeCommand));
        addRealWriteCapabilityWarnings(issues, safeCommand);
        return issues;
    }

    private List<ProductListingValidationIssue> validateDuplicateIdentityFields(
            Long ownerUserId,
            String storeCode,
            ProductListingDraftCommand command
    ) {
        List<ProductListingValidationIssue> issues = new ArrayList<>();
        addPartnerSkuAlreadyExistsIssue(issues, ownerUserId, storeCode, command);
        addBarcodeAlreadyExistsIssue(issues, ownerUserId, storeCode, command);
        return issues;
    }

    private void addPartnerSkuAlreadyExistsIssue(
            List<ProductListingValidationIssue> issues,
            Long ownerUserId,
            String storeCode,
            ProductListingDraftCommand command
    ) {
        String partnerSku = normalizeText(command == null ? null : command.getPsku());
        if (!StringUtils.hasText(partnerSku) || ownerUserId == null || !StringUtils.hasText(storeCode)) {
            return;
        }
        Long existingProductId = mapper.selectLocalProductIdByPartnerSku(
                ownerUserId,
                storeCode,
                partnerSku,
                command.getDraftId()
        );
        if (existingProductId != null) {
            if (isSameRebuildSourceProduct(existingProductId, command)) {
                return;
            }
            issues.add(error(
                    "psku",
                    PARTNER_SKU_ALREADY_EXISTS_CODE,
                    localPartnerSkuAlreadyExistsMessage(partnerSku)
            ));
            return;
        }
        if (isProductRebuildDraft(command)) {
            return;
        }
        ProductListingTaskRecord existingTask = mapper.selectListedPartnerSkuTask(ownerUserId, storeCode, partnerSku);
        if (existingTask == null) {
            return;
        }
        issues.add(error(
                "psku",
                PARTNER_SKU_ALREADY_EXISTS_CODE,
                partnerSkuAlreadyExistsMessage(partnerSku)
        ));
    }

    private void addBarcodeAlreadyExistsIssue(
            List<ProductListingValidationIssue> issues,
            Long ownerUserId,
            String storeCode,
            ProductListingDraftCommand command
    ) {
        String barcode = normalizeText(ProductListingDraftProjectionFields.from(
                command,
                command == null ? null : command.getPsku()
        ).barcode());
        if (!StringUtils.hasText(barcode) || ownerUserId == null || !StringUtils.hasText(storeCode)) {
            return;
        }
        Long existingProductId = mapper.selectLocalProductIdByBarcode(
                ownerUserId,
                storeCode,
                barcode,
                command == null ? null : command.getDraftId()
        );
        if (existingProductId != null) {
            if (isSameRebuildSourceProduct(existingProductId, command)) {
                return;
            }
            issues.add(error(
                    "barcode",
                    "barcode_already_exists",
                    barcodeAlreadyExistsMessage(barcode)
            ));
            return;
        }
        if (isProductRebuildDraft(command)) {
            return;
        }
        ProductListingTaskRecord existingTask = mapper.selectReservedBarcodeTask(ownerUserId, storeCode, barcode);
        if (existingTask == null) {
            return;
        }
        issues.add(error(
                "barcode",
                "barcode_already_exists",
                barcodeAlreadyExistsMessage(barcode)
        ));
    }

    private void addRealWriteCapabilityWarnings(
            List<ProductListingValidationIssue> issues,
            ProductListingDraftCommand command
    ) {
        if (hasOfferPriceFields(command) && !realWriteProperties.isOfferUpsertEnabled()) {
            issues.add(warning(
                    "offerPrice",
                    "offer_price_not_written",
                    "Offer price range and sale-window fields are saved in the draft but are not written to Noon unless product-listing offer upsert is enabled."
            ));
        }
        if (hasOfferSplitFields(command)
                && !(realWriteProperties.isOfferUpsertEnabled() && realWriteProperties.isOfferSplitWriteEnabled())) {
            issues.add(warning(
                    "offerSplit",
                    "offer_note_active_not_written",
                    "Offer note and active-state fields are saved in the draft but are not written to Noon unless the split offer writer is enabled."
            ));
        }
        if (hasWarehouseStockFields(command)) {
            issues.add(warning(
                    "warehouseStock",
                    "warehouse_stock_not_written",
                    "Warehouse, stock quantity, and FBP fields are saved in the draft but are not written to Noon by the current product listing real-run."
            ));
        }
    }

    private ProductListingValidationIssue warning(String fieldKey, String code, String message) {
        return new ProductListingValidationIssue(fieldKey, "warning", code, message);
    }

    private ProductListingValidationIssue error(String fieldKey, String code, String message) {
        return new ProductListingValidationIssue(fieldKey, "error", code, message);
    }

    private boolean hasOfferPriceFields(ProductListingDraftCommand command) {
        return command != null
                && (command.getPriceMin() != null
                || command.getPriceMax() != null
                || command.getSalePrice() != null
                || StringUtils.hasText(command.getSaleStart())
                || StringUtils.hasText(command.getSaleEnd()));
    }

    private boolean hasOfferSplitFields(ProductListingDraftCommand command) {
        return command != null
                && (command.getIsActive() != null
                || command.getOfferNote() != null);
    }

    private boolean hasWarehouseStockFields(ProductListingDraftCommand command) {
        return command != null
                && (command.getFbp() != null
                || StringUtils.hasText(command.getWarehouseId())
                || StringUtils.hasText(command.getWarehouseCode())
                || command.getQuantity() != null);
    }

    private boolean isActiveRealRunStatus(String status) {
        return "running".equals(status) || "submitted".equals(status);
    }

    private ProductListingNoonWriteResult normalizeNoonWriteFailure(
            ProductListingTaskRecord task,
            ProductListingNoonWriteResult result
    ) {
        if (result == null || result.isSuccess() || !isPartnerSkuAlreadyExistsFailure(result)) {
            return result;
        }
        String partnerSku = extractPartnerSkuAlreadyExists(result);
        if (!StringUtils.hasText(partnerSku) && task != null) {
            partnerSku = readPartnerSku(task.getInputSnapshotJson());
        }
        String message = partnerSkuAlreadyExistsMessage(partnerSku);
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            if (step == null || !"failed".equals(step.getStatus())) {
                continue;
            }
            if (containsPartnerSkuAlreadyExists(step.getFailureMessage())) {
                step.setFailureCode(PARTNER_SKU_ALREADY_EXISTS_CODE);
                step.setFailureMessage(message);
            }
        }
        return ProductListingNoonWriteResult.failed(
                "validation",
                PARTNER_SKU_ALREADY_EXISTS_CODE,
                message,
                result.getSteps()
        );
    }

    private boolean isPartnerSkuAlreadyExistsFailure(ProductListingNoonWriteResult result) {
        if (result == null) {
            return false;
        }
        if (containsPartnerSkuAlreadyExists(result.getFailureCode())
                || containsPartnerSkuAlreadyExists(result.getFailureMessage())) {
            return true;
        }
        return result.getSteps().stream()
                .filter(Objects::nonNull)
                .anyMatch(step -> containsPartnerSkuAlreadyExists(step.getFailureCode())
                        || containsPartnerSkuAlreadyExists(step.getFailureMessage()));
    }

    private boolean containsPartnerSkuAlreadyExists(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("partner skus already exists")
                || normalized.contains("partner sku already exists")
                || normalized.contains(PARTNER_SKU_ALREADY_EXISTS_CODE);
    }

    private String extractPartnerSkuAlreadyExists(ProductListingNoonWriteResult result) {
        String fromResult = extractPartnerSkuAlreadyExists(result == null ? null : result.getFailureMessage());
        if (StringUtils.hasText(fromResult)) {
            return fromResult;
        }
        if (result == null || result.getSteps() == null) {
            return null;
        }
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            String fromStep = extractPartnerSkuAlreadyExists(step == null ? null : step.getFailureMessage());
            if (StringUtils.hasText(fromStep)) {
                return fromStep;
            }
        }
        return null;
    }

    private String extractPartnerSkuAlreadyExists(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = PARTNER_SKU_ALREADY_EXISTS_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return normalizeText(matcher.group(1));
    }

    private String partnerSkuAlreadyExistsMessage(String partnerSku) {
        String normalized = normalizeText(partnerSku);
        if (StringUtils.hasText(normalized)) {
            return "PSKU 已存在，不能重复创建：" + normalized + "。请更换新的 PSKU，或到商品详情中编辑已有商品。";
        }
        return "PSKU 已存在，不能重复创建。请更换新的 PSKU，或到商品详情中编辑已有商品。";
    }

    private String localPartnerSkuAlreadyExistsMessage(String partnerSku) {
        String normalized = normalizeText(partnerSku);
        if (StringUtils.hasText(normalized)) {
            return "当前本地店铺已存在相同 PSKU：" + normalized + "。请更换新的 PSKU，或到商品详情中编辑已有商品。";
        }
        return "当前本地店铺已存在相同 PSKU。请更换新的 PSKU，或到商品详情中编辑已有商品。";
    }

    private String barcodeAlreadyExistsMessage(String barcode) {
        String normalized = normalizeText(barcode);
        if (StringUtils.hasText(normalized)) {
            return "当前本地店铺已存在相同 Barcode：" + normalized + "。请更换新的 Barcode，或到商品详情中编辑已有商品。";
        }
        return "当前本地店铺已存在相同 Barcode。请更换新的 Barcode，或到商品详情中编辑已有商品。";
    }

    private boolean isSameRebuildSourceProduct(Long existingProductId, ProductListingDraftCommand command) {
        Long rebuildSourceProductMasterId = command == null ? null : command.getRebuildSourceProductMasterId();
        return existingProductId != null
                && rebuildSourceProductMasterId != null
                && existingProductId.equals(rebuildSourceProductMasterId);
    }

    private boolean isProductRebuildDraft(ProductListingDraftCommand command) {
        if (command == null) {
            return false;
        }
        if (command.getRebuildSourceProductMasterId() != null) {
            return true;
        }
        return "PRODUCT_REBUILD".equalsIgnoreCase(normalizeText(command.getSourceType()));
    }

    private boolean isHistoricalRebuildPartnerSkuTask(
            ProductListingTaskRecord dryRunTask,
            ProductListingDraftCommand dryRunDraft,
            ProductListingTaskRecord existingPartnerSkuTask
    ) {
        if (!isProductRebuildDraft(dryRunDraft)
                || dryRunTask == null
                || dryRunTask.getId() == null
                || existingPartnerSkuTask == null
                || existingPartnerSkuTask.getId() == null) {
            return false;
        }
        return existingPartnerSkuTask.getId() < dryRunTask.getId();
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String draftNo(Long draftId) {
        return "PLD-" + draftId;
    }

    private String taskNo(Long taskId) {
        return "PLT-" + taskId;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize product listing payload.", exception);
        }
    }

    private ProductListingDraftCommand readDraft(String json) {
        try {
            return objectMapper.readValue(json, ProductListingDraftCommand.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse product listing draft payload.", exception);
        }
    }

    private ProductListingRealRunCommand readConfirmation(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProductListingRealRunCommand.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse product listing real-run confirmation payload.", exception);
        }
    }

    private String readPartnerSku(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        ProductListingDraftCommand draft = readDraft(json);
        return StringUtils.hasText(draft.getPsku()) ? draft.getPsku().trim() : null;
    }

    private List<ProductListingValidationIssue> readIssues(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProductListingValidationIssue>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse product listing validation payload.", exception);
        }
    }

    private ProductListingNoonWriteResult readNoonResult(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProductListingNoonWriteResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse product listing Noon result payload.", exception);
        }
    }

    private NoonWriteReferences requireNoonWriteReferences(ProductListingNoonWriteResult result) {
        NoonWriteReferences references = noonWriteReferences(result);
        if (!StringUtils.hasText(references.skuParent)) {
            throw new IllegalArgumentException("Product listing real-run task is missing Noon skuParent reference.");
        }
        return references;
    }

    private NoonWriteReferences noonWriteReferences(ProductListingNoonWriteResult result) {
        NoonWriteReferences references = new NoonWriteReferences();
        if (result != null && result.getSteps() != null) {
            for (ProductListingNoonWriteStepResult step : result.getSteps()) {
                Map<String, String> parts = externalReferenceParts(step == null ? null : step.getExternalReference());
                references.accept(parts);
            }
        }
        return references;
    }

    private Map<String, String> externalReferenceParts(String value) {
        Map<String, String> parts = new LinkedHashMap<>();
        if (!StringUtils.hasText(value)) {
            return parts;
        }
        String[] tokens = value.split(";");
        for (String token : tokens) {
            int separator = token.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = token.substring(0, separator).trim();
            String referenceValue = token.substring(separator + 1).trim();
            if (StringUtils.hasText(key) && StringUtils.hasText(referenceValue)) {
                parts.put(key, referenceValue);
            }
        }
        return parts;
    }

    private static class NoonWriteReferences {
        private String skuParent;
        private String pskuCode;
        private List<String> uploadedImagePaths = List.of();

        private void accept(Map<String, String> parts) {
            if (parts == null || parts.isEmpty()) {
                return;
            }
            if (StringUtils.hasText(parts.get("skuParent"))) {
                skuParent = parts.get("skuParent");
            }
            if (StringUtils.hasText(parts.get("pskuCode"))) {
                pskuCode = parts.get("pskuCode");
            }
            if (StringUtils.hasText(parts.get("uploadedImagePaths"))) {
                uploadedImagePaths = List.of(parts.get("uploadedImagePaths").split(",")).stream()
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toList());
            }
        }
    }
}
