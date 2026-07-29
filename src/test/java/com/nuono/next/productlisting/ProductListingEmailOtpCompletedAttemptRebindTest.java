package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductListingReauthenticationAttemptMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductListingEmailOtpCompletedAttemptRebindTest {

    @Test
    void completedAttemptCanBindToANewerRecoveryForTheSameTask() {
        ProductListingReauthenticationAttemptMapper mapper =
                mock(ProductListingReauthenticationAttemptMapper.class);
        NoonProjectAuthRecoveryQueue queue =
                mock(NoonProjectAuthRecoveryQueue.class);
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        NoonAuthRecoveryProperties properties =
                new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setTrustedSenderDomains("noon.partners");
        ProductListingEmailOtpRecoveryEnqueuer enqueuer =
                new ProductListingEmailOtpRecoveryEnqueuer(
                        mapper,
                        queue,
                        properties,
                        sessionGateway
                );
        ProductListingReauthenticationAttemptRecord completed =
                completedAttempt();
        when(mapper.selectAttemptForUpdate(20002L, 10002L))
                .thenReturn(completed);
        when(sessionGateway.configuredMerchantEmail())
                .thenReturn("shared@example.com");
        when(queue.enqueueProject(
                10002L,
                "PRJ245027",
                "STR245027-NAE"
        )).thenReturn(Optional.of(91L));
        when(mapper.selectSourceLessRecoveryItem(
                91L,
                10002L,
                "PRJ245027"
        )).thenReturn(recoveryItem());
        when(mapper.rebindTerminalAttemptCas(
                any(),
                org.mockito.ArgumentMatchers.eq(77L),
                org.mockito.ArgumentMatchers.eq(401L),
                org.mockito.ArgumentMatchers.eq(2L)
        )).thenReturn(1);

        enqueuer.enqueue(
                task(),
                project(),
                site(),
                ProductListingReauthenticationCommitter.ResumeAction
                        .REOPEN_REVIEW
        );

        verify(mapper).rebindTerminalAttemptCas(
                org.mockito.ArgumentMatchers.argThat(replacement ->
                        replacement.getRecoveryId().equals(91L)
                                && replacement.getRequestedAuthVersion()
                                .equals(4L)
                                && "PENDING".equals(
                                replacement.getStatus()
                        )),
                org.mockito.ArgumentMatchers.eq(77L),
                org.mockito.ArgumentMatchers.eq(401L),
                org.mockito.ArgumentMatchers.eq(2L)
        );
    }

    private ProductListingReauthenticationAttemptRecord completedAttempt() {
        ProductListingReauthenticationAttemptRecord attempt =
                new ProductListingReauthenticationAttemptRecord();
        attempt.setRealRunTaskId(20002L);
        attempt.setOwnerUserId(10002L);
        attempt.setDraftId(10001L);
        attempt.setProjectId(7000L);
        attempt.setProjectCode("PRJ245027");
        attempt.setStoreCode("STR245027-NAE");
        attempt.setRecoveryId(77L);
        attempt.setRecoveryItemId(401L);
        attempt.setRequestedAuthVersion(2L);
        attempt.setResumeAction("REOPEN_REVIEW");
        attempt.setStatus("COMPLETED");
        attempt.setVersionNo(2L);
        return attempt;
    }

    private ProductListingReauthenticationAttemptRecord recoveryItem() {
        ProductListingReauthenticationAttemptRecord item =
                new ProductListingReauthenticationAttemptRecord();
        item.setRecoveryId(91L);
        item.setRecoveryItemId(501L);
        item.setRequestedAuthVersion(4L);
        return item;
    }

    private ProductListingTaskView task() {
        ProductListingTaskView task = new ProductListingTaskView();
        task.setTaskId(20002L);
        task.setSourceTaskId(20001L);
        task.setDraftId(10001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        task.setMode("REAL_RUN");
        task.setStatus("failed");
        task.setFailureCode("noon_auth_required");
        return task;
    }

    private StoreSyncStoreRecord project() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setId(7000L);
        project.setProjectCode("PRJ245027");
        return project;
    }

    private StoreSyncStoreRecord site() {
        StoreSyncStoreRecord site = new StoreSyncStoreRecord();
        site.setProjectCode("PRJ245027");
        site.setStoreCode("STR245027-NAE");
        site.setSite("AE");
        return site;
    }
}
