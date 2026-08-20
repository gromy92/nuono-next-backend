package com.nuono.next.store;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalDbStoreSyncProjectCookieIsolationTest {

    @Test
    void projectConnectionTestHasNoOwnerCookieFallback() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/store/LocalDbStoreSyncService.java"));
        String method = source.substring(
                source.indexOf("public StoreConnectionTestResult testConnection"),
                source.indexOf("private String connectionFailureMessage")
        );

        assertTrue(method.contains("project.getNoonPartnerCookie()"));
        assertFalse(method.contains("owner.getNoonPartnerCookie()"));
    }
}
