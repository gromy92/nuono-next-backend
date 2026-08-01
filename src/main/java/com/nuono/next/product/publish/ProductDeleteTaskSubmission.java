package com.nuono.next.product.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductPublishTaskRecord;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateCommand;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateResult;
import java.util.List;
import org.springframework.util.StringUtils;

public final class ProductDeleteTaskSubmission {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ProductManagementMapper mapper;
    private final ProductPublishCommandService commandService;

    public ProductDeleteTaskSubmission(
            ProductManagementMapper mapper,
            ProductPublishCommandService commandService
    ) {
        this.mapper = mapper;
        this.commandService = commandService;
    }

    public ProductDeleteTaskSubmissionResult submit(ProductPublishTaskCreateCommand command) {
        if (command == null || command.getProductMasterId() == null) {
            throw new IllegalArgumentException("商品删除任务缺少本地商品 ID。");
        }
        commandService.recoverStaleRunningTasks();
        ProductPublishTaskRecord latest = mapper.selectLatestProductPublishTask(command.getProductMasterId());
        if (isPlainDelete(latest)) {
            ProductDeleteTaskSubmissionResult reused = reuseLatestDelete(latest, command.getOwnerUserId());
            if (reused != null) {
                return reused;
            }
        } else if (isUnfinishedRebuild(latest)) {
            throw new IllegalStateException("当前商品已有重建任务待处理，不能同时创建删除任务。");
        } else if (latest != null && commandService.isActiveStatus(latest.getStatus())) {
            throw new IllegalStateException("当前商品已有后台任务正在执行，请等待完成后再删除商品。");
        }

        long boundaryTaskId = latest == null || latest.getId() == null ? 0L : latest.getId();
        command.setIdempotencyKey(
                "delete:" + command.getProductMasterId() + ":after:" + boundaryTaskId
        );
        ProductPublishTaskCreateResult created = commandService.createProductDeleteTask(command);
        if (!created.isDuplicate()) {
            return ProductDeleteTaskSubmissionResult.created(created.getTask());
        }
        if (!isPlainDelete(created.getTask())) {
            throw new IllegalStateException("当前商品已有后台任务正在执行，请等待完成后再删除商品。");
        }
        ProductDeleteTaskSubmissionResult reused = reuseLatestDelete(
                created.getTask(),
                command.getOwnerUserId()
        );
        return reused == null
                ? ProductDeleteTaskSubmissionResult.existing(created.getTask())
                : reused;
    }

    private ProductDeleteTaskSubmissionResult reuseLatestDelete(
            ProductPublishTaskRecord task,
            Long ownerUserId
    ) {
        if (task == null) {
            return null;
        }
        if (commandService.isActiveStatus(task.getStatus()) || "synced".equalsIgnoreCase(task.getStatus())) {
            return ProductDeleteTaskSubmissionResult.existing(task);
        }
        if (!"failed".equalsIgnoreCase(task.getStatus())
                && !"pending_manual_check".equalsIgnoreCase(task.getStatus())) {
            return null;
        }
        commandService.retryTask(task.getId(), ownerUserId, null, ignored -> List.of("delete"));
        ProductPublishTaskRecord refreshed = mapper.selectProductPublishTaskById(task.getId());
        return ProductDeleteTaskSubmissionResult.resumed(refreshed == null ? task : refreshed);
    }

    private boolean isPlainDelete(ProductPublishTaskRecord task) {
        return ProductPublishTaskClassifier.isProductDelete(task) && !isRebuild(task);
    }

    private boolean isUnfinishedRebuild(ProductPublishTaskRecord task) {
        return ProductPublishTaskClassifier.isProductDelete(task)
                && isRebuild(task)
                && !"synced".equalsIgnoreCase(task.getStatus())
                && !"cancelled".equalsIgnoreCase(task.getStatus());
    }

    private boolean isRebuild(ProductPublishTaskRecord task) {
        if (task == null || !StringUtils.hasText(task.getRequestJson())) {
            return false;
        }
        try {
            JsonNode request = OBJECT_MAPPER.readTree(task.getRequestJson());
            return request != null
                    && "product-rebuild".equalsIgnoreCase(request.path("rebuildAction").asText());
        } catch (Exception exception) {
            return false;
        }
    }
}
