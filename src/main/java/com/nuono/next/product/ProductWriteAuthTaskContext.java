package com.nuono.next.product;

import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthRetrySuppressedException;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import java.util.Optional;
import org.springframework.util.StringUtils;

/** Carries exact durable product-task identity across lower-level Noon adapter calls. */
final class ProductWriteAuthTaskContext {
    private final NoonAuthWaitQueue queue;
    private final ProjectResolver projectResolver;
    private final ThreadLocal<TaskIdentity> current = new ThreadLocal<>();

    ProductWriteAuthTaskContext(NoonAuthWaitQueue queue, ProjectResolver projectResolver) {
        this.queue = queue;
        this.projectResolver = projectResolver;
    }

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

    Optional<Long> enqueue(ProductPublishTaskRecord task, String checkpoint, boolean writeMayHaveOccurred) {
        if (task == null || task.getId() == null || queue == null) {
            return Optional.empty();
        }
        String projectCode = projectResolver.resolve(
                task.getOwnerUserId(), task.getProjectCode(), task.getStoreCode()
        );
        try {
            return enqueue(identity(
                    task.getOwnerUserId(), projectCode, task.getStoreCode(), task.getCurrentSiteCode(),
                    domain(task), task.getId(),
                    StringUtils.hasText(checkpoint) ? checkpoint : "AUTH_FAILURE", false
            ), projectCode, writeMayHaveOccurred);
        } catch (NoonAuthRetrySuppressedException suppressed) {
            throw suppressed;
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    Optional<Long> enqueue(
            TaskIdentity task,
            String canonicalProjectCode,
            boolean writeMayHaveOccurred
    ) {
        return queue.enqueue(NoonAuthWaitRequest.task(
                task.ownerUserId, canonicalProjectCode, task.storeCode, task.siteCode,
                task.sourceDomain, task.sourceTaskId, task.checkpoint,
                writeMayHaveOccurred
                        ? NoonAuthResumePolicy.READBACK_REQUIRED
                        : NoonAuthResumePolicy.AUTO_RESUME
        ));
    }

    private static String domain(ProductPublishTaskRecord task) {
        return "product-delete".equalsIgnoreCase(task.getTaskType())
                ? "PRODUCT_DELETE"
                : "PRODUCT_PUBLISH";
    }

    @FunctionalInterface
    interface ProjectResolver {
        String resolve(Long ownerUserId, String projectCode, String storeCode);
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
