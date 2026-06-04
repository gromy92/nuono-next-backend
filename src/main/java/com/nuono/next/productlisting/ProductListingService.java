package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductListingService {

    private static final String DRY_RUN_MODE = "DRY_RUN";
    private static final String REAL_RUN_MODE = "REAL_RUN";

    private final ProductListingMapper mapper;
    private final ObjectMapper objectMapper;
    private final ProductListingValidator validator;
    private final ProductListingRealWriteProperties realWriteProperties;
    private final ProductListingNoonWriteAdapter noonWriteAdapter;

    @Autowired
    public ProductListingService(
            ProductListingMapper mapper,
            ObjectMapper objectMapper,
            ProductListingValidator validator,
            ProductListingRealWriteProperties realWriteProperties,
            ProductListingNoonWriteAdapter noonWriteAdapter
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
        List<ProductListingValidationIssue> issues = validator.validate(safeCommand);
        String status = hasHardIssues(issues) ? "draft" : "ready_for_dry_run";

        ProductListingDraftRecord existing = null;
        Long draftId = safeCommand.getDraftId();
        if (draftId == null) {
            draftId = mapper.nextProductListingDraftId();
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

        ProductListingDraftRecord record = new ProductListingDraftRecord();
        record.setId(draftId);
        record.setOwnerUserId(ownerUserId);
        record.setStoreCode(storeCode);
        record.setDraftNo(existing == null ? draftNo(draftId) : existing.getDraftNo());
        record.setSourceType(existing == null ? null : existing.getSourceType());
        record.setSourceRefId(existing == null ? null : existing.getSourceRefId());
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
        return draftView(record, safeCommand, issues);
    }

    public ProductListingDraftView validateDraft(BusinessAccessContext context, Long draftId) {
        requireContext(context);
        Long ownerUserId = requireOwnerUserId(context);
        ProductListingDraftRecord record = requireDraft(draftId, ownerUserId);
        requireStoreAccess(context, record.getStoreCode());
        ProductListingDraftCommand command = readDraft(record.getDraftJson());
        List<ProductListingValidationIssue> issues = validator.validate(command);
        record.setStatus(hasHardIssues(issues) ? "draft" : "ready_for_dry_run");
        record.setValidationJson(writeJson(issues));
        record.setUpdatedBy(requireOperatorUserId(context));
        mapper.updateDraft(record);
        return draftView(record, command, issues);
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
        List<ProductListingValidationIssue> issues = validator.validate(draftCommand);
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
        ProductListingTaskRecord existingAttempt = mapper.selectRealWriteAttemptTaskBySourceTaskId(
                ownerUserId,
                dryRunTask.getId()
        );
        if (existingAttempt != null) {
            return rejectExistingRealWriteAttempt(context, dryRunTask, command, existingAttempt);
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
                "running",
                null,
                null,
                null
        );
        task.setStartedAt(LocalDateTime.now());
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
        ProductListingNoonWriteResult result = executeNoonWrite(context, dryRunTask, task, command);
        applyNoonWriteResult(task, result);
        mapper.updateTaskResult(task);
        return taskView(task, readIssues(task.getValidationJson()));
    }

    public List<ProductListingTaskView> recentTasks(
            BusinessAccessContext context,
            String storeCode,
            int limit
    ) {
        requireContext(context);
        String safeStoreCode = requireStoreCode(storeCode);
        requireStoreAccess(context, safeStoreCode);
        Long ownerUserId = resolveOwnerUserId(context, safeStoreCode);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return mapper.selectRecentTasks(ownerUserId, safeStoreCode, safeLimit).stream()
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

    private ProductListingNoonWriteResult executeNoonWrite(
            BusinessAccessContext context,
            ProductListingTaskRecord dryRunTask,
            ProductListingTaskRecord realRunTask,
            ProductListingRealRunCommand command
    ) {
        try {
            ProductListingNoonWriteResult result = noonWriteAdapter.execute(noonWriteRequest(
                    context,
                    dryRunTask,
                    realRunTask,
                    command
            ));
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
            ProductListingTaskRecord dryRunTask,
            ProductListingTaskRecord realRunTask,
            ProductListingRealRunCommand command
    ) {
        ProductListingNoonWriteRequest request = new ProductListingNoonWriteRequest();
        request.setOwnerUserId(dryRunTask.getOwnerUserId());
        request.setStoreCode(dryRunTask.getStoreCode());
        request.setDraftId(dryRunTask.getDraftId());
        request.setDryRunTaskId(dryRunTask.getId());
        request.setRealRunTaskId(realRunTask.getId());
        request.setSubmittedBy(requireOperatorUserId(context));
        request.setDraft(readDraft(dryRunTask.getInputSnapshotJson()));
        request.setValidationIssues(readIssues(dryRunTask.getValidationJson()));
        request.setConfirmation(command);
        return request;
    }

    private void applyNoonWriteResult(
            ProductListingTaskRecord task,
            ProductListingNoonWriteResult result
    ) {
        task.setNoonResultJson(writeJson(result));
        task.setCompletedAt(LocalDateTime.now());
        if (result.isSuccess()) {
            task.setStatus("succeeded");
            task.setFailureCategory(null);
            task.setFailureCode(null);
            task.setFailureMessage(null);
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
        view.setMode(record.getMode());
        view.setStatus(record.getStatus());
        view.setSourceTaskId(record.getSourceTaskId());
        view.setValidationIssues(issues);
        view.setFailureCategory(record.getFailureCategory());
        view.setFailureCode(record.getFailureCode());
        view.setFailureMessage(record.getFailureMessage());
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

    private boolean isActiveRealRunStatus(String status) {
        return "running".equals(status) || "submitted".equals(status);
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
}
