package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProductListingCreateOutcomeVerifier {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProductListingCreateOutcomeVerifier.class);

    private final ProductListingMapper mapper;
    private final ProductListingService listingService;
    private final ProductListingNoonWriteAdapter noonWriteAdapter;
    private final ProductListingCreateOutcomeSupport support;

    ProductListingCreateOutcomeVerifier(
            ProductListingMapper mapper,
            ProductListingService listingService,
            ProductListingNoonWriteAdapter noonWriteAdapter,
            ProductListingCreateOutcomeSupport support
    ) {
        this.mapper = mapper;
        this.listingService = listingService;
        this.noonWriteAdapter = noonWriteAdapter;
        this.support = support;
    }

    ProductListingCreateOutcomeVerificationView verify(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        ProductListingTaskView authorized = listingService.loadTask(context, realRunTaskId);
        ProductListingCreateOutcomeValidation.requireVerifiable(authorized);
        ProductListingTaskRecord task =
                mapper.selectTaskById(realRunTaskId, authorized.getOwnerUserId());
        ProductListingCreateOutcomeValidation.requireLatestVerifiable(authorized, task);
        String partnerSku = support.readDraft(task.getInputSnapshotJson()).getPsku();
        ProductListingNoonWriteResult previous =
                support.readNoonResult(task.getNoonResultJson());
        ProductListingCreateOutcomeSupport.References existing =
                support.references(previous);
        if (existing.complete()) {
            return found(task.getId(), partnerSku, existing);
        }

        ProductListingNoonWriteStepResult lookup;
        try {
            lookup = noonWriteAdapter.resolveCreateReference(support.request(task));
        } catch (RuntimeException exception) {
            if (NoonAuthenticationFailureClassifier.isAuthenticationFailure(exception)) {
                return markAuthenticationRequired(
                        task, partnerSku, previous, authenticationFailureStep());
            }
            LOGGER.warn(
                    "Product listing create outcome lookup failed: taskId={}, causeType={}",
                    task.getId(), exception.getClass().getSimpleName());
            return ProductListingCreateOutcomeVerificationView.lookupFailed(
                    task.getId(), partnerSku);
        }
        if (lookup != null
                && "noon_auth_required".equalsIgnoreCase(lookup.getFailureCode())) {
            return markAuthenticationRequired(task, partnerSku, previous, lookup);
        }
        ProductListingCreateOutcomeSupport.References references =
                support.references(lookup);
        if (lookup != null && "succeeded".equalsIgnoreCase(lookup.getStatus())
                && references.complete()) {
            String updatedJson = support.writeJson(support.append(previous, lookup));
            if (mapper.persistRecoveredCreateReference(
                    task.getId(), task.getOwnerUserId(),
                    task.getNoonResultJson(), updatedJson) != 1) {
                return foundAfterConcurrentUpdate(task, partnerSku);
            }
            return found(task.getId(), partnerSku, references);
        }
        if (lookup != null && "noon_create_reference_not_found"
                .equalsIgnoreCase(lookup.getFailureCode())) {
            int attemptNumber = support.notFoundLookupCount(previous) + 1;
            lookup.setExternalReference(
                    "lookupAttempt=" + attemptNumber
                            + ";lookupCheckedAt=" + LocalDateTime.now());
            ProductListingTaskRecord persisted = persistLookup(
                    task, support.append(previous, lookup));
            ProductListingNoonWriteResult persistedResult =
                    support.readNoonResult(persisted.getNoonResultJson());
            ProductListingCreateOutcomeSupport.References concurrent =
                    support.references(persistedResult);
            if (concurrent.complete()) {
                return found(persisted.getId(), partnerSku, concurrent);
            }
            List<LocalDateTime> reliableChecks =
                    support.reliableNotFoundChecks(persistedResult);
            return ProductListingCreateOutcomeVerificationView.notFound(
                    task.getId(), partnerSku, reliableChecks.size(),
                    support.canConfirmNotCreated(persisted, reliableChecks));
        }
        return ProductListingCreateOutcomeVerificationView.lookupFailed(
                task.getId(), partnerSku);
    }

    private ProductListingCreateOutcomeVerificationView markAuthenticationRequired(
            ProductListingTaskRecord task,
            String partnerSku,
            ProductListingNoonWriteResult previous,
            ProductListingNoonWriteStepResult lookup
    ) {
        ProductListingNoonWriteResult updated = support.append(previous, lookup);
        if (mapper.markCreateOutcomeLookupAuthenticationRequired(
                task.getId(), task.getOwnerUserId(), task.getNoonResultJson(),
                support.writeJson(updated)) == 1) {
            return ProductListingCreateOutcomeVerificationView
                    .reauthenticationRequired(task.getId(), partnerSku);
        }
        ProductListingTaskRecord latest =
                mapper.selectTaskById(task.getId(), task.getOwnerUserId());
        ProductListingCreateOutcomeSupport.References references =
                support.references(latest == null
                        ? null : support.readNoonResult(latest.getNoonResultJson()));
        if (references.complete()) {
            return found(task.getId(), partnerSku, references);
        }
        if (latest != null
                && "noon_auth_required".equalsIgnoreCase(latest.getFailureCode())) {
            return ProductListingCreateOutcomeVerificationView
                    .reauthenticationRequired(task.getId(), partnerSku);
        }
        throw changedTask();
    }

    private ProductListingNoonWriteStepResult authenticationFailureStep() {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("resolve_create_reference");
        step.setStatus("failed");
        step.setFailureCode("noon_auth_required");
        step.setFailureMessage(
                "Noon authorization expired during create reference lookup.");
        return step;
    }

    private ProductListingCreateOutcomeVerificationView foundAfterConcurrentUpdate(
            ProductListingTaskRecord original,
            String partnerSku
    ) {
        ProductListingTaskRecord latest =
                mapper.selectTaskById(original.getId(), original.getOwnerUserId());
        ProductListingCreateOutcomeSupport.References references =
                support.references(latest == null
                        ? null : support.readNoonResult(latest.getNoonResultJson()));
        if (!references.complete()) {
            throw changedTask();
        }
        return found(original.getId(), partnerSku, references);
    }

    private ProductListingTaskRecord persistLookup(
            ProductListingTaskRecord original,
            ProductListingNoonWriteResult updated
    ) {
        String updatedJson = support.writeJson(updated);
        if (mapper.persistRecoveredCreateReference(
                original.getId(), original.getOwnerUserId(),
                original.getNoonResultJson(), updatedJson) == 1) {
            original.setNoonResultJson(updatedJson);
            return original;
        }
        ProductListingTaskRecord latest =
                mapper.selectTaskById(original.getId(), original.getOwnerUserId());
        if (latest == null) {
            throw new IllegalArgumentException("上架任务状态已变化，请刷新后重试。");
        }
        return latest;
    }

    private ProductListingCreateOutcomeVerificationView found(
            Long taskId,
            String partnerSku,
            ProductListingCreateOutcomeSupport.References references
    ) {
        return ProductListingCreateOutcomeVerificationView.found(
                taskId, partnerSku, references.skuParent(), references.pskuCode());
    }

    private IllegalArgumentException changedTask() {
        return new IllegalArgumentException(
                "The product listing task changed; reload the workflow before checking again.");
    }
}
