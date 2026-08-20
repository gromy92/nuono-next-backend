package com.nuono.next.noon;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthTransientFailure;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.util.List;

final class NoonAuthAttemptResults {
    private NoonAuthAttemptResults() {
    }

    static NoonAuthRecoveryAttemptResult failed(
            NoonAuthRecoveryFailureCode code, String messageKeyHash, String diagnostic
    ) {
        return NoonAuthRecoveryAttemptResult.failed(code, messageKeyHash, diagnostic);
    }

    static NoonAuthRecoveryAttemptResult transientIdentityFailure(
            NoonAuthRecoveryFailureStage stage,
            NoonTransientErrorType type,
            String messageKeyHash
    ) {
        return transientIdentityFailures(List.of(transientFact(stage, type)), messageKeyHash);
    }

    static NoonAuthRecoveryAttemptResult transientIdentityFailures(
            List<NoonAuthTransientFailure> failures, String messageKeyHash
    ) {
        String diagnostic = failures.size() == 1
                ? failures.get(0).getSafeDiagnostic()
                : "multiple exact transient failures";
        return NoonAuthRecoveryAttemptResult.transientFailures(
                failures, messageKeyHash, diagnostic
        );
    }

    static NoonAuthTransientFailure transientFact(
            NoonAuthRecoveryFailureStage stage, NoonTransientErrorType type
    ) {
        return new NoonAuthTransientFailure(
                stage, type, stage.name() + ": transient " + type.name()
        );
    }
}
