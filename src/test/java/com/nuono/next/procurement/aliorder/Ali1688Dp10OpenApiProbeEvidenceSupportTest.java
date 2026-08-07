package com.nuono.next.procurement.aliorder;

import static com.nuono.next.procurement.aliorder.Ali1688Dp10OpenApiProbeEvidenceFixture.COMMIT;
import static com.nuono.next.procurement.aliorder.Ali1688Dp10OpenApiProbeEvidenceFixture.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Ali1688Dp10OpenApiProbeEvidenceSupportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesCreateNew0600EvidenceWithOnlySafeBoundFields() throws Exception {
        Ali1688Dp10OpenApiProbeEvidenceFixture fixture = fixture();

        fixture.write();

        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(fixture.evidence)
        );
        JsonNode json = new ObjectMapper().readTree(fixture.evidence.toFile());
        assertEquals(Ali1688Dp10OpenApiProbeEvidenceSupport.SCHEMA, json.get("schema").asText());
        assertEquals(
                Ali1688Dp10OpenApiProbeEvidenceSupport.EXECUTION_PROVEN,
                json.get("release_disposition").asText()
        );
        assertEquals(COMMIT, json.get("manifest_commit").asText());
        assertEquals(13, json.size());
        String serialized = json.toString().toLowerCase();
        for (String forbidden : Set.of(
                "token", "secret", "authorization_id", "order_no", "payload",
                "totalrecord", "count", "ready", "complete"
        )) {
            assertFalse(serialized.contains(forbidden), forbidden);
        }
        assertThrows(java.nio.file.FileAlreadyExistsException.class, fixture::write);
    }

    @Test
    void verifierBindsEvidenceFileCommitJarAndOpenApiConfiguration() throws Exception {
        Ali1688Dp10OpenApiProbeEvidenceFixture fixture = fixture();
        fixture.write();
        String evidenceSha = fixture.evidenceSha();

        assertTrue(fixture.verifyFresh(evidenceSha, COMMIT, NOW));
        assertTrue(fixture.verifyBound(evidenceSha, COMMIT, NOW));
        assertFalse(fixture.verifyFresh("0".repeat(64), COMMIT, NOW));
        assertFalse(fixture.verifyFresh(evidenceSha, "e".repeat(40), NOW));

        fixture.properties.setAppKey("changed-app-key");
        assertFalse(fixture.verifyBound(evidenceSha, COMMIT, NOW));
    }

    @Test
    void predecessorRollbackAcceptsOwnEvidenceButRejectsCrossJarReplay() throws Exception {
        Ali1688Dp10OpenApiProbeEvidenceFixture predecessor = fixture();
        predecessor.write();
        String evidenceSha = predecessor.evidenceSha();
        Path candidateJar = predecessor.evidence.getParent().resolve("candidate-other.jar");
        Files.write(candidateJar, "other-candidate".getBytes(StandardCharsets.UTF_8));

        assertTrue(predecessor.verifyBound(evidenceSha, COMMIT, NOW.plusSeconds(86_400)));
        assertFalse(Ali1688Dp10OpenApiProbeEvidenceSupport.verifyBound(
                predecessor.evidence,
                evidenceSha,
                COMMIT,
                candidateJar,
                predecessor.properties,
                Clock.fixed(NOW.plusSeconds(86_400), ZoneOffset.UTC),
                new ObjectMapper()
        ));
    }

    private Ali1688Dp10OpenApiProbeEvidenceFixture fixture() throws Exception {
        return Ali1688Dp10OpenApiProbeEvidenceFixture.create(temporaryDirectory);
    }
}
