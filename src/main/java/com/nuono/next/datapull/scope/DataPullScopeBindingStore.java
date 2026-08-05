package com.nuono.next.datapull.scope;

import com.nuono.next.datapull.runtime.OperationCode;
import java.util.List;

/** Transactional seam that turns one complete live cohort into immutable temporal epochs. */
public interface DataPullScopeBindingStore {
    List<DataPullScopeBindingEpoch> reconcileCurrent(
            OperationCode operationCode,
            List<DataPullScopeBindingCandidate> currentBindings
    );
}
