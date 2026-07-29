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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.util.StringUtils;

@Service
public class ProductListingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductListingService.class);
    private static final String DRY_RUN_MODE = "DRY_RUN";
    private static final String REAL_RUN_MODE = "REAL_RUN";
    private static final String REAL_RUN_STATUS_WRITTEN_VERIFY_FAILED = "written_verify_failed";
    private static final String PARTNER_SKU_ALREADY_EXISTS_CODE = "partner_sku_already_exists";
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
    private final ProductListingImageMetadataEnricher imageMetadataEnricher;
    private final ProductListingDryRunFreshness dryRunFreshness;
    private final ProductListingWorkflowGuard workflowGuard;
    private final ProductListingIdentityLockManager identityLockManager;
    private ProductListingOfficialTaxonomyGuard taxonomyGuard;
    @Autowired
    public ProductListingService(
            ProductListingMapper mapper,
            ObjectMapper objectMapper,
            ProductListingValidator validator,
            ProductListingRealWriteProperties realWriteProperties,
            ProductListingNoonWriteAdapter noonWriteAdapter,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<ProductListingProjectionBackfill> projectionBackfillProvider,
            ProductListingImageMetadataEnricher imageMetadataEnricher
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.realWriteProperties = realWriteProperties == null ? new ProductListingRealWriteProperties() : realWriteProperties;
        this.noonWriteAdapter = noonWriteAdapter == null ? new UnavailableProductListingNoonWriteAdapter() : noonWriteAdapter;
        this.eventPublisher = eventPublisher == null ? event -> {
        } : eventPublisher;
        this.projectionBackfill = projectionBackfillProvider == null
                ? ProductListingProjectionBackfill.noop()
                : projectionBackfillProvider.getIfAvailable(ProductListingProjectionBackfill::noop);
        this.imageMetadataEnricher = imageMetadataEnricher == null
                ? new ProductListingImageMetadataEnricher()
                : imageMetadataEnricher;
        this.dryRunFreshness = new ProductListingDryRunFreshness(objectMapper);
        this.workflowGuard = new ProductListingWorkflowGuard(mapper, objectMapper);
        this.identityLockManager = new ProductListingIdentityLockManager(mapper);
        this.taxonomyGuard = new ProductListingOfficialTaxonomyGuard(null, this.realWriteProperties);
    }
    @Autowired(required = false)
    void setOfficialTaxonomyMapper(com.nuono.next.infrastructure.mapper.ProductListingOfficialTaxonomyMapper mapper) {
        this.taxonomyGuard = new ProductListingOfficialTaxonomyGuard(mapper, realWriteProperties);
    }
    public ProductListingService(
            ProductListingMapper mapper,
            ObjectMapper objectMapper,
            ProductListingValidator validator,
            ProductListingRealWriteProperties realWriteProperties,
            ProductListingNoonWriteAdapter noonWriteAdapter,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<ProductListingProjectionBackfill> projectionBackfillProvider
    ) {
        this(
                mapper,
                objectMapper,
                validator,
                realWriteProperties,
                noonWriteAdapter,
                eventPublisher,
                projectionBackfillProvider,
                null
        );
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
    ProductListingService(
            ProductListingMapper mapper,
            ObjectMapper objectMapper,
            ProductListingValidator validator,
            ProductListingImageMetadataEnricher imageMetadataEnricher
    ) {
        this(
                mapper,
                objectMapper,
                validator,
                new ProductListingRealWriteProperties(),
                new UnavailableProductListingNoonWriteAdapter(),
                null,
                null,
                imageMetadataEnricher
        );
    }
    @Transactional
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
        List<String> sourceLocks = identityLockManager.acquireDraftSourceLock(
                ownerUserId,
                storeCode,
                safeCommand
        );
        boolean deferredSourceLockRelease =
                identityLockManager.deferReleaseUntilTransactionCompletion(
                        sourceLocks
                );
        try {
        ProductListingDraftRecord existing = null;
        Long draftId = safeCommand.getDraftId();
        if (draftId == null) {
            draftId = activeSourceDraftId(ownerUserId, storeCode, safeCommand);
            if (draftId != null) {
                existing = workflowGuard.lockDraftIfPresent(draftId, ownerUserId);
                if (existing == null) {
                    draftId = null;
                }
            }
            if (draftId == null) {
                draftId = mapper.nextProductListingDraftId();
            }
            safeCommand.setDraftId(draftId);
        } else {
            existing = workflowGuard.requireLockedDraft(draftId, ownerUserId);
            requireStoreAccess(context, existing.getStoreCode());
            if (!storeCode.equalsIgnoreCase(existing.getStoreCode())) {
                throw new IllegalArgumentException("Product listing draft store cannot be changed.");
            }
        }
        ProductListingDraftSourceIdentity.preserveExisting(
                safeCommand,
                existing
        );
        if (existing != null
                && mapper.selectCurrentRealRunTaskByDraftId(ownerUserId, draftId) != null) {
            throw new IllegalArgumentException(
                    "该草稿存在未决真实上架任务，任务结束或完成恢复前不能修改。"
            );
        }
        ProductListingTaskRecord latestDryRun = existing == null
                ? null
                : mapper.selectLatestDryRunTaskByDraftId(ownerUserId, draftId);
        if (latestDryRun != null
                && "validated".equalsIgnoreCase(latestDryRun.getStatus())
                && dryRunFreshness.matches(
                        existing.getDraftJson(), latestDryRun.getInputSnapshotJson())) {
            throw new IllegalArgumentException("请先返回修改，使当前上架检查失效后再保存草稿。");
        }
        preserveExistingStableDraftFields(safeCommand, existing);
        List<ProductListingValidationIssue> issues = validateWithRuntimeWarnings(safeCommand, ownerUserId, storeCode);
        boolean hasBlockingDraftIssues = validator.hasBlockingDraftIssues(issues);
        String status = hasBlockingDraftIssues ? "draft" : "ready_for_dry_run";
        ProductListingDraftRecord record = new ProductListingDraftRecord();
        record.setId(draftId);
        record.setOwnerUserId(ownerUserId);
        record.setStoreCode(storeCode);
        record.setDraftNo(existing == null ? draftNo(draftId) : existing.getDraftNo());
        String sourceType = ProductListingDraftSourceIdentity.resolveType(
                safeCommand,
                existing
        );
        Long sourceRefId =
                ProductListingDraftSourceIdentity.resolveReferenceId(
                        safeCommand,
                        existing
                );
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
        if (!hasBlockingDraftIssues) {
            backfillDraftProjection(record, safeCommand);
        }
        return draftView(record, safeCommand, issues);
        } finally {
            if (!deferredSourceLockRelease) {
                identityLockManager.release(sourceLocks);
            }
        }
    }

    private Long activeSourceDraftId(
            Long ownerUserId,
            String storeCode,
            ProductListingDraftCommand command
    ) {
        if (!StringUtils.hasText(command.getSourceType()) || command.getSourceRefId() == null){ return null; }
        return mapper.findActiveDraftId(
                ownerUserId,
                storeCode,
                command.getSourceType().trim(),
                command.getSourceRefId()
        );
    }
    public ProductListingDraftView validateDraft(BusinessAccessContext context, Long draftId) {
        requireContext(context);
        ProductListingDraftRecord record =
                workflowGuard.requireAccessibleDraft(
                        context, draftId, false);
        Long ownerUserId = record.getOwnerUserId();
        ProductListingDraftCommand command = readDraft(record.getDraftJson());
        List<ProductListingValidationIssue> issues = validateWithRuntimeWarnings(command, ownerUserId, record.getStoreCode());
        record.setStatus(validator.hasBlockingDraftIssues(issues) ? "draft" : "ready_for_dry_run");
        record.setValidationJson(writeJson(issues));
        record.setUpdatedBy(requireOperatorUserId(context));
        mapper.updateDraft(record);
        return draftView(record, command, issues);
    }

    public ProductListingDraftView loadDraft(BusinessAccessContext context, Long draftId) {
        requireContext(context);
        ProductListingDraftRecord record =
                workflowGuard.requireAccessibleDraft(
                        context, draftId, false);
        return draftView(record, readDraft(record.getDraftJson()), readIssues(record.getValidationJson()));
    }

    public ProductListingDraftView loadActiveSourceDraft(
            BusinessAccessContext context,
            String storeCode,
            String sourceType,
            Long sourceRefId
    ) {
        requireContext(context);
        String safeStoreCode = requireStoreCode(storeCode);
        requireStoreAccess(context, safeStoreCode);
        if (!StringUtils.hasText(sourceType)) {
            throw new IllegalArgumentException("Product listing source type is required.");
        }
        if (sourceRefId == null || sourceRefId <= 0) {
            throw new IllegalArgumentException("Product listing source reference ID is required.");
        }
        Long ownerUserId = resolveOwnerUserId(context, safeStoreCode);
        Long draftId = mapper.findActiveDraftId(
                ownerUserId,
                safeStoreCode,
                sourceType.trim(),
                sourceRefId
        );
        if (draftId == null){ return null; }
        ProductListingDraftRecord record = mapper.selectDraftById(draftId, ownerUserId);
        return record == null
                ? null
                : draftView(
                        record,
                        readDraft(record.getDraftJson()),
                        readIssues(record.getValidationJson())
                );
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

    @Transactional
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
        ProductListingDraftRecord draft =
                workflowGuard.requireLockedDraft(command.getDraftId(), ownerUserId);
        if (!storeCode.equalsIgnoreCase(draft.getStoreCode())) {
            throw new IllegalArgumentException("Dry-run store does not match the draft store.");
        }
        if (mapper.selectCurrentRealRunTaskByDraftId(ownerUserId, draft.getId()) != null) {
            throw new IllegalArgumentException(
                    "该草稿存在未决真实上架任务，不能生成新的上架检查。");
        }

        ProductListingDraftCommand draftCommand = readDraft(draft.getDraftJson());
        boolean imageMetadataChanged =
                imageMetadataEnricher.enrichMissingDimensions(draftCommand);
        List<ProductListingValidationIssue> issues = validateWithRuntimeWarnings(draftCommand, ownerUserId, draft.getStoreCode());
        boolean failed = hasHardIssues(issues);
        LocalDateTime now = LocalDateTime.now();
        Long taskId = mapper.nextProductListingTaskId();
        String canonicalDraftJson = writeJson(draftCommand);
        boolean draftChanged = imageMetadataChanged || !Objects.equals(canonicalDraftJson, draft.getDraftJson());
        String inputSnapshotJson = draftChanged ? canonicalDraftJson : draft.getDraftJson();
        if (draftChanged) {
            draft.setDraftJson(inputSnapshotJson);
            draft.setValidationJson(writeJson(issues));
            draft.setStatus(failed ? "draft" : "ready_for_dry_run");
            draft.setUpdatedBy(requireOperatorUserId(context));
            mapper.updateDraft(draft);
            if (!failed) {
                backfillDraftProjection(draft, draftCommand);
            }
        }

        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setId(taskId);
        task.setDraftId(draft.getId());
        task.setOwnerUserId(ownerUserId);
        task.setStoreCode(draft.getStoreCode());
        task.setTaskNo(taskNo(taskId));
        task.setMode(DRY_RUN_MODE);
        task.setStatus(failed ? "validation_failed" : "validated");
        task.setInputSnapshotJson(inputSnapshotJson);
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
        ProductListingTaskRecord task =
                workflowGuard.requireAccessibleTask(
                        context, taskId, false);
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
        if (dryRun == null || !"validated".equalsIgnoreCase(dryRun.getStatus())){ return new ProductListingRealRunSubmission(draftView, dryRun, null); }

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
        ProductListingTaskRecord dryRunSnapshot =
                workflowGuard.requireAccessibleTask(
                        context, dryRunTaskId, false);
        Long ownerUserId = dryRunSnapshot.getOwnerUserId();
        ProductListingDraftRecord currentDraft =
                workflowGuard.requireLockedDraft(dryRunSnapshot.getDraftId(), ownerUserId);
        ProductListingTaskRecord dryRunTask =
                mapper.selectTaskByIdForUpdate(dryRunTaskId, ownerUserId);
        if (dryRunTask == null
                || !Objects.equals(currentDraft.getId(), dryRunTask.getDraftId())) {
            throw new IllegalArgumentException("Product listing task not found.");
        }
        workflowGuard.requireRecordScope(
                context,
                dryRunTask.getOwnerUserId(),
                dryRunTask.getStoreCode(),
                "Product listing task not found."
        );

        if (!DRY_RUN_MODE.equals(dryRunTask.getMode())) {
            throw new IllegalArgumentException(
                    "Only product listing dry-run tasks can be confirmed."
            );
        }
        if (!"validated".equals(dryRunTask.getStatus())) {
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
        List<String> identityLocks =
                identityLockManager.acquireProductIdentityLocks(
                        ownerUserId,
                        dryRunTask.getStoreCode(),
                        partnerSku,
                        barcode
                );
        boolean deferredLockRelease =
                identityLockManager.deferReleaseUntilTransactionCompletion(
                        identityLocks
                );
        try {
            ProductListingTaskRecord unresolved =
                    mapper.selectCurrentRealRunTaskByDraftId(
                            ownerUserId, dryRunTask.getDraftId());
            if (unresolved != null
                    && !Objects.equals(dryRunTask.getId(), unresolved.getSourceTaskId())) {
                throw new IllegalArgumentException(
                        "该草稿存在另一个未决真实上架任务，不能重复确认。");
            }
            ProductListingTaskRecord existingAttempt = mapper.selectRealWriteAttemptTaskBySourceTaskId(
                    ownerUserId,
                    dryRunTask.getId()
            );
            if (existingAttempt != null){ return taskView(existingAttempt, readIssues(existingAttempt.getValidationJson())); }
            if (!dryRunFreshness.matches(
                    currentDraft.getDraftJson(), dryRunTask.getInputSnapshotJson())) {
                return insertRejectedRealRunTask(
                        context,
                        dryRunTask,
                        command,
                        "workflow",
                        "dry_run_stale",
                        "The draft changed after validation. Create a new dry-run before publishing."
                );
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
                        || existingPartnerSkuTask != null) {
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
                        || existingBarcodeTask != null) {
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
            ProductListingTaskView existingAfterClaim = claimAttemptOrLoadExisting(task);
            if (existingAfterClaim != null){ return existingAfterClaim; }
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
                return taskView(duplicateAttempt, readIssues(duplicateAttempt.getValidationJson()));
            }
            eventPublisher.publishEvent(new ProductListingRealRunSubmittedEvent(task.getId()));
            return taskView(task, readIssues(task.getValidationJson()));
        } finally {
            if (!deferredLockRelease) {
                identityLockManager.release(identityLocks);
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
        if (!"submitted".equals(task.getStatus())){ return taskView(task, readIssues(task.getValidationJson())); }
        LocalDateTime startedAt = LocalDateTime.now();
        final String partnerSku;
        try {
            ProductListingDraftCommand draft =
                    readDraft(task.getInputSnapshotJson());
            partnerSku = normalizeText(draft.getPsku());
        } catch (RuntimeException exception) {
            return quarantineInvalidSubmittedTask(task, startedAt, exception);
        }
        int claimed = ProductListingNoonCheckpoint.claim(
                mapper,
                objectMapper,
                task,
                startedAt,
                partnerSku
        );
        task = mapper.selectTaskByIdForWorker(realRunTaskId);
        if (task == null) {
            throw new IllegalArgumentException("Product listing real-run task not found.");
        }
        if (claimed == 0){ return taskView(task, readIssues(task.getValidationJson())); }
        task.setStatus("running");
        if (task.getStartedAt() == null) {
            task.setStartedAt(startedAt);
        }
        try (ProductListingTaskLease lease = ProductListingTaskLease.start(mapper, task)) {
            ProductListingNoonWriteResult result = executeNoonWrite(task, lease);
            lease.heartbeatOrThrow();
            applyNoonWriteResult(task, result);
            task = lease.completeOrReload(task);
            return taskView(task, readIssues(task.getValidationJson()));
        }
    }

    public int recoverStaleRunningRealRunTasks(Duration maxRunningAge) {
        Duration safeMaxRunningAge = maxRunningAge == null || maxRunningAge.isNegative() || maxRunningAge.isZero()
                ? Duration.ofMinutes(30)
                : maxRunningAge;
        LocalDateTime staleBefore = LocalDateTime.now().minus(safeMaxRunningAge);
        return mapper.recoverStaleRunningRealRunTasks(staleBefore);
    }
    public List<ProductListingTaskView> executeRunnableRealRunTasks(int limit) {
        int safeLimit = Math.max(1, limit);
        List<ProductListingTaskRecord> tasks = mapper.selectRunnableRealRunTasks(safeLimit);
        if (tasks == null || tasks.isEmpty()){ return List.of(); }
        List<ProductListingTaskView> executed = new ArrayList<>();
        for (ProductListingTaskRecord task : tasks) {
            if (task == null || task.getId() == null) {
                continue;
            }
            try {
                executed.add(executeSubmittedRealRunTask(task.getId()));
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Product listing runnable task failed without blocking the batch: taskId={}",
                        task.getId(),
                        exception
                );
            }
        }
        return executed;
    }

    private ProductListingTaskView quarantineInvalidSubmittedTask(
            ProductListingTaskRecord task,
            LocalDateTime startedAt,
            RuntimeException cause
    ) {
        int claimed = ProductListingNoonCheckpoint.claim(
                mapper,
                objectMapper,
                task,
                startedAt,
                null
        );
        ProductListingTaskRecord current =
                mapper.selectTaskByIdForWorker(task.getId());
        if (current == null) {
            throw new IllegalArgumentException(
                    "Product listing real-run task not found."
            );
        }
        if (claimed == 0) {
            return taskView(current, safeReadIssues(current));
        }
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("validate_input_snapshot");
        step.setStatus("failed");
        step.setFailureCode("invalid_input_snapshot");
        step.setFailureMessage(
                "Product listing task input snapshot is invalid."
        );
        step.setWriteMayHaveOccurred(false);
        ProductListingNoonWriteResult result =
                ProductListingNoonWriteResult.failed(
                        "validation",
                        "invalid_input_snapshot",
                        "Product listing task input snapshot is invalid.",
                        List.of(step)
                );
        result.setWriteMayHaveOccurred(false);
        current.setStatus("failed");
        current.setNoonResultJson(writeJson(result));
        current.setFailureCategory("validation");
        current.setFailureCode("invalid_input_snapshot");
        current.setFailureMessage(
                "Product listing task input snapshot is invalid."
        );
        current.setCompletedAt(LocalDateTime.now());
        if (mapper.updateRunningTaskResult(current) != 1) {
            ProductListingTaskRecord reloaded =
                    mapper.selectTaskByIdForWorker(task.getId());
            if (reloaded != null) {
                current = reloaded;
            }
        }
        LOGGER.error(
                "Quarantined invalid product listing task payload: taskId={}",
                task.getId(),
                cause
        );
        return taskView(current, safeReadIssues(current));
    }

    @Transactional
    public ProductListingTaskView verifyRealRunReadBack(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        ProductListingTaskRecord task = workflowGuard.requireRecoveryTask(
                context, realRunTaskId, ProductListingWorkflowView.NextAction.VERIFY_READBACK);
        task = ProductListingTaskLease.claimRecovery(mapper, task);
        try (ProductListingTaskLease lease = ProductListingTaskLease.start(mapper, task)) {
            ProductListingNoonWriteResult previousResult = readNoonResult(task.getNoonResultJson()), result;
            try {
                ProductListingNoonReferences references =
                        ProductListingNoonReferences.requireComplete(
                                previousResult
                        );
                ProductListingNoonWriteRequest request = noonWriteRequest(context, task);
                request.setExecutionLeaseHeartbeat(lease::heartbeatOrThrow);
                ProductListingNoonWriteStepResult readBack = noonWriteAdapter.verifyReadBack(
                        request,
                        references.skuParent(),
                        references.pskuCode(),
                        references.uploadedImagePaths()
                );
                result = resultWithReadBack(previousResult, readBack);
            } catch (RuntimeException exception) {
                ProductListingNoonCheckpoint
                        .rethrowIfLeaseLost(exception);
                result = ProductListingManualRecoveryResult.fromException(previousResult, "verify_noon_readback", exception);
            }
            lease.heartbeatOrThrow();
            return completeManualRecovery(task, lease, result);
        }
    }

    @Transactional
    public ProductListingTaskView continueRealRunAfterCreate(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        ProductListingTaskRecord task = workflowGuard.requireRecoveryTask(
                context,
                realRunTaskId,
                ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE
        );
        if (!realWriteProperties.isEnabled()) {
            throw new IllegalArgumentException("Real Noon listing writes are disabled by kill switch.");
        }
        ProductListingNoonWriteResult previousResult = readNoonResult(task.getNoonResultJson());
        String partnerSku = readPartnerSku(task.getInputSnapshotJson());
        ProductListingCreateContinuationPolicy.requireContinuationWriteAllowed(
                previousResult, task.getId(), task.getStoreCode(), partnerSku);
        task = ProductListingTaskLease.claimRecovery(mapper, task);
        try (ProductListingTaskLease lease = ProductListingTaskLease.start(mapper, task)) {
            ProductListingNoonWriteRequest request = noonWriteRequest(context, task);
            request.setExecutionLeaseHeartbeat(lease::heartbeatOrThrow);
            ProductListingNoonCheckpoint.bind(
                    request, lease, objectMapper,
                    checkpoint -> resultWithContinuation(
                            previousResult, checkpoint));
            ProductListingNoonReferences references =
                    ProductListingNoonReferences.from(previousResult);
            ProductListingNoonWriteResult result;
            try {
                if (!StringUtils.hasText(references.skuParent())
                        || !StringUtils.hasText(references.pskuCode())) {
                    throw new IllegalArgumentException(
                            "请先通过只读创建结果核对保存 Noon 商品引用，再继续创建后的步骤。"
                    );
                }
                ProductListingNoonWriteResult continuationResult =
                        noonWriteAdapter.continueAfterCreate(
                                request,
                                references.skuParent(),
                                references.pskuCode()
                        );
                result = resultWithContinuation(previousResult, continuationResult);
            } catch (RuntimeException exception) {
                ProductListingNoonCheckpoint
                        .rethrowIfLeaseLost(exception);
                result = ProductListingManualRecoveryResult.fromException(
                        previousResult, "continue_after_create", exception);
            }
            lease.heartbeatOrThrow();
            return completeManualRecovery(task, lease, result);
        }
    }

    private ProductListingTaskView completeManualRecovery(
            ProductListingTaskRecord task, ProductListingTaskLease lease, ProductListingNoonWriteResult result) {
        ProductListingNoonCheckpoint.persist(
                lease, objectMapper, result);
        applyNoonWriteResult(task, result);
        task = lease.completeOrReload(task);
        return taskView(task, readIssues(task.getValidationJson()));
    }

    @Transactional
    public ProductListingTaskView replaySuccessfulProjectionBackfill(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        ProductListingTaskRecord task = workflowGuard.requireRecoveryTask(
                context, realRunTaskId, ProductListingWorkflowView.NextAction.REPLAY_PROJECTION);
        ProductListingNoonWriteResult result = readNoonResult(task.getNoonResultJson());
        if (result == null || !result.isSuccess()) {
            throw new IllegalArgumentException("Product listing real-run task does not contain a successful Noon write result.");
        }
        if (!backfillProductProjection(task, result)) {
            throw new IllegalStateException("Product listing projection backfill failed.");
        }
        task.setStatus("succeeded");
        task.setFailureCategory(null);
        task.setFailureCode(null);
        task.setFailureMessage(null);
        task.setCompletedAt(LocalDateTime.now());
        mapper.updateTaskResult(task);
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
        ProductListingTaskView existingAfterClaim = claimAttemptOrLoadExisting(task);
        if (existingAfterClaim != null){ return existingAfterClaim; }
        mapper.insertTask(task);
        return taskView(task, readIssues(task.getValidationJson()));
    }

    private ProductListingTaskView claimAttemptOrLoadExisting(ProductListingTaskRecord task) {
        if (mapper.claimRealRunAttempt(
                task.getOwnerUserId(), task.getSourceTaskId(), task.getId()) == 1) {
            return null;
        }
        ProductListingTaskRecord existing = mapper.selectRealWriteAttemptTaskBySourceTaskId(
                task.getOwnerUserId(), task.getSourceTaskId());
        if (existing == null) {
            throw new IllegalStateException("Product listing real-run attempt claim is inconsistent.");
        }
        return taskView(existing, readIssues(existing.getValidationJson()));
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

    private ProductListingNoonWriteResult executeNoonWrite(
            ProductListingTaskRecord realRunTask,
            ProductListingTaskLease lease
    ) {
        ProductListingNoonWriteRequest request =
                noonWriteRequest(realRunTask);
        request.setExecutionLeaseHeartbeat(lease::heartbeatOrThrow);
        ProductListingNoonCheckpoint.bind(
                request, lease, objectMapper);
        ProductListingNoonWriteResult result =
                noonWriteAdapter.execute(request);
        if (result == null) {
            result = ProductListingNoonWriteResult.failed(
                    "configuration",
                    "noon_write_adapter_empty_result",
                    "Product listing Noon write adapter returned an empty result.",
                    List.of()
            );
        }
        ProductListingNoonCheckpoint.persist(
                lease, objectMapper, result);
        return result;
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
            if (ProductListingWorkflowEvidence.hasFailedWriteStep(previousResult)) {
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
        ).withPriorWriteCompleted();
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
        if (safeContinuation.isSuccess()){ return ProductListingNoonWriteResult.succeeded(steps); }
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
        ).withPriorWriteCompleted();
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
        if (ProductListingWriteAuthRecovery.FAILURE_CODE.equals(result.getFailureCode())) {
            task.setStatus(Boolean.TRUE.equals(result.getWriteMayHaveOccurred())
                    ? REAL_RUN_STATUS_WRITTEN_VERIFY_FAILED
                    : "failed");
            task.setFailureCategory("authorization");
            task.setFailureCode(ProductListingWriteAuthRecovery.FAILURE_CODE);
            task.setFailureMessage(StringUtils.hasText(result.getFailureMessage())
                    ? result.getFailureMessage()
                    : "Noon Project 授权恢复中；系统不会自动重放本次上架。");
            return;
        }
        if (isCreateOutcomeUnknown(result)) {
            task.setStatus(REAL_RUN_STATUS_WRITTEN_VERIFY_FAILED);
            task.setFailureCategory("noon_uncertain_write");
            task.setFailureCode("noon_create_outcome_unknown");
            task.setFailureMessage("Noon 创建请求结果未知；请核对创建结果并继续后续步骤，不要重复提交上架。");
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
            LOGGER.error(
                    "Product listing draft projection failed; rolling back draft save: draftId={}, draftNo={}",
                    record == null ? null : record.getId(),
                    record == null ? null : record.getDraftNo(),
                    exception
            );
            throw new IllegalStateException(
                    "商品上架草稿无法同步到商品列表，请稍后重试；本次草稿保存已取消。",
                    exception
            );
        }
    }

    private boolean isWrittenButVerificationFailed(ProductListingNoonWriteResult result) {
        return result != null
                && !result.isSuccess()
                && ProductListingWorkflowEvidence.hasConfirmedCreate(result);
    }

    private boolean isCreateOutcomeUnknown(ProductListingNoonWriteResult result){ return ProductListingWorkflowEvidence.hasUnresolvedCreateOutcome(result); }

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
        view.setPartnerSku(safeReadPartnerSku(record));
        view.setMode(record.getMode());
        view.setStatus(record.getStatus());
        view.setSourceTaskId(record.getSourceTaskId());
        view.setValidationIssues(issues);
        view.setFailureCategory(record.getFailureCategory());
        view.setFailureCode(record.getFailureCode());
        view.setFailureMessage(record.getFailureMessage());
        ProductListingNoonWriteResult noonResult =
                readNoonResult(record.getNoonResultJson());
        view.setNoonResult(noonResult);
        ProductListingNoonReferences references =
                ProductListingNoonReferences.from(noonResult);
        view.setSkuParent(references.skuParent());
        view.setPskuCode(references.pskuCode());
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

    private void preserveExistingStableDraftFields(
            ProductListingDraftCommand command,
            ProductListingDraftRecord existing
    ) {
        if (command == null || existing == null || !StringUtils.hasText(existing.getDraftJson())) {
            return;
        }
        ProductListingStableDraftFields.preserve(
                command,
                readDraft(existing.getDraftJson())
        );
    }

    private void requireStoreAccess(BusinessAccessContext context, String storeCode) {
        if (!context.canAccessStore(storeCode)) {
            throw new BusinessAccessDeniedException("当前账号不能操作该店铺。");
        }
    }

    private Long resolveOwnerUserId(BusinessAccessContext context, String storeCode){
        Long ownerUserId = ProductListingOwnerScope.resolve(context, storeCode);
        if (ownerUserId == null){ throw new IllegalArgumentException("Business owner user ID is required."); }
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
        issues.addAll(taxonomyGuard.validateAndHydrate(safeCommand));
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
    }

    private ProductListingValidationIssue warning(String fieldKey, String code, String message){ return new ProductListingValidationIssue(fieldKey, "warning", code, message); }

    private ProductListingValidationIssue error(String fieldKey, String code, String message){ return new ProductListingValidationIssue(fieldKey, "error", code, message); }

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

    private ProductListingNoonWriteResult normalizeNoonWriteFailure(
            ProductListingTaskRecord task,
            ProductListingNoonWriteResult result
    ) {
        if (result == null || result.isSuccess() || !isPartnerSkuAlreadyExistsFailure(result)){ return result; }
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
        if (result == null){ return false; }
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
        if (!StringUtils.hasText(value)){ return false; }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("partner skus already exists")
                || normalized.contains("partner sku already exists")
                || normalized.contains(PARTNER_SKU_ALREADY_EXISTS_CODE);
    }

    private String extractPartnerSkuAlreadyExists(ProductListingNoonWriteResult result) {
        String fromResult = extractPartnerSkuAlreadyExists(result == null ? null : result.getFailureMessage());
        if (StringUtils.hasText(fromResult)){ return fromResult; }
        if (result == null || result.getSteps() == null){ return null; }
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            String fromStep = extractPartnerSkuAlreadyExists(step == null ? null : step.getFailureMessage());
            if (StringUtils.hasText(fromStep)){ return fromStep; }
        }
        return null;
    }

    private String extractPartnerSkuAlreadyExists(String value) {
        if (!StringUtils.hasText(value)){ return null; }
        Matcher matcher = PARTNER_SKU_ALREADY_EXISTS_PATTERN.matcher(value);
        if (!matcher.find()){ return null; }
        return normalizeText(matcher.group(1));
    }

    private String partnerSkuAlreadyExistsMessage(String partnerSku) {
        String normalized = normalizeText(partnerSku);
        if (StringUtils.hasText(normalized)){ return "PSKU 已存在，不能重复创建：" + normalized + "。请更换新的 PSKU，或到商品详情中编辑已有商品。"; }
        return "PSKU 已存在，不能重复创建。请更换新的 PSKU，或到商品详情中编辑已有商品。";
    }

    private String localPartnerSkuAlreadyExistsMessage(String partnerSku) {
        String normalized = normalizeText(partnerSku);
        if (StringUtils.hasText(normalized)){ return "当前本地店铺已存在相同 PSKU：" + normalized + "。请更换新的 PSKU，或到商品详情中编辑已有商品。"; }
        return "当前本地店铺已存在相同 PSKU。请更换新的 PSKU，或到商品详情中编辑已有商品。";
    }

    private String barcodeAlreadyExistsMessage(String barcode) {
        String normalized = normalizeText(barcode);
        if (StringUtils.hasText(normalized)){ return "当前本地店铺已存在相同 Barcode：" + normalized + "。请更换新的 Barcode，或到商品详情中编辑已有商品。"; }
        return "当前本地店铺已存在相同 Barcode。请更换新的 Barcode，或到商品详情中编辑已有商品。";
    }

    private boolean isSameRebuildSourceProduct(Long existingProductId, ProductListingDraftCommand command) {
        Long rebuildSourceProductMasterId = command == null ? null : command.getRebuildSourceProductMasterId();
        return existingProductId != null
                && rebuildSourceProductMasterId != null
                && existingProductId.equals(rebuildSourceProductMasterId);
    }

    private boolean isProductRebuildDraft(ProductListingDraftCommand command) {
        if (command == null){ return false; }
        if (command.getRebuildSourceProductMasterId() != null){ return true; }
        return "PRODUCT_REBUILD".equalsIgnoreCase(normalizeText(command.getSourceType()));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String draftNo(Long draftId){ return "PLD-" + draftId; }

    private String taskNo(Long taskId){ return "PLT-" + taskId; }

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
        if (!StringUtils.hasText(json)){ return null; }
        try {
            return objectMapper.readValue(json, ProductListingRealRunCommand.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse product listing real-run confirmation payload.", exception);
        }
    }

    private String readPartnerSku(String json) {
        if (!StringUtils.hasText(json)){ return null; }
        ProductListingDraftCommand draft = readDraft(json);
        return StringUtils.hasText(draft.getPsku()) ? draft.getPsku().trim() : null;
    }

    private List<ProductListingValidationIssue> readIssues(String json) {
        if (!StringUtils.hasText(json)){ return Collections.emptyList(); }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProductListingValidationIssue>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse product listing validation payload.", exception);
        }
    }

    private List<ProductListingValidationIssue> safeReadIssues(
            ProductListingTaskRecord task
    ) {
        try {
            return readIssues(task == null ? null : task.getValidationJson());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Ignoring invalid product listing validation payload while rendering task: taskId={}",
                    task == null ? null : task.getId(),
                    exception
            );
            return List.of();
        }
    }

    private String safeReadPartnerSku(ProductListingTaskRecord task) {
        try {
            return readPartnerSku(
                    task == null ? null : task.getInputSnapshotJson()
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Ignoring invalid product listing input snapshot while rendering task: taskId={}",
                    task == null ? null : task.getId(),
                    exception
            );
            return null;
        }
    }

    private ProductListingNoonWriteResult readNoonResult(String json) {
        if (!StringUtils.hasText(json)){ return null; }
        try {
            return objectMapper.readValue(json, ProductListingNoonWriteResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse product listing Noon result payload.", exception);
        }
    }

}
