package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductListingCreateOutcomeService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProductListingCreateOutcomeService.class);
    private static final int MIN_RELIABLE_NOT_FOUND_CHECKS = 3;
    private static final Duration MIN_NOT_FOUND_CONFIRMATION_AGE =
            Duration.ofMinutes(2);
    private static final Duration MIN_NOT_FOUND_LOOKUP_INTERVAL =
            Duration.ofSeconds(30);
    private static final Duration MIN_NOT_FOUND_EVIDENCE_WINDOW =
            Duration.ofMinutes(2);

    private final ProductListingMapper mapper;
    private final ProductListingService listingService;
    private final ProductListingNoonWriteAdapter noonWriteAdapter;
    private final ObjectMapper objectMapper;

    public ProductListingCreateOutcomeService(
            ProductListingMapper mapper,
            ProductListingService listingService,
            ProductListingNoonWriteAdapter noonWriteAdapter,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.listingService = listingService;
        this.noonWriteAdapter = noonWriteAdapter;
        this.objectMapper = objectMapper;
    }

    public ProductListingCreateOutcomeVerificationView verify(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        ProductListingTaskView authorized = listingService.loadTask(context, realRunTaskId);
        requireVerifiable(authorized);
        ProductListingTaskRecord task =
                mapper.selectTaskById(realRunTaskId, authorized.getOwnerUserId());
        requireLatestVerifiable(authorized, task);
        String partnerSku = readDraft(task.getInputSnapshotJson()).getPsku();
        ProductListingNoonWriteResult previous = readNoonResult(task.getNoonResultJson());
        References existing = references(previous);
        if (existing.complete()) {
            return ProductListingCreateOutcomeVerificationView.found(
                    task.getId(), partnerSku, existing.skuParent, existing.pskuCode);
        }

        ProductListingNoonWriteStepResult lookup;
        try {
            lookup = noonWriteAdapter.resolveCreateReference(request(task));
        } catch (RuntimeException exception) {
            if (NoonAuthenticationFailureClassifier.isAuthenticationFailure(
                    exception
            )) {
                return markAuthenticationRequired(
                        task,
                        partnerSku,
                        previous,
                        authenticationFailureStep()
                );
            }
            LOGGER.warn(
                    "Product listing create outcome lookup failed: taskId={}, causeType={}",
                    task.getId(),
                    exception.getClass().getSimpleName()
            );
            return ProductListingCreateOutcomeVerificationView.lookupFailed(
                    task.getId(), partnerSku);
        }
        if (lookup != null
                && "noon_auth_required".equalsIgnoreCase(
                lookup.getFailureCode())) {
            return markAuthenticationRequired(
                    task, partnerSku, previous, lookup);
        }
        References found = references(lookup);
        if (lookup != null && "succeeded".equalsIgnoreCase(lookup.getStatus()) && found.complete()) {
            ProductListingNoonWriteResult updated = append(previous, lookup);
            String updatedJson = writeJson(updated);
            if (mapper.persistRecoveredCreateReference(
                    task.getId(),
                    task.getOwnerUserId(),
                    task.getNoonResultJson(),
                    updatedJson
            ) != 1) {
                return foundAfterConcurrentUpdate(task, partnerSku);
            }
            return ProductListingCreateOutcomeVerificationView.found(
                    task.getId(), partnerSku, found.skuParent, found.pskuCode);
        }
        if (lookup != null
                && "noon_create_reference_not_found".equalsIgnoreCase(lookup.getFailureCode())) {
            int attemptNumber = notFoundLookupCount(previous) + 1;
            lookup.setExternalReference(
                    "lookupAttempt=" + attemptNumber
                            + ";lookupCheckedAt=" + LocalDateTime.now()
            );
            ProductListingNoonWriteResult updated = append(previous, lookup);
            ProductListingTaskRecord persisted = persistLookup(task, updated);
            ProductListingNoonWriteResult persistedResult =
                    readNoonResult(persisted.getNoonResultJson());
            References concurrentReferences = references(persistedResult);
            if (concurrentReferences.complete()) {
                return ProductListingCreateOutcomeVerificationView.found(
                        persisted.getId(),
                        partnerSku,
                        concurrentReferences.skuParent,
                        concurrentReferences.pskuCode
                );
            }
            List<LocalDateTime> reliableChecks =
                    reliableNotFoundChecks(persistedResult);
            return ProductListingCreateOutcomeVerificationView.notFound(
                    task.getId(),
                    partnerSku,
                    reliableChecks.size(),
                    canConfirmNotCreated(persisted, reliableChecks)
            );
        }
        return ProductListingCreateOutcomeVerificationView.lookupFailed(
                task.getId(), partnerSku);
    }

    private ProductListingCreateOutcomeVerificationView
            markAuthenticationRequired(
            ProductListingTaskRecord task,
            String partnerSku,
            ProductListingNoonWriteResult previous,
            ProductListingNoonWriteStepResult lookup
    ) {
        ProductListingNoonWriteResult updated = append(previous, lookup);
        if (mapper.markCreateOutcomeLookupAuthenticationRequired(
                task.getId(),
                task.getOwnerUserId(),
                task.getNoonResultJson(),
                writeJson(updated)
        ) == 1) {
            return ProductListingCreateOutcomeVerificationView
                    .reauthenticationRequired(task.getId(), partnerSku);
        }
        ProductListingTaskRecord latest =
                mapper.selectTaskById(task.getId(), task.getOwnerUserId());
        References latestReferences = references(
                latest == null
                        ? null
                        : readNoonResult(latest.getNoonResultJson())
        );
        if (latestReferences.complete()) {
            return ProductListingCreateOutcomeVerificationView.found(
                    task.getId(),
                    partnerSku,
                    latestReferences.skuParent,
                    latestReferences.pskuCode
            );
        }
        if (latest != null
                && "noon_auth_required".equalsIgnoreCase(
                latest.getFailureCode())) {
            return ProductListingCreateOutcomeVerificationView
                    .reauthenticationRequired(task.getId(), partnerSku);
        }
        throw new IllegalArgumentException(
                "The product listing task changed; reload the workflow before checking again."
        );
    }

    private ProductListingNoonWriteStepResult authenticationFailureStep() {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("resolve_create_reference");
        step.setStatus("failed");
        step.setFailureCode("noon_auth_required");
        step.setFailureMessage(
                "Noon authorization expired during create reference lookup."
        );
        return step;
    }

    @Transactional
    public Long confirmNotCreated(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        ProductListingTaskView authorized =
                listingService.loadTask(context, realRunTaskId);
        requireVerifiable(authorized);
        ProductListingTaskRecord task = mapper.selectTaskByIdForUpdate(
                realRunTaskId,
                authorized.getOwnerUserId()
        );
        requireLatestVerifiable(authorized, task);
        ProductListingNoonWriteResult previous =
                readNoonResult(task.getNoonResultJson());
        List<LocalDateTime> reliableChecks =
                reliableNotFoundChecks(previous);
        if (!canConfirmNotCreated(task, reliableChecks)) {
            throw new IllegalArgumentException(
                    "创建结果尚未达到安全确认条件，请稍后继续执行只读核对。"
            );
        }

        ProductListingNoonWriteStepResult confirmation =
                new ProductListingNoonWriteStepResult();
        confirmation.setStepKey("confirm_create_not_found");
        confirmation.setStatus("succeeded");
        confirmation.setExternalReference(
                "lookupAttempts=" + reliableChecks.size()
                        + ";firstLookupAt=" + reliableChecks.get(0)
                        + ";lastLookupAt="
                        + reliableChecks.get(reliableChecks.size() - 1)
        );
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        if (previous != null && previous.getSteps() != null) {
            steps.addAll(previous.getSteps());
        }
        steps.add(confirmation);
        String message = "已基于多次只读核对确认 Noon 未创建商品；"
                + "本次真实上架尝试已关闭，可返回修改草稿。";
        ProductListingNoonWriteResult confirmed =
                ProductListingNoonWriteResult.failed(
                        "noon_pre_create",
                        "noon_create_not_found_confirmed",
                        message,
                        steps
                );
        task.setNoonResultJson(writeJson(confirmed));
        task.setStatus("failed");
        task.setFailureCategory("noon_pre_create");
        task.setFailureCode("noon_create_not_found_confirmed");
        task.setFailureMessage(message);
        task.setCompletedAt(LocalDateTime.now());
        if (mapper.updateTaskResult(task) != 1) {
            throw new IllegalArgumentException(
                    "上架任务状态已变化，请刷新后重试。"
            );
        }
        if (task.getSourceTaskId() == null
                || mapper.markValidatedDryRunSuperseded(
                task.getSourceTaskId(),
                task.getOwnerUserId()
        ) != 1) {
            throw new IllegalArgumentException(
                    "原上架检查无法安全关闭，请刷新后重试。"
            );
        }
        return task.getDraftId();
    }

    private void requireVerifiable(ProductListingTaskView task) {
        boolean unknownOutcome = task != null
                && ("noon_create_outcome_unknown".equalsIgnoreCase(task.getFailureCode())
                || "real_run_interrupted".equalsIgnoreCase(task.getFailureCode()));
        if (task == null
                || !"REAL_RUN".equalsIgnoreCase(task.getMode())
                || !"written_verify_failed".equalsIgnoreCase(task.getStatus())
                || !unknownOutcome) {
            throw new IllegalArgumentException(
                    "Only a real-run with an unknown create outcome can be checked.");
        }
    }

    private void requireLatestVerifiable(
            ProductListingTaskView authorized,
            ProductListingTaskRecord latest
    ) {
        boolean sameIdentity = latest != null
                && Objects.equals(authorized.getTaskId(), latest.getId())
                && Objects.equals(authorized.getOwnerUserId(), latest.getOwnerUserId())
                && Objects.equals(authorized.getDraftId(), latest.getDraftId())
                && sameText(authorized.getStoreCode(), latest.getStoreCode());
        boolean unknownOutcome = latest != null
                && ("noon_create_outcome_unknown".equalsIgnoreCase(latest.getFailureCode())
                || "real_run_interrupted".equalsIgnoreCase(latest.getFailureCode()));
        if (!sameIdentity
                || !"REAL_RUN".equalsIgnoreCase(latest.getMode())
                || !"written_verify_failed".equalsIgnoreCase(latest.getStatus())
                || !unknownOutcome) {
            throw new IllegalArgumentException(
                    "The product listing task changed; reload the workflow before checking again.");
        }
    }

    private boolean sameText(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }

    private ProductListingCreateOutcomeVerificationView foundAfterConcurrentUpdate(
            ProductListingTaskRecord original,
            String partnerSku
    ) {
        ProductListingTaskRecord latest =
                mapper.selectTaskById(original.getId(), original.getOwnerUserId());
        References latestReferences = references(
                latest == null ? null : readNoonResult(latest.getNoonResultJson()));
        if (!latestReferences.complete()) {
            throw new IllegalArgumentException(
                    "The product listing task changed; reload the workflow before checking again.");
        }
        return ProductListingCreateOutcomeVerificationView.found(
                original.getId(),
                partnerSku,
                latestReferences.skuParent,
                latestReferences.pskuCode
        );
    }

    private ProductListingTaskRecord persistLookup(
            ProductListingTaskRecord original,
            ProductListingNoonWriteResult updated
    ) {
        String updatedJson = writeJson(updated);
        if (mapper.persistRecoveredCreateReference(
                original.getId(),
                original.getOwnerUserId(),
                original.getNoonResultJson(),
                updatedJson
        ) == 1) {
            original.setNoonResultJson(updatedJson);
            return original;
        }
        ProductListingTaskRecord latest =
                mapper.selectTaskById(original.getId(), original.getOwnerUserId());
        if (latest == null) {
            throw new IllegalArgumentException(
                    "上架任务状态已变化，请刷新后重试。"
            );
        }
        return latest;
    }

    private int notFoundLookupCount(ProductListingNoonWriteResult result) {
        if (result == null || result.getSteps() == null) {
            return 0;
        }
        return (int) result.getSteps().stream()
                .filter(Objects::nonNull)
                .filter(step -> "resolve_create_reference".equalsIgnoreCase(
                        step.getStepKey()
                ))
                .filter(step -> "failed".equalsIgnoreCase(step.getStatus()))
                .filter(step -> "noon_create_reference_not_found".equalsIgnoreCase(
                        step.getFailureCode()
                ))
                .count();
    }

    private List<LocalDateTime> reliableNotFoundChecks(
            ProductListingNoonWriteResult result
    ) {
        if (result == null || result.getSteps() == null) {
            return List.of();
        }
        List<LocalDateTime> reliable = new ArrayList<>();
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            if (step == null
                    || !"resolve_create_reference".equalsIgnoreCase(
                    step.getStepKey())
                    || !"failed".equalsIgnoreCase(step.getStatus())
                    || !"noon_create_reference_not_found".equalsIgnoreCase(
                    step.getFailureCode())) {
                continue;
            }
            LocalDateTime checkedAt = externalReferenceTime(
                    step.getExternalReference(), "lookupCheckedAt");
            if (checkedAt == null) {
                continue;
            }
            if (reliable.isEmpty()
                    || !checkedAt.isBefore(
                    reliable.get(reliable.size() - 1)
                            .plus(MIN_NOT_FOUND_LOOKUP_INTERVAL))) {
                reliable.add(checkedAt);
            }
        }
        return reliable;
    }

    private boolean canConfirmNotCreated(
            ProductListingTaskRecord task,
            List<LocalDateTime> reliableChecks
    ) {
        if (task == null
                || reliableChecks == null
                || reliableChecks.size() < MIN_RELIABLE_NOT_FOUND_CHECKS
                || task.getCompletedAt() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime first = reliableChecks.get(0);
        LocalDateTime last = reliableChecks.get(reliableChecks.size() - 1);
        return !task.getCompletedAt()
                .plus(MIN_NOT_FOUND_CONFIRMATION_AGE)
                .isAfter(now)
                && !first.plus(MIN_NOT_FOUND_EVIDENCE_WINDOW).isAfter(last)
                && !last.isAfter(now);
    }

    private LocalDateTime externalReferenceTime(
            String externalReference,
            String expectedKey
    ) {
        if (!StringUtils.hasText(externalReference)) {
            return null;
        }
        for (String token : externalReference.split(";")) {
            int separator = token.indexOf('=');
            if (separator <= 0
                    || !expectedKey.equalsIgnoreCase(
                    token.substring(0, separator).trim())) {
                continue;
            }
            try {
                return LocalDateTime.parse(
                        token.substring(separator + 1).trim());
            } catch (java.time.format.DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private ProductListingNoonWriteRequest request(ProductListingTaskRecord task) {
        ProductListingNoonWriteRequest request = new ProductListingNoonWriteRequest();
        request.setOwnerUserId(task.getOwnerUserId());
        request.setStoreCode(task.getStoreCode());
        request.setDraftId(task.getDraftId());
        request.setDryRunTaskId(task.getSourceTaskId());
        request.setRealRunTaskId(task.getId());
        request.setSubmittedBy(task.getSubmittedBy());
        request.setDraft(readDraft(task.getInputSnapshotJson()));
        request.setValidationIssues(readIssues(task.getValidationJson()));
        request.setConfirmation(readConfirmation(task.getConfirmationJson()));
        return request;
    }

    private ProductListingNoonWriteResult append(
            ProductListingNoonWriteResult previous,
            ProductListingNoonWriteStepResult lookup
    ) {
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        if (previous != null && previous.getSteps() != null) {
            steps.addAll(previous.getSteps());
        }
        steps.add(lookup);
        return ProductListingNoonWriteResult.failed(
                previous == null ? "noon_uncertain_write" : previous.getFailureCategory(),
                previous == null ? "noon_create_outcome_unknown" : previous.getFailureCode(),
                previous == null ? "Noon create outcome was unknown." : previous.getFailureMessage(),
                steps
        );
    }

    private References references(ProductListingNoonWriteResult result) {
        References references = new References();
        if (result != null && result.getSteps() != null) {
            result.getSteps().forEach(step -> references.accept(
                    step == null ? null : step.getExternalReference()));
        }
        return references;
    }

    private References references(ProductListingNoonWriteStepResult step) {
        References references = new References();
        references.accept(step == null ? null : step.getExternalReference());
        return references;
    }

    private ProductListingDraftCommand readDraft(String json) {
        return readJson(json, ProductListingDraftCommand.class, "draft");
    }

    private ProductListingRealRunCommand readConfirmation(String json) {
        return readJson(json, ProductListingRealRunCommand.class, "confirmation");
    }

    private ProductListingNoonWriteResult readNoonResult(String json) {
        return !StringUtils.hasText(json)
                ? null
                : readJson(json, ProductListingNoonWriteResult.class, "Noon result");
    }

    private List<ProductListingValidationIssue> readIssues(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<ProductListingValidationIssue>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to parse product listing validation payload.", exception);
        }
    }

    private <T> T readJson(String json, Class<T> type, String label) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse product listing " + label + ".", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to persist recovered Noon reference.", exception);
        }
    }

    private static final class References {
        private String skuParent;
        private String pskuCode;

        private void accept(String value) {
            if (!StringUtils.hasText(value)) {
                return;
            }
            for (String token : value.split(";")) {
                int separator = token.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = token.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String item = token.substring(separator + 1).trim();
                if ("skuparent".equals(key) && StringUtils.hasText(item)) {
                    skuParent = item;
                } else if ("pskucode".equals(key) && StringUtils.hasText(item)) {
                    pskuCode = item;
                }
            }
        }

        private boolean complete() {
            return StringUtils.hasText(skuParent) && StringUtils.hasText(pskuCode);
        }
    }
}
