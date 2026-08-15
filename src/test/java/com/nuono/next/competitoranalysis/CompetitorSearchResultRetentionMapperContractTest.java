package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CompetitorSearchResultRetentionMapperContractTest {
    @Test
    void deletesOnlyBoundedResultsOfFinishedTerminalRuns() throws IOException {
        String sql = read("src/main/java/com/nuono/next/infrastructure/mapper/CompetitorSearchResultRetentionMapper.java");

        assertTrue(sql.contains("delete sr"));
        assertTrue(sql.contains("from operations_competitor_search_result candidate"));
        assertTrue(sql.contains("search_run.status in ('succeeded', 'partial_failed', 'failed')"));
        assertTrue(sql.contains("search_run.finished_at is not null"));
        assertTrue(sql.contains("search_run.finished_at < #{cutoff}"));
        assertTrue(sql.contains("candidate.is_deleted = b'0'"));
        assertTrue(sql.contains("keyword_run.is_deleted = b'0'"));
        assertTrue(sql.contains("search_run.is_deleted = b'0'"));
        assertTrue(sql.contains("order by search_run.finished_at asc, candidate.id asc"));
        assertTrue(sql.contains("limit #{limit}"));
        assertTrue(sql.contains("from ("), "the candidate list must be materialized before the delete");
        assertFalse(sql.contains("source_result_id"), "rank-fact provenance must not block evidence expiry");
    }

    @Test
    void releaseMigrationAddsTheTerminalRunLookupIndexAndRegistersChecks() throws IOException {
        String migration = read("src/main/resources/db/init/253_operations_competitor_search_result_retention.sql");
        String postcheck = read("src/main/resources/db/postcheck/253_operations_competitor_search_result_retention.sql");
        String catalog = read("src/main/resources/db/init/release-migrations.tsv");

        assertTrue(migration.contains("idx_ops_comp_search_run_retention"));
        assertTrue(migration.contains("(`status`, `finished_at`, `id`)"));
        assertTrue(migration.contains("algorithm=inplace, lock=none"));
        assertTrue(postcheck.contains("status,finished_at,id"));
        assertTrue(catalog.contains(String.join("\t",
                "253",
                "253_operations_competitor_search_result_retention.sql",
                "auto_additive",
                "db/init/253_operations_competitor_search_result_retention.sql",
                "db/postcheck/253_operations_competitor_search_result_retention.sql",
                "db/postcheck/253_operations_competitor_search_result_retention.sql"
        )));
    }

    @Test
    void ordinaryTop200WritesDoNotPersistParserRawPayloads() throws IOException {
        String source = read("src/main/java/com/nuono/next/competitoranalysis/CompetitorSearchRefreshRunner.java");
        int methodStart = source.indexOf("private competitorsearchresultinsertcommand buildsearchresult(");
        int nextMethod = source.indexOf("private string normalizecode", methodStart);

        assertTrue(methodStart >= 0 && nextMethod > methodStart);
        assertFalse(source.substring(methodStart, nextMethod).contains("setrawresultjson"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath)).toLowerCase(Locale.ROOT);
    }
}
