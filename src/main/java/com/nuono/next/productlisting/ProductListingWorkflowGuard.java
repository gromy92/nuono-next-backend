package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
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

    ProductListingTaskRecord requireRecoveryTask(
            BusinessAccessContext context,
            Long taskId,
            ProductListingWorkflowView.NextAction action
    ) {
        if (context == null || context.getBusinessOwnerUserId() == null) {
            throw new IllegalArgumentException("Business access context is required.");
        }
        ProductListingTaskRecord task = mapper.selectTaskByIdForUpdate(
                taskId, context.getBusinessOwnerUserId());
        if (task == null) {
            throw new IllegalArgumentException("Product listing task not found.");
        }
        if (!context.canAccessStore(task.getStoreCode())) {
            throw new BusinessAccessDeniedException("当前账号不能操作该店铺。");
        }
        ProductListingWorkflowView workflow =
                projector.project(null, null, taskView(task));
        if (workflow.getNextAction() != action) {
            throw new IllegalArgumentException("当前任务证据不允许执行该恢复操作。");
        }
        return task;
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
