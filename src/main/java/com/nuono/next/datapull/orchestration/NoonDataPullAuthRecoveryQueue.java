package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthRetrySuppressedException;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;

/** Connects Noon DP auth waits to the existing single shared-identity OTP queue. */
public final class NoonDataPullAuthRecoveryQueue implements DataPullAuthRecoveryQueue {
    static final String SOURCE_DOMAIN = "DP_RUNTIME";

    private static final EnumSet<OperationCode> NOON_OPERATIONS = EnumSet.of(
            OperationCode.DP01,
            OperationCode.DP02,
            OperationCode.DP03,
            OperationCode.DP04,
            OperationCode.DP05,
            OperationCode.DP06,
            OperationCode.DP07A,
            OperationCode.DP07B,
            OperationCode.DP08A,
            OperationCode.DP08B
    );

    private final NoonAuthWaitQueue authWaitQueue;

    public NoonDataPullAuthRecoveryQueue(NoonAuthWaitQueue authWaitQueue) {
        this.authWaitQueue = Objects.requireNonNull(authWaitQueue, "authWaitQueue");
    }

    @Override
    public void enqueue(
            DataPullTask task,
            long waitingTaskVersion,
            LocalDateTime committedAtUtc
    ) {
        DataPullTask value = Objects.requireNonNull(task, "task");
        LocalDateTime nowUtc = Objects.requireNonNull(committedAtUtc, "committedAtUtc");
        if (!NOON_OPERATIONS.contains(value.getOperationCode())) {
            return;
        }
        if (value.getVersion() == null || waitingTaskVersion != value.getVersion() + 1L) {
            throw new IllegalArgumentException("waitingTaskVersion must follow the committed task version");
        }
        NoonAuthWaitRequest request = NoonAuthWaitRequest.task(
                value.getOwnerUserId(),
                value.getProjectCode(),
                value.getStoreCode(),
                value.getSiteCode(),
                SOURCE_DOMAIN,
                value.getId(),
                Long.toString(waitingTaskVersion),
                NoonAuthResumePolicy.AUTO_RESUME,
                value.getUpdatedAt()
        );
        try {
            authWaitQueue.enqueue(request);
        } catch (NoonAuthRetrySuppressedException suppressed) {
            // The durable WAITING_AUTH retry remains authoritative. Suppressing a duplicate OTP
            // attempt must not turn a temporary shared-auth condition into a terminal DP result.
        }
    }
}
