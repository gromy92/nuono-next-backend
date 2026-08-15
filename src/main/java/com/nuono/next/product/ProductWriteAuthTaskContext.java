package com.nuono.next.product;

/** Carries write-outcome safety context across lower-level Noon adapter calls. */
final class ProductWriteAuthTaskContext {
    private final ThreadLocal<TaskIdentity> current = new ThreadLocal<>();

    TaskIdentity current() {
        return current.get();
    }

    TaskIdentity identity(
            Long ownerUserId, String projectCode, String storeCode, String siteCode,
            String sourceDomain, Long sourceTaskId, String checkpoint, boolean forceReadback
    ) {
        return new TaskIdentity(
                ownerUserId, projectCode, storeCode, siteCode,
                sourceDomain, sourceTaskId, checkpoint, forceReadback
        );
    }

    ProductWriteAuthRecovery.TaskScope open(ProductPublishTaskRecord task) {
        if (task == null) {
            return open((TaskIdentity) null);
        }
        return open(identity(
                task.getOwnerUserId(), task.getProjectCode(), task.getStoreCode(),
                task.getCurrentSiteCode(), domain(task), task.getId(), "PROVIDER_CALL", false
        ));
    }

    ProductWriteAuthRecovery.TaskScope open(
            Long ownerUserId, String projectCode, String storeCode, String siteCode,
            String sourceDomain, Long sourceTaskId, String checkpoint, boolean forceReadback
    ) {
        return open(identity(
                ownerUserId, projectCode, storeCode, siteCode,
                sourceDomain, sourceTaskId, checkpoint, forceReadback
        ));
    }

    private static String domain(ProductPublishTaskRecord task) {
        return "product-delete".equalsIgnoreCase(task.getTaskType())
                ? "PRODUCT_DELETE"
                : "PRODUCT_PUBLISH";
    }

    private ProductWriteAuthRecovery.TaskScope open(TaskIdentity task) {
        TaskIdentity previous = current.get();
        if (task == null) {
            current.remove();
        } else {
            current.set(task);
        }
        return () -> {
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
        };
    }

    static final class TaskIdentity {
        final Long ownerUserId;
        final String projectCode;
        final String storeCode;
        final String siteCode;
        final String sourceDomain;
        final Long sourceTaskId;
        final String checkpoint;
        final boolean forceReadback;

        private TaskIdentity(
                Long ownerUserId, String projectCode, String storeCode, String siteCode,
                String sourceDomain, Long sourceTaskId, String checkpoint, boolean forceReadback
        ) {
            this.ownerUserId = ownerUserId;
            this.projectCode = projectCode;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.sourceDomain = sourceDomain;
            this.sourceTaskId = sourceTaskId;
            this.checkpoint = checkpoint;
            this.forceReadback = forceReadback;
        }
    }
}
