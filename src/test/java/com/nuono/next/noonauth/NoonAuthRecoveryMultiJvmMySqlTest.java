package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the production claim and send-intent fences from separate JVM processes. */
class NoonAuthRecoveryMultiJvmMySqlTest {
    private Connection connection;
    private long recoveryId;

    @BeforeEach
    void prepare() throws Exception {
        String url = System.getenv("NUONO_NOON_AUTH_MYSQL_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        connection = DriverManager.getConnection(
                url,
                System.getenv("NUONO_NOON_AUTH_MYSQL_USERNAME"),
                System.getenv("NUONO_NOON_AUTH_MYSQL_PASSWORD")
        );
        String identity = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO noon_auth_identity_recovery ("
                        + "identity_key,status,generation_no,send_budget_epoch,send_attempt_count,"
                        + "coalesce_until,next_attempt_at,version_no,config_fingerprint,requested_at,"
                        + "gmt_create,gmt_updated) VALUES "
                        + "(?,'COALESCING',0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),0,?,"
                        + "UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, identity);
            statement.setString(2, "f".repeat(64));
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                recoveryId = keys.getLong(1);
            }
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (connection == null) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "DELETE FROM noon_auth_recovery_checkpoint WHERE recovery_id=" + recoveryId
            );
            statement.executeUpdate(
                    "DELETE FROM noon_auth_identity_send_ledger WHERE recovery_id=" + recoveryId
            );
            statement.executeUpdate(
                    "DELETE FROM noon_auth_identity_recovery WHERE id=" + recoveryId
            );
        } finally {
            connection.close();
        }
    }

    @Test
    void crashedLeaseOwnerIsTakenOverAndSendIntentRemainsSingle() throws Exception {
        List<ProbeResult> firstClaims = race("claim", 0L, "claim-a", "claim-b");
        assertSingleWinner(firstClaims);
        assertState("1:0:0:0");

        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE noon_auth_identity_recovery "
                        + "SET lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?"
        )) {
            statement.setLong(1, recoveryId);
            assertEquals(1, statement.executeUpdate());
        }

        List<ProbeResult> takeover = race("claim", 1L, "takeover-a", "takeover-b");
        ProbeResult leaseWinner = assertSingleWinner(takeover);
        assertState("2:0:0:0");

        List<ProbeResult> sends = race(
                "send", 2L, leaseWinner.token(), leaseWinner.token()
        );
        assertSingleWinner(sends);
        assertState("3:1:1:1");

        Path replayBarrier = Files.createTempFile("noon-auth-replay-", ".ready");
        try {
            assertEquals(0, runProbe("send", 2L, leaseWinner.token(), replayBarrier).result());
        } finally {
            Files.deleteIfExists(replayBarrier);
        }
        assertState("3:1:1:1");
    }

    private List<ProbeResult> race(String mode, long version, String tokenA, String tokenB)
            throws Exception {
        Path directory = Files.createTempDirectory("noon-auth-multijvm-");
        Path barrier = directory.resolve("start");
        Process first = startProbe(mode, version, tokenA, barrier);
        Process second = startProbe(mode, version, tokenB, barrier);
        Files.createFile(barrier);
        try {
            return List.of(
                    finishProbe(first, tokenA),
                    finishProbe(second, tokenB)
            );
        } finally {
            first.destroyForcibly();
            second.destroyForcibly();
            Files.deleteIfExists(barrier);
            Files.deleteIfExists(directory);
        }
    }

    private ProbeResult runProbe(String mode, long version, String token, Path barrier)
            throws Exception {
        return finishProbe(startProbe(mode, version, token, barrier), token);
    }

    private Process startProbe(String mode, long version, String token, Path barrier)
            throws Exception {
        String classpath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path")
        );
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(classpath);
        command.add(NoonAuthRecoveryMultiJvmProbe.class.getName());
        command.add(mode);
        command.add(Long.toString(recoveryId));
        command.add(Long.toString(version));
        command.add(token);
        command.add(barrier.toString());
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }

    private ProbeResult finishProbe(Process process, String token) throws Exception {
        assertTrue(process.waitFor(Duration.ofSeconds(15).toMillis(), TimeUnit.MILLISECONDS));
        String output = new String(process.getInputStream().readAllBytes()).trim();
        assertEquals(0, process.exitValue(), output);
        int marker = output.lastIndexOf("RESULT=");
        assertTrue(marker >= 0, output);
        return new ProbeResult(token, Integer.parseInt(output.substring(marker + 7).trim()));
    }

    private ProbeResult assertSingleWinner(List<ProbeResult> results) {
        assertEquals(1, results.stream().mapToInt(ProbeResult::result).sum());
        return results.stream().filter(result -> result.result() == 1).findFirst().orElseThrow();
    }

    private void assertState(String expected) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT CONCAT(recovery.version_no,':',recovery.generation_no,':',"
                        + "recovery.send_attempt_count,':',("
                        + "SELECT COUNT(*) FROM noon_auth_identity_send_ledger ledger "
                        + "WHERE ledger.recovery_id=recovery.id)) "
                        + "FROM noon_auth_identity_recovery recovery WHERE recovery.id=?"
        )) {
            statement.setLong(1, recoveryId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(expected, rows.getString(1));
            }
        }
    }

    private static final class ProbeResult {
        private final String token;
        private final int result;

        private ProbeResult(String token, int result) {
            this.token = token;
            this.result = result;
        }

        String token() {
            return token;
        }

        int result() {
            return result;
        }
    }
}
