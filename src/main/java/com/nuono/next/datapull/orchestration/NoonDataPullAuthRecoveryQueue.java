package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;

/** Stops the current Noon DP task and raises one shared manual-login requirement. */
public final class NoonDataPullAuthRecoveryQueue implements DataPullAuthRecoveryQueue {
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

    private final NoonAccountSessionAttentionPort accountSessionAttention;

    public NoonDataPullAuthRecoveryQueue(NoonAccountSessionAttentionPort accountSessionAttention) {
        this.accountSessionAttention = Objects.requireNonNull(
                accountSessionAttention, "accountSessionAttention"
        );
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
        accountSessionAttention.requireManualLogin();
    }
}
