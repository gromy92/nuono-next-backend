package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import java.util.Objects;
import org.springframework.util.StringUtils;

final class ProductListingWorkflowGuard {

    private final ProductListingMapper mapper;
    private final ObjectMapper objectMapper;
    private final ProductListingWorkflowProjector projector =
            new ProductListingWorkflowProjector();

    ProductListingWorkflowGuard(
            ProductListingMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    ProductListingDraftRecord lockDraftIfPresent(Long draftId, Long ownerUserId) {
        return draftId == null
                ? null
                : mapper.selectDraftByIdForUpdate(draftId, ownerUserId);
    }

    ProductListingDraftRecord requireLockedDraft(Long draftId, Long ownerUserId) {
        if (draftId == null) {
            throw new IllegalArgumentException("Product listing draft ID is required.");
        }
        ProductListingDraftRecord draft = lockDraftIfPresent(draftId, ownerUserId);
        if (draft == null) {
            throw new IllegalArgumentException("Product listing draft not found.");
        }
        return draft;
    }

    ProductListingDraftRecord requireAccessibleDraft(
            BusinessAccessContext context,
            Long draftId,
            boolean forUpdate
    ) {
        requireContext(context);
        if (draftId == null) {
            throw new IllegalArgumentException(
                    "Product listing draft ID is required.");
        }
        for (Long ownerUserId :
                ProductListingOwnerScope.accessible(context)) {
            ProductListingDraftRecord draft = forUpdate
                    ? mapper.selectDraftByIdForUpdate(draftId, ownerUserId)
                    : mapper.selectDraftById(draftId, ownerUserId);
            if (draft == null) {
                continue;
            }
            requireRecordScope(
                    context,
                    draft.getOwnerUserId(),
                    draft.getStoreCode(),
                    "Product listing draft not found."
            );
            return draft;
        }
        throw new IllegalArgumentException("Product listing draft not found.");
    }

    ProductListingTaskRecord requireAccessibleTask(
            BusinessAccessContext context,
            Long taskId,
            boolean forUpdate
    ) {
        requireContext(context);
        if (taskId == null) {
            throw new IllegalArgumentException(
                    "Product listing task ID is required.");
        }
        for (Long ownerUserId :
                ProductListingOwnerScope.accessible(context)) {
            ProductListingTaskRecord task = forUpdate
                    ? mapper.selectTaskByIdForUpdate(taskId, ownerUserId)
                    : mapper.selectTaskById(taskId, ownerUserId);
            if (task == null) {
                continue;
            }
            requireRecordScope(
                    context,
                    task.getOwnerUserId(),
                    task.getStoreCode(),
                    "Product listing task not found."
            );
            return task;
        }
        throw new IllegalArgumentException("Product listing task not found.");
    }

    void requireRecordScope(
            BusinessAccessContext context,
            Long ownerUserId,
            String storeCode,
            String notFoundMessage
    ) {
        requireContext(context);
        if (!context.canAccessStore(storeCode)) {
            throw new BusinessAccessDeniedException(
                    "当前账号不能操作该店铺。");
        }
        if (!context.canAccessOwner(ownerUserId)
                || !Objects.equals(
                        ProductListingOwnerScope.resolve(context, storeCode),
                        ownerUserId)) {
            throw new IllegalArgumentException(notFoundMessage);
        }
    }

    ProductListingTaskRecord requireRecoveryTask(
            BusinessAccessContext context,
            Long taskId,
            ProductListingWorkflowView.NextAction action
    ) {
        ProductListingTaskRecord task =
                requireAccessibleTask(context, taskId, true);
        ProductListingWorkflowView workflow =
                projector.project(null, null, taskView(task));
        if (workflow.getNextAction() != action) {
            throw new IllegalArgumentException("当前任务证据不允许执行该恢复操作。");
        }
        return task;
    }

    private void requireContext(BusinessAccessContext context) {
        if (context == null
                || context.getBusinessOwnerUserId() == null) {
            throw new IllegalArgumentException(
                    "Business access context is required.");
        }
    }

    private ProductListingTaskView taskView(ProductListingTaskRecord task) {
        ProductListingTaskView view = new ProductListingTaskView();
        view.setTaskId(task.getId());
        view.setDraftId(task.getDraftId());
        view.setOwnerUserId(task.getOwnerUserId());
        view.setStoreCode(task.getStoreCode());
        view.setMode(task.getMode());
        view.setStatus(task.getStatus());
        view.setSourceTaskId(task.getSourceTaskId());
        view.setFailureCategory(task.getFailureCategory());
        view.setFailureCode(task.getFailureCode());
        view.setFailureMessage(task.getFailureMessage());
        if (StringUtils.hasText(task.getNoonResultJson())) {
            try {
                view.setNoonResult(objectMapper.readValue(
                        task.getNoonResultJson(), ProductListingNoonWriteResult.class));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException(
                        "Failed to parse product listing Noon result.", exception);
            }
        }
        return view;
    }
}
