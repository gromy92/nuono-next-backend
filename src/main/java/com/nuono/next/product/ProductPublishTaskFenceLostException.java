package com.nuono.next.product;

public class ProductPublishTaskFenceLostException extends IllegalStateException {

    private final Long taskId;
    private final String targetStatus;

    public ProductPublishTaskFenceLostException(ProductPublishTaskRecord task, String targetStatus) {
        super("发布任务锁已失效，跳过旧 worker 写回。taskId="
                + (task == null ? null : task.getId())
                + ", currentStatus=" + (task == null ? null : task.getStatus())
                + ", targetStatus=" + targetStatus);
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
