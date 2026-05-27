package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ManualSelectionPermissionSeedContractTest {

    private static final Pattern BUSINESS_ROLE_AUTO_SELECTION_GRANT =
            Pattern.compile("\\(\\s*[234]\\s*,\\s*9101\\s*\\)");
    private static final Pattern BOSS_MANUAL_SELECTION_GRANT =
            Pattern.compile("\\(\\s*2\\s*,\\s*9102\\s*\\)");

    @Test
    void releasePermissionCleanupKeepsBusinessRolesOnManualSelectionMenu() throws IOException {
        String sql = Files.readString(Path.of("..", "release", "20260511_permission_data_integrity_cleanup.sql"));

        assertFalse(
                BUSINESS_ROLE_AUTO_SELECTION_GRANT.matcher(sql).find(),
                "release permission repair must not grant legacy 9101 自动选品 to business roles"
        );
        assertTrue(
                BOSS_MANUAL_SELECTION_GRANT.matcher(sql).find(),
                "boss role must be granted 9102 人工选品 in the release permission repair"
        );
        assertTrue(sql.contains("人工选品"));
        assertTrue(sql.contains("/product/manual-selection"));
    }
}
