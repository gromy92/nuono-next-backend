package com.nuono.next.product.publish;

import com.nuono.next.product.ProductPublishTaskRecord;

public final class ProductDeleteTaskSubmissionResult {
    public enum Disposition {
        CREATED,
        RESUMED,
        EXISTING
    }

    private final ProductPublishTaskRecord task;
    private final Disposition disposition;

    private ProductDeleteTaskSubmissionResult(
            ProductPublishTaskRecord task,
            Disposition disposition
    ) {
        this.task = task;
        this.disposition = disposition;
    }

    public static ProductDeleteTaskSubmissionResult created(ProductPublishTaskRecord task) {
        return new ProductDeleteTaskSubmissionResult(task, Disposition.CREATED);
    }

    public static ProductDeleteTaskSubmissionResult resumed(ProductPublishTaskRecord task) {
        return new ProductDeleteTaskSubmissionResult(task, Disposition.RESUMED);
    }

    public static ProductDeleteTaskSubmissionResult existing(ProductPublishTaskRecord task) {
        return new ProductDeleteTaskSubmissionResult(task, Disposition.EXISTING);
    }

    public ProductPublishTaskRecord getTask() {
        return task;
    }

    public Disposition getDisposition() {
        return disposition;
    }

    public String getMessage() {
        if (disposition == Disposition.RESUMED) {
            return "商品删除已继续后台处理，将从原任务的安全检查点恢复。";
        }
        if (disposition == Disposition.EXISTING) {
            return "商品删除正在后台处理，无需重复提交。";
        }
        return "商品删除已提交后台处理，请在发布状态和历史中查看进度。";
    }
}
