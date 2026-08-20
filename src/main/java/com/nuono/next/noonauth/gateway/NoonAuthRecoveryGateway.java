package com.nuono.next.noonauth.gateway;

public interface NoonAuthRecoveryGateway {

    NoonAuthRecoveryAttemptResult attempt(NoonAuthRecoveryAttemptCommand command);

    default boolean canResume(long recoveryId) {
        return false;
    }

    default boolean requiresCheckpointSecret() {
        return false;
    }

    default void clearCheckpoint(long recoveryId) {
    }
}
