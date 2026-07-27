package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompetitorRefreshRunResultSupportTest {

    @Test
    void detailOnlyFailureCannotBeReportedAsSuccessfulRun() {
        CompetitorProductDetailRefreshResult detailResult =
                CompetitorProductDetailRefreshResult.unavailable(
                        "DETAIL_SOURCE_NOT_PRODUCT_DETAIL",
                        "Noon 前台仅返回搜索基础字段。"
                );

        String status = CompetitorRefreshRunResultSupport.status(0, 0, detailResult);
        String resultJson = CompetitorRefreshRunResultSupport.resultJson(
                CompetitorRefreshExecutionMode.SCHEDULED_DETAIL,
                status,
                0,
                0,
                detailResult,
                0,
                0
        );

        assertEquals("FAILED", status);
        assertTrue(resultJson.contains("\"detailAttempted\":1"));
        assertTrue(resultJson.contains("\"detailFailed\":1"));
        assertTrue(resultJson.contains("\"keywordFailed\":0"));
    }
}
