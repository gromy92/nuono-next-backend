package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryTargetFailureTest
        extends AbstractNoonSessionGatewayAuthRecoveryTestSupport {

    @Test
    void catalogProxy407IsTransportBackoffInsteadOfCookieFailure() throws Exception {
        try (RecoveryServer server = new RecoveryServer(
                200,
                "{\"success\":true,\"access_token\":\"token-1\"}",
                "{\"ok\":true,\"email\":\"merchant@example.com\"}"
        )) {
            server.failCatalog(407);

            NoonAuthRecoveryAttemptResult result =
                    recoveryGateway(identityGateway(server)).attempt(command());

            assertTrue(result.isIdentityAuthenticated());
            NoonAuthRecoveryProjectResult projectResult = result.getProjectResults().get(0);
            assertTrue(projectResult.isTransientFailure());
            assertEquals(
                    NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                    projectResult.getFailureStage()
            );
            assertEquals(NoonTransientErrorType.HTTP_407, projectResult.getTransientErrorType());
            assertEquals(1, server.generateCount());
            assertEquals(1, server.sessionCreateCount());
            assertTrue(server.catalogCount() >= 1);
        }
    }

    @Test
    void invalidProjectAsStoreTargetStopsBeforeProjectSessionCall() throws Exception {
        try (RecoveryServer server = RecoveryServer.forProjects(List.of(TARGET_PROJECT))) {
            NoonAuthRecoveryAttemptResult result = recoveryGateway(identityGateway(server)).attempt(command(
                    List.of(new NoonAuthRecoveryProjectTarget(
                            307L, TARGET_PROJECT, TARGET_PROJECT, "AE", 0L
                    )),
                    () -> true
            ));

            assertTrue(result.isIdentityAuthenticated());
            NoonAuthRecoveryProjectResult projectResult = result.getProjectResults().get(0);
            assertEquals(
                    NoonAuthRecoveryProjectResult.Code.PROJECT_TARGET_INVALID,
                    projectResult.getCode()
            );
            assertEquals(0, server.sessionCreateCount());
            assertEquals(0, server.whoamiCount());
            assertEquals(0, server.catalogCount());
        }
    }
}
