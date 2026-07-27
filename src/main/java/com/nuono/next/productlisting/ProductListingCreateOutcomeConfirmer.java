package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

final class ProductListingCreateOutcomeConfirmer {

    private final ProductListingMapper mapper;
    private final ProductListingService listingService;
    private final ProductListingCreateOutcomeSupport support;

    ProductListingCreateOutcomeConfirmer(
            ProductListingMapper mapper,
            ProductListingService listingService,
            ProductListingCreateOutcomeSupport support
    ) {
        this.mapper = mapper;
        this.listingService = listingService;
        this.support = support;
    }

    Long confirmNotCreated(BusinessAccessContext context, Long realRunTaskId) {
        ProductListingTaskView authorized =
                listingService.loadTask(context, realRunTaskId);
        ProductListingCreateOutcomeValidation.requireVerifiable(authorized);
        ProductListingTaskRecord task = mapper.selectTaskByIdForUpdate(
                realRunTaskId, authorized.getOwnerUserId());
        ProductListingCreateOutcomeValidation.requireLatestVerifiable(authorized, task);
        ProductListingNoonWriteResult previous =
                support.readNoonResult(task.getNoonResultJson());
        List<LocalDateTime> reliableChecks =
                support.reliableNotFoundChecks(previous);
        if (!support.canConfirmNotCreated(task, reliableChecks)) {
            throw new IllegalArgumentException(
                    "创建结果尚未达到安全确认条件，请稍后继续执行只读核对。");
        }

        ProductListingNoonWriteStepResult confirmation =
                new ProductListingNoonWriteStepResult();
        confirmation.setStepKey("confirm_create_not_found");
        confirmation.setStatus("succeeded");
        confirmation.setExternalReference(
                "lookupAttempts=" + reliableChecks.size()
                        + ";firstLookupAt=" + reliableChecks.get(0)
                        + ";lastLookupAt="
                        + reliableChecks.get(reliableChecks.size() - 1));
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        if (previous != null && previous.getSteps() != null) {
            steps.addAll(previous.getSteps());
        }
        steps.add(confirmation);
        String message = "已基于多次只读核对确认 Noon 未创建商品；"
                + "本次真实上架尝试已关闭，可返回修改草稿。";
        ProductListingNoonWriteResult confirmed =
                ProductListingNoonWriteResult.failed(
                        "noon_pre_create", "noon_create_not_found_confirmed",
                        message, steps);
        task.setNoonResultJson(support.writeJson(confirmed));
        task.setStatus("failed");
        task.setFailureCategory("noon_pre_create");
        task.setFailureCode("noon_create_not_found_confirmed");
        task.setFailureMessage(message);
        task.setCompletedAt(LocalDateTime.now());
        if (mapper.updateTaskResult(task) != 1) {
            throw new IllegalArgumentException("上架任务状态已变化，请刷新后重试。");
        }
        if (task.getSourceTaskId() == null
                || mapper.markValidatedDryRunSuperseded(
                task.getSourceTaskId(), task.getOwnerUserId()) != 1) {
            throw new IllegalArgumentException(
                    "原上架检查无法安全关闭，请刷新后重试。");
        }
        return task.getDraftId();
    }
}
