package com.nuono.next.noonauth;

import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.LocalDateTime;
import java.util.List;

public interface NoonAuthTransientBackoffRepository {

    Long resolveLogicalStoreId(Long ownerUserId, String projectCode);

    NoonAuthTransientBackoffState incrementFailure(
            NoonAuthTransientBackoffState failure,
            NoonAuthTransientBackoffWriteFence fence,
            LocalDateTime now
    );

    NoonAuthTransientBackoffState selectState(
            Long logicalStoreId,
            NoonTransientErrorType errorType
    );

    List<NoonAuthTransientBackoffState> listActiveHolds(
            Long logicalStoreId,
            LocalDateTime now
    );

    boolean hasFailureForRecovery(Long logicalStoreId, Long recoveryId);

    boolean resetForRecovery(
            Long logicalStoreId,
            Long recoveryId,
            NoonAuthTransientBackoffWriteFence fence,
            LocalDateTime resetAt
    );
}
