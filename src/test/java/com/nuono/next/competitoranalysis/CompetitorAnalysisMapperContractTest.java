package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CompetitorAnalysisMapperContractTest {
    private static final Path MAPPER_PATH = Path.of(
            "src",
            "main",
            "java",
            "com",
            "nuono",
            "next",
            "infrastructure",
            "mapper",
            "CompetitorAnalysisMapper.java"
    );

    @Test
    void keywordMonitoredCountAssignsEachConfirmedCompetitorToOneKeyword() throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);

        assertEquals(
                2,
                occurrences(source, "select min(primary_kp.id)"),
                "watch-products and product-baselines list queries must use a primary keyword relation"
        );
        assertEquals(
                2,
                occurrences(source, "kp.id = ("),
                "per-keyword monitored counts must count only the primary confirmed keyword relation"
        );
        assertTrue(
                source.contains("cp.review_status = 'confirmed'"),
                "per-keyword monitored counts must align with product-level confirmed competitors"
        );
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
