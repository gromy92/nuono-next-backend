package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CompetitorCorrectionWriterFenceMigrationTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/init",
            "241_operations_competitor_correction_writer_fence.sql"
    );

    @Test
    void migrationAcceptsOnlyAbsentOrExactV1Schema() throws IOException {
        String sql = normalized();

        assertTrue(sql.contains("then 'legacy'"));
        assertTrue(sql.contains("then 'target_empty'"));
        assertTrue(sql.contains("then 'target'"));
        assertTrue(sql.contains("else 'drift'"));
        assertTrue(sql.contains("information_schema.columns"));
        assertTrue(sql.contains("information_schema.statistics"));
        assertTrue(sql.contains("information_schema.table_constraints"));
        assertTrue(sql.contains("information_schema.check_constraints"));
        assertTrue(sql.contains("migration_241_unsupported_schema_drift"));
        assertFalse(sql.contains("create table if not exists"));
    }

    @Test
    void schemaForcesOneFixedOpenOrActiveFenceWithAudit() throws IOException {
        String sql = normalized();

        assertTrue(sql.contains(
                "operations_competitor_correction_writer_fence"
        ));
        assertTrue(sql.contains("historical_business_date_correction"));
        assertTrue(sql.contains("primary key (`fence_name`)"));
        assertTrue(sql.contains("chk_ops_comp_cwf_name"));
        assertTrue(sql.contains("chk_ops_comp_cwf_status"));
        assertTrue(sql.contains("`fence_status` in (''open'', ''active'')"));
        assertTrue(sql.contains("chk_ops_comp_cwf_active_audit"));
        assertTrue(sql.contains("`generation` > 0"));
        assertTrue(sql.contains("`operation_run_id` is not null"));
        assertTrue(sql.contains("`activated_by` is not null"));
        assertTrue(sql.contains("`activated_at` is not null"));
        assertTrue(sql.contains("`reopened_by` is not null"));
        assertTrue(sql.contains("`reopened_at` is not null"));
        assertTrue(sql.contains("`generation` = 0"));
        assertTrue(sql.contains("`operation_run_id` is null"));
    }

    @Test
    void legacySeedAndRerunAreDeterministicAndPostchecked() throws IOException {
        String sql = normalized();

        assertTrue(sql.contains(
                "values (''historical_business_date_correction'', 0, ''open'')"
        ));
        assertTrue(sql.contains("migration_241_exact_empty"));
        assertTrue(sql.contains("@cwf_state in ('legacy', 'target_empty')"));
        assertTrue(sql.contains("migration_241_already_target"));
        assertTrue(sql.contains("migration_241_target_verified"));
        assertTrue(sql.contains("migration_241_postcheck_failed"));
        assertTrue(Files.readAllLines(MIGRATION).size() <= 300);
    }

    private static String normalized() throws IOException {
        return Files.readString(MIGRATION)
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
