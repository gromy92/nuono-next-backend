package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ProductControlledRealPublishAcceptanceChecklistTest {

    @Test
    void controlledRealPublishChecklistRequiresHumanConfirmationAndRecordsReadbackEvidence() throws Exception {
        Path checklist = findWorkspaceRoot().resolve(
                ".scratch/product-management-current-requirements-status/controlled-real-publish-acceptance-checklist.md"
        );
        assertTrue(Files.exists(checklist), () -> "Missing controlled real publish checklist: " + checklist);

        String content = Files.readString(checklist);
        String normalized = content.toLowerCase(Locale.ROOT);

        assertContains(content, "# Controlled Real Noon Publish Acceptance Checklist");
        assertContains(content, "ownerUserId");
        assertContains(content, "storeCode");
        assertContains(content, "SKU/PSKU");
        assertContains(content, "intended fields");
        assertContains(content, "expected readback");
        assertContains(content, "no-draft-loss");
        assertContains(content, "task-lock");
        assertContains(content, "NoonSessionGateway request count");
        assertContains(content, "task status");
        assertContains(content, "readback result");
        assertContains(content, "history entry");
        assertContains(content, "user-visible state");
        assertContains(content, "publish-current");
        assertContains(content, "I confirm");
        assertContains(normalized, "timeout");
        assertContains(normalized, "captcha");
        assertContains(normalized, "login failure");
        assertContains(normalized, "unexpected noon response");
        assertContains(normalized, "no automation");
        assertContains(normalized, "afk agent");
    }

    private static Path findWorkspaceRoot() throws IOException {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            if (Files.exists(cursor.resolve(".scratch/product-management-current-requirements-status/PRD.md"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Unable to locate workspace root from " + Path.of("").toAbsolutePath());
    }

    private static void assertContains(String content, String expected) {
        assertTrue(content.contains(expected), () -> "Expected checklist to contain: " + expected);
    }
}
