package com.nuono.next.noon;

import static com.nuono.next.noon.NoonAuthRecoveryFailureClassifier.safeDiagnostic;
import static com.nuono.next.noon.NoonAuthRecoveryFailureClassifier.throwableMessage;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand.LeaseLostException;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult.Code;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class NoonProjectSessionRecovery {
    private final NoonSessionGateway sessionGateway;

    NoonProjectSessionRecovery(NoonSessionGateway sessionGateway) {
        this.sessionGateway = sessionGateway;
    }

    List<NoonAuthRecoveryProjectResult> recover(
            NoonSessionGateway.EmailIdentityGrant grant,
            String expectedEmail,
            List<NoonAuthRecoveryProjectTarget> targets,
            NoonAuthRecoveryAttemptCommand command
    ) {
        List<NoonAuthRecoveryProjectResult> results = new ArrayList<>();
        for (NoonAuthRecoveryProjectTarget target : targets) {
            results.add(recoverProject(grant, target, expectedEmail, command));
        }
        command.heartbeatOrThrow();
        return results;
    }

    private NoonAuthRecoveryProjectResult recoverProject(
            NoonSessionGateway.EmailIdentityGrant grant,
            NoonAuthRecoveryProjectTarget target,
            String expectedEmail,
            NoonAuthRecoveryAttemptCommand command
    ) {
        if (!target.hasCompleteBusinessIdentity()) {
            return NoonAuthRecoveryProjectResult.invalidTarget(target);
        }
        command.heartbeatOrThrow();
        final NoonSessionGateway.ProjectSessionCookie projectSession;
        try {
            projectSession = sessionGateway.createEmailOtpProjectSession(
                    grant, target.getProjectCode(), target.getStoreCode()
            );
        } catch (LeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            command.heartbeatOrThrow();
            String message = throwableMessage(exception).toLowerCase(Locale.ROOT);
            if (message.contains("不包含当前项目") || message.contains("does not contain")) {
                return NoonAuthRecoveryProjectResult.failed(
                        target, Code.PROJECT_ACCESS_DENIED, safeDiagnostic("project session", exception)
                );
            }
            Optional<NoonTransientErrorType> transientType =
                    NoonProjectTransientFailureClassifier.classify(exception);
            return transientType.isPresent()
                    ? transientFailure(target, NoonAuthRecoveryFailureStage.PROJECT_SESSION_CREATE,
                            transientType.get())
                    : NoonAuthRecoveryProjectResult.failed(
                            target, Code.SESSION_CREATE_FAILED, safeDiagnostic("project session", exception)
                    );
        }
        command.heartbeatOrThrow();

        final JsonNode whoami;
        try {
            whoami = sessionGateway.whoamiWithProjectSession(projectSession, target.getStoreCode());
            command.heartbeatOrThrow();
        } catch (LeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            command.heartbeatOrThrow();
            return validationFailure(
                    target, NoonAuthRecoveryFailureStage.WHOAMI_VALIDATION,
                    "whoami validation", exception
            );
        }
        if (!NoonProjectSessionValidator.validatesProjectSession(
                whoami, expectedEmail, target.getProjectCode(), projectSession
        )) {
            return NoonAuthRecoveryProjectResult.failed(
                    target, Code.COOKIE_VALIDATION_FAILED,
                    "project cookie validation: identity or target project not confirmed"
            );
        }

        try {
            sessionGateway.validateCatalogProjectSession(projectSession, target.getStoreCode());
            command.heartbeatOrThrow();
            return NoonAuthRecoveryProjectResult.recovered(
                    target, projectSession.getCookie(), grant.getUserCode()
            );
        } catch (LeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            command.heartbeatOrThrow();
            return validationFailure(
                    target, NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                    "catalog validation", exception
            );
        }
    }

    private NoonAuthRecoveryProjectResult validationFailure(
            NoonAuthRecoveryProjectTarget target,
            NoonAuthRecoveryFailureStage failureStage,
            String operation,
            RuntimeException exception
    ) {
        Optional<NoonTransientErrorType> transientType =
                NoonProjectTransientFailureClassifier.classify(exception);
        return transientType.isPresent()
                ? transientFailure(target, failureStage, transientType.get())
                : NoonAuthRecoveryProjectResult.failed(
                        target, Code.COOKIE_VALIDATION_FAILED, safeDiagnostic(operation, exception)
                );
    }

    private NoonAuthRecoveryProjectResult transientFailure(
            NoonAuthRecoveryProjectTarget target,
            NoonAuthRecoveryFailureStage failureStage,
            NoonTransientErrorType transientType
    ) {
        return NoonAuthRecoveryProjectResult.transientFailure(
                target, failureStage, transientType,
                failureStage.name() + ": transient " + transientType.name()
        );
    }
}
