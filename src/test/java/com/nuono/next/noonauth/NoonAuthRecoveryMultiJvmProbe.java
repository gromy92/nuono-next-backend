package com.nuono.next.noonauth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

/** Child-JVM database probe used only by the isolated MySQL concurrency test. */
public final class NoonAuthRecoveryMultiJvmProbe {
    private NoonAuthRecoveryMultiJvmProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("expected mode, recovery, version, token and barrier");
        }
        awaitBarrier(Path.of(args[4]));
        int result;
        try (Connection connection = DriverManager.getConnection(
                required("NUONO_NOON_AUTH_MYSQL_URL"),
                required("NUONO_NOON_AUTH_MYSQL_USERNAME"),
                required("NUONO_NOON_AUTH_MYSQL_PASSWORD")
        )) {
            long recoveryId = Long.parseLong(args[1]);
            long version = Long.parseLong(args[2]);
            if ("claim".equals(args[0])) {
                result = claim(connection, recoveryId, version, args[3]);
            } else if ("send".equals(args[0])) {
                result = sendIntent(connection, recoveryId, version, args[3]);
            } else {
                throw new IllegalArgumentException("unsupported mode");
            }
        }
        System.out.println("RESULT=" + result);
        System.out.flush();
        Runtime.getRuntime().halt(0);
    }

    private static int claim(Connection connection, long recoveryId, long version, String token)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE noon_auth_identity_recovery "
                        + "SET lease_owner=?,lease_token=?,"
                        + "lease_until=UTC_TIMESTAMP(3)+INTERVAL 10 MINUTE,"
                        + "started_at=COALESCE(started_at,UTC_TIMESTAMP(3)),"
                        + "version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3) "
                        + "WHERE id=? AND status='COALESCING' AND version_no=? "
                        + "AND next_attempt_at<=UTC_TIMESTAMP(3) "
                        + "AND (lease_until IS NULL OR lease_until<=UTC_TIMESTAMP(3)) "
                        + "AND active_identity_slot IS NOT NULL"
        )) {
            statement.setString(1, "jvm-" + token);
            statement.setString(2, token);
            statement.setLong(3, recoveryId);
            statement.setLong(4, version);
            return statement.executeUpdate();
        }
    }

    private static int sendIntent(
            Connection connection,
            long recoveryId,
            long version,
            String token
    ) throws Exception {
        connection.setAutoCommit(false);
        try {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE noon_auth_identity_recovery "
                            + "SET generation_no=generation_no+1,"
                            + "first_send_at=CASE WHEN send_attempt_count=0 "
                            + "THEN UTC_TIMESTAMP(3) ELSE first_send_at END,"
                            + "second_send_at=CASE WHEN send_attempt_count=1 "
                            + "THEN UTC_TIMESTAMP(3) ELSE second_send_at END,"
                            + "send_attempt_count=send_attempt_count+1,"
                            + "version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3) "
                            + "WHERE id=? AND status='COALESCING' AND version_no=? "
                            + "AND lease_token=? AND lease_until>UTC_TIMESTAMP(3) "
                            + "AND send_attempt_count<2 AND active_identity_slot IS NOT NULL"
            )) {
                statement.setLong(1, recoveryId);
                statement.setLong(2, version);
                statement.setString(3, token);
                updated = statement.executeUpdate();
            }
            if (updated == 1) {
                insertLedger(connection, recoveryId);
                connection.commit();
            } else {
                connection.rollback();
            }
            return updated;
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        }
    }

    private static void insertLedger(Connection connection, long recoveryId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO noon_auth_identity_send_ledger ("
                        + "identity_key,recovery_id,config_fingerprint,send_budget_epoch,generation_no,"
                        + "send_intent_at,gmt_create) "
                        + "SELECT identity_key,id,config_fingerprint,send_budget_epoch,generation_no,"
                        + "UTC_TIMESTAMP(3),UTC_TIMESTAMP(3) "
                        + "FROM noon_auth_identity_recovery "
                        + "WHERE id=? AND config_fingerprint IS NOT NULL"
        )) {
            statement.setLong(1, recoveryId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("send ledger insert was not exact");
            }
        }
    }

    private static void awaitBarrier(Path barrier) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        while (!Files.exists(barrier) && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        if (!Files.exists(barrier)) {
            throw new IllegalStateException("concurrency barrier timed out");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
