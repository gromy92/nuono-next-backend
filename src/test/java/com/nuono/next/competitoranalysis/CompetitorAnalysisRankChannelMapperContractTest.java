package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CompetitorAnalysisRankChannelMapperContractTest {
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
    void dashboardRankChangesCompareOrganicEndpointsAndKeepSponsoredDatesSeparate() throws IOException {
        String sql = selectSql("listrankchanges");

        assertTrue(sql.contains("rf.rank_channel = 'organic'"));
        assertTrue(sql.contains("rf.rank_channel = 'sponsored'"));
    }

    @Test
    void latestEndpointDateUsesTheSameOrganicRankContract() throws IOException {
        String sql = selectSql("selectlatestrankfactdate");

        assertTrue(sql.contains("rf.rank_channel = 'organic'"));
    }

    @Test
    void competitorDetailChangeRankSummariesUseOrganicRanks() throws IOException {
        String sql = selectSql("listcompetitorattributechanges");

        assertTrue(occurrences(sql, "rf.rank_channel = 'organic'") >= 3);
    }

    @Test
    void productKeywordSummaryUsesOrganicRanks() throws IOException {
        String sql = selectSql("listproductbaselines");

        assertTrue(occurrences(sql, "rf.rank_channel = 'organic'") >= 6);
        assertTrue(sql.contains("date_add(utc_timestamp(), interval 8 hour)"));
    }

    @Test
    void browserObservationSerializesAndKeepsItsEvidenceSeparateFromOrganicRows() throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);
        String lockSql = selectSql(source, "lockkeywordrunforbrowserobservation");
        String existingRawSql = selectSql(source, "selectbrowsersponsoredsearchresultbycode");
        String nextPositionSql = selectSql(source, "selectnextsearchresultposition");
        String rawUpdateSql = updateSql(source, "updatesponsoredsearchresultfrombrowser");
        String existingFactSql = selectSql(source, "selectrankfactid");
        String sponsoredSql = updateSql(source, "updatesponsoredrankfact");

        assertTrue(lockSql.contains("for update"));
        assertTrue(existingRawSql.contains("is_sponsored = b'1'"));
        assertTrue(existingRawSql.contains("\\\"source\\\":\\\"browser-observation\\\""));
        assertTrue(nextPositionSql.contains("max(result_position)"));
        assertTrue(!nextPositionSql.contains("is_deleted"));
        assertTrue(rawUpdateSql.contains("raw_result_json = #{rawresultjson}"));
        assertTrue(existingFactSql.contains("rank_channel = #{rankchannel}"));
        assertTrue(sponsoredSql.contains("rank_channel = 'sponsored'"));
        assertTrue(sponsoredSql.contains("is_deleted = b'0'"));
        assertTrue(!sponsoredSql.contains("and is_deleted = b'0'"));
        assertTrue(!source.contains("marksearchresultsponsored"));
        assertTrue(!source.contains("markorganicrankfactnotinscandepthbysourceresultid"));
        assertTrue(!source.contains("markrankfactsponsored"));
    }

    private static String selectSql(String methodName) throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);
        return selectSql(source, methodName);
    }

    private static String selectSql(String source, String methodName) {
        int methodIndex = source.indexOf(methodName);
        assertTrue(methodIndex >= 0, "expected mapper method: " + methodName);
        int selectIndex = source.lastIndexOf("@select", methodIndex);
        assertTrue(selectIndex >= 0, "expected @Select SQL before mapper method: " + methodName);
        return source.substring(selectIndex, methodIndex);
    }

    private static String updateSql(String source, String methodName) {
        int methodIndex = source.indexOf(methodName);
        assertTrue(methodIndex >= 0, "expected mapper method: " + methodName);
        return source.substring(source.lastIndexOf("@update({", methodIndex), methodIndex);
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }
}
