package com.nuono.next.productlisting;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductListingEmailOtpReauthenticationService {
    private final ProductListingEmailOtpRecoveryEnqueuer enqueuer;
    private final ProductListingEmailOtpRecoveryFinalizer finalizer;
    private final ProductListingReauthenticationAttemptProjector projector;

    public ProductListingEmailOtpReauthenticationService(
            ProductListingEmailOtpRecoveryEnqueuer enqueuer,
            ProductListingEmailOtpRecoveryFinalizer finalizer,
            ProductListingReauthenticationAttemptProjector projector
    ) {
        this.enqueuer = enqueuer;
        this.finalizer = finalizer;
        this.projector = projector;
    }

    public boolean applies(StoreSyncStoreRecord project) {
        return project != null
                && StringUtils.hasText(project.getNoonPartnerMailAuthCode());
    }

    public ProductListingWorkflowView enqueue(
            BusinessAccessContext context,
            ProductListingTaskView task,
            ProductListingWorkflowView workflow,
            StoreSyncStoreRecord project,
            StoreSyncStoreRecord site,
            ProductListingReauthenticationCommitter.ResumeAction resumeAction
    ) {
        enqueuer.enqueue(task, project, site, resumeAction);
        return projector.pending(workflow, null);
    }

    public ProductListingWorkflowView poll(
            BusinessAccessContext context,
            ProductListingTaskView task,
            ProductListingWorkflowView workflow
    ) {
        return finalizer.poll(context, task, workflow);
    }
}
