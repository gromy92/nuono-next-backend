package com.nuono.next.product.publish;

import com.nuono.next.product.ProductPublishTaskRecord;

public class ProductPublishTaskFenceLostException extends IllegalStateException {
    private final Long taskId;
    private final String targetStatus;

    public ProductPublishTaskFenceLostException(ProductPublishTaskRecord task, String targetStatus) {
        super("发布任务状态已被其他进程更新，任务 ID=" + (task == null ? null : task.getId())
                + "，目标状态=" + targetStatus);
        this.taskId = task == null ? null : task.getId();
        this.targetStatus = targetStatus;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getTargetStatus() {
        return targetStatus;
    }
}
