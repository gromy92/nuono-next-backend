package com.nuono.next.noonauth.gateway;

public interface NoonAuthRecoveryGateway {

    NoonAuthRecoveryAttemptResult attempt(NoonAuthRecoveryAttemptCommand command);
}
