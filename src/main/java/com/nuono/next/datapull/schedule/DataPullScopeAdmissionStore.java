package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.List;

/** Read-only Interface that closes every current source scope against immutable admission facts. */
public interface DataPullScopeAdmissionStore {

    List<AdmittedDataPullScope> admitCurrent(
            OperationCode operationCode,
            List<DataPullScope> activeSourceScopes
    );

    List<AdmittedDataPullScope> requireActiveAdmissions(
            OperationCode operationCode,
            List<DataPullScope> activeSourceScopes
    );

    static DataPullScopeAdmissionStore failClosed() {
        return new DataPullScopeAdmissionStore() {
            @Override
            public List<AdmittedDataPullScope> admitCurrent(
                    OperationCode operation,
                    List<DataPullScope> scopes
            ) {
                return fail(operation);
            }

            @Override
            public List<AdmittedDataPullScope> requireActiveAdmissions(
                    OperationCode operation,
                    List<DataPullScope> scopes
            ) {
                return fail(operation);
            }

            private List<AdmittedDataPullScope> fail(OperationCode operation) {
                throw new IllegalStateException(
                        "DP_SCOPE_ADMISSION_STORE_NOT_WIRED:" + operation
                );
            }
        };
    }
}
