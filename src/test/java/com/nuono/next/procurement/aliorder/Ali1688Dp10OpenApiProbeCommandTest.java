package com.nuono.next.procurement.aliorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
