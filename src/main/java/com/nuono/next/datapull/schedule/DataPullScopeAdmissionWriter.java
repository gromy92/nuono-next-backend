package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.DataPullScope;
import java.time.LocalDateTime;

/**
 * Transactional seam for future binding writers; scheduling never performs lazy admission.
 *
 * <p>{@code firstEligibleAtUtc} is the persisted business-binding effective instant. A caller must
 * never substitute scheduler observation time, because the value is the exclusive lower bound for
 * every operation that shares this global scope admission.</p>
 */
public interface DataPullScopeAdmissionWriter {

    DataPullScopeAdmission admitPostCutover(
            DataPullScope sourceScope,
            LocalDateTime firstEligibleAtUtc,
            String cutoverKey
    );
}
