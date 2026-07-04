package com.nuono.next.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthEmailCodeSchemaContractTest {

    @Test
    void migrationCreatesEmailCodeChallengeTable() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/166_auth_email_code_login.sql"));

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `auth_email_code_challenge`")
                .contains("`email` VARCHAR(190) NOT NULL")
                .contains("`purpose` VARCHAR(32) NOT NULL")
                .contains("`code_hash` VARCHAR(128) NOT NULL")
                .contains("`code_salt` VARCHAR(64) NOT NULL")
                .contains("`expires_at` DATETIME NOT NULL")
                .contains("`consumed_at` DATETIME DEFAULT NULL")
                .contains("`attempt_count` INT NOT NULL DEFAULT 0")
                .contains("KEY `idx_auth_email_code_active` (`email`, `purpose`, `consumed_at`, `expires_at`)")
                .doesNotContain("123456");
    }
}
