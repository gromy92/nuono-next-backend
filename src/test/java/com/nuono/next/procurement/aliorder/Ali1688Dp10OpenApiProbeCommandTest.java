package com.nuono.next.procurement.aliorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class Ali1688Dp10OpenApiProbeCommandTest {
    @Test
    void acceptsOnlyTheExactCandidateBoundArguments() {
        String[] args = validArgs();

        Map<String, String> parsed = Ali1688Dp10OpenApiProbeCommand.parse(args);

        assertEquals("/candidate.jar", parsed.get("--candidate-jar"));
        assertEquals("c".repeat(40), parsed.get("--manifest-commit"));
    }

    @Test
    void rejectsUnknownSkipVerifiedProviderResultAndEvidenceInputArguments() {
        for (String forbidden : new String[]{
                "--skip", "--verified", "--provider-result", "--evidence-input"
        }) {
            String[] args = validArgs();
            args[1] = forbidden;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Ali1688Dp10OpenApiProbeCommand.parse(args),
                    forbidden
            );
        }
    }

    @Test
    void rejectsDuplicateOrMissingBindings() {
        String[] duplicate = validArgs();
        duplicate[9] = "--env-file";

        assertThrows(
                IllegalArgumentException.class,
                () -> Ali1688Dp10OpenApiProbeCommand.parse(duplicate)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Ali1688Dp10OpenApiProbeCommand.parse(new String[]{
                        Ali1688Dp10OpenApiProbeCommand.COMMAND,
                        "--env-file", "/app/.env"
                })
        );
    }

    @Test
    void onlyPreExecutionAuthorizationFailuresCanBecomeIsolatedAuthWaitEvidence() {
        assertTrue(Ali1688Dp10OpenApiProbeCommand.isIsolatedAuthWait(
                "PROBE_AUTH_REFRESH_REQUIRED"
        ));
        assertTrue(Ali1688Dp10OpenApiProbeCommand.isIsolatedAuthWait(
                "PROBE_AUTH_REFRESH_UNPROVEN"
        ));
        assertTrue(Ali1688Dp10OpenApiProbeCommand.isIsolatedAuthWait(
                "PROBE_AUTH_REFRESH_RISK_CONTROL"
        ));
        assertTrue(Ali1688Dp10OpenApiProbeCommand.isIsolatedAuthWait(
                "PROBE_AUTH_REFRESH_RATE_LIMITED"
        ));
        assertTrue(Ali1688Dp10OpenApiProbeCommand.isIsolatedAuthWait(
                "PROBE_AUTH_REFRESH_RETRYABLE"
        ));
        assertFalse(Ali1688Dp10OpenApiProbeCommand.isIsolatedAuthWait(
                "PROBE_CURRENT_LIST_CONTRACT_UNPROVEN"
        ));
        assertFalse(Ali1688Dp10OpenApiProbeCommand.isIsolatedAuthWait(
                "PROBE_DETAIL_CONTRACT_UNPROVEN"
        ));
        assertFalse(Ali1688Dp10OpenApiProbeCommand.isIsolatedAuthWait(
                "PROBE_EXECUTION_FAILED"
        ));
    }

    private String[] validArgs() {
        return new String[]{
                Ali1688Dp10OpenApiProbeCommand.COMMAND,
                "--env-file", "/app/.env",
                "--candidate-jar", "/candidate.jar",
                "--manifest-commit", "c".repeat(40),
                "--expected-jar-sha256", "a".repeat(64),
                "--evidence-file", "/evidence.json"
        };
    }
}
