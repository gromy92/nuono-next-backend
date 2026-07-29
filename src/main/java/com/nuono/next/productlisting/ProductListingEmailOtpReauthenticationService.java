package com.nuono.next.productlisting;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ProductListingEmailOtpReauthenticationService {
    private final ProductListingEmailOtpRecoveryEnqueuer enqueuer;
    private final ProductListingEmailOtpRecoveryFinalizer finalizer;
    private final ProductListingReauthenticationAttemptProjector projector;
    private final ProductListingPersistedSessionReauthenticationService
            persistedSessionReauthenticationService;

    public ProductListingEmailOtpReauthenticationService(
            ProductListingEmailOtpRecoveryEnqueuer enqueuer,
            ProductListingEmailOtpRecoveryFinalizer finalizer,
            ProductListingReauthenticationAttemptProjector projector,
            ProductListingPersistedSessionReauthenticationService
                    persistedSessionReauthenticationService
    ) {
        this.enqueuer = enqueuer;
        this.finalizer = finalizer;
        this.projector = projector;
        this.persistedSessionReauthenticationService =
                persistedSessionReauthenticationService;
    }

    public boolean applies(StoreSyncStoreRecord project) {
        return enqueuer.applies(project);
    }

    public ProductListingWorkflowView enqueue(
            BusinessAccessContext context,
            ProductListingTaskView task,
            ProductListingWorkflowView workflow,
            StoreSyncStoreRecord project,
            StoreSyncStoreRecord site,
            ProductListingReauthenticationCommitter.ResumeAction resumeAction
    ) {
        Optional<ProductListingWorkflowView> resumed =
                persistedSessionReauthenticationService
                        .reauthenticateIfVerified(
                                context,
                                task,
                                project,
                                site,
                                resumeAction
                        );
        if (resumed.isPresent()) {
            return resumed.get();
        }
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
