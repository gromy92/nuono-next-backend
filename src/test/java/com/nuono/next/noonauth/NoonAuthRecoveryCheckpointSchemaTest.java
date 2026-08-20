package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryCheckpointSchemaTest {
    @Test
    void migrationMustStoreOnlyEncryptedCheckpointMaterial() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/init/255_noon_auth_recovery_checkpoint.sql"));
        String postcheck = Files.readString(Path.of(
                "src/main/resources/db/postcheck/255_noon_auth_recovery_checkpoint.sql"));
        String catalog = Files.readString(Path.of(
                "src/main/resources/db/init/release-migrations.tsv"));

        assertTrue(sql.contains("noon_auth_recovery_checkpoint"));
        assertTrue(sql.contains("initialization_vector"));
        assertTrue(sql.contains("ciphertext"));
        assertTrue(sql.contains("expires_at"));
        assertTrue(!sql.toLowerCase().contains("otp_code"));
        assertTrue(!sql.toLowerCase().contains("access_token"));
        assertTrue(postcheck.contains("initialization_vector"));
        assertTrue(catalog.contains(
                "255\t255_noon_auth_recovery_checkpoint.sql\tAUTO_ADDITIVE"
                        + "\tdb/init/255_noon_auth_recovery_checkpoint.sql"
                        + "\tdb/postcheck/255_noon_auth_recovery_checkpoint.sql"
                        + "\tdb/postcheck/255_noon_auth_recovery_checkpoint.sql"));
    }
}
