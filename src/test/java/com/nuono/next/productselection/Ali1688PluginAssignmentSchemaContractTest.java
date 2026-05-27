package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Ali1688PluginAssignmentSchemaContractTest {

    @Test
    void pluginAssignmentMigrationUsesOwnTableSequenceAndDoesNotAlterCandidateTable() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "055_product_selection_ali1688_plugin_assignment.sql"
        ));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `product_selection_ali1688_plugin_assignment`"));
        assertTrue(sql.contains("`active_assignment_key` VARCHAR(80) DEFAULT NULL"));
        assertTrue(sql.contains("UNIQUE KEY `uk_product_selection_ali1688_plugin_current` (`active_assignment_key`)"));
        assertTrue(sql.contains("`assignment_code_hash` CHAR(64) NOT NULL"));
        assertTrue(sql.contains("`submission_idempotency_key` VARCHAR(120) DEFAULT NULL"));
        assertTrue(sql.contains("`accepted_candidate_count` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`rejected_candidate_count` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("'product_selection_ali1688_plugin_assignment'"));
        assertTrue(sql.contains("product_management_id_sequence"));
        assertFalse(sql.contains("ALTER TABLE `product_selection_ali1688_candidate`"));
        assertFalse(sql.contains("ALTER TABLE product_selection_ali1688_candidate"));
    }

    @Test
    void ali1688TaskBackfillMigrationCreatesCurrentTasksFromExistingSourceCollectionsWithoutMaxId() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "064_product_selection_ali1688_task_backfill.sql"
        ));

        assertTrue(sql.contains("product_selection_source_collection"));
        assertTrue(sql.contains("product_selection_ali1688_collection_task"));
        assertTrue(sql.contains("product_management_id_sequence"));
        assertTrue(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("source.status = 'success'"));
        assertTrue(sql.contains("'queued'"));
        assertTrue(sql.contains("'waiting_source'"));
        assertTrue(sql.contains("backfilledFromSourceCollection"));
        assertFalse(sql.contains("MAX("));
        assertFalse(sql.contains("MAX (`"));
        assertFalse(sql.contains("max("));
    }
}
