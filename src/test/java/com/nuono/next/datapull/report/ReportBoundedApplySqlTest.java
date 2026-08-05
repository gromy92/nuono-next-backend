package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.FbnReportApplySql;
import com.nuono.next.infrastructure.mapper.FbnReportBulkMapper;
import com.nuono.next.infrastructure.mapper.LegacyReportFactBulkMapper;
import com.nuono.next.infrastructure.mapper.ReportStageMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ReportBoundedApplySqlTest {

    @Test
    void stageAndApplyTransactionsHaveHardRowAndTimeBounds() throws Exception {
        String slice = selectSql(ReportStageMapper.class, "selectNextApplySlice");
        assertThat(slice)
                .contains("decision='ACCEPTED'")
                .contains("`row_number`>#{afterRowNumber}")
                .contains("ORDER BY `row_number` LIMIT #{limitRows}");
        assertThat(MyBatisReportStageStore.APPLY_CHUNK_ROWS).isEqualTo(200);
        assertTimeout("stage");
        assertTimeout("applySealed");
    }

    @Test
    void legacyFactWritersUseSetBasedJsonAndThePersistedCursorRange() throws Exception {
        for (String methodName : List.of(
                "applySalesFacts",
                "applyOrderFacts",
                "applyFinanceFacts"
        )) {
            Method method = method(LegacyReportFactBulkMapper.class, methodName);
            String sql = String.join(" ", method.getAnnotation(Insert.class).value());
            assertThat(sql)
                    .as(methodName)
                    .contains("JOIN JSON_TABLE(")
                    .contains("staged.`row_number`>#{afterRowNumber}")
                    .contains("staged.`row_number`<=#{throughRowNumber}")
                    .doesNotContain("<foreach");
            assertThat(method.getAnnotation(Options.class).timeout()).isEqualTo(10);
        }
    }

    @Test
    void dp07bUsesBoundedSourceOnlyWritesAndExistingReceiptStatuses() throws Exception {
        for (String sql : List.of(
                FbnReportApplySql.insertReportRows(),
                FbnReportApplySql.insertReceiptLines()
        )) {
            assertThat(sql)
                    .contains("JOIN JSON_TABLE(")
                    .contains("staged.`row_number`>#{afterRowNumber}")
                    .contains("staged.`row_number`<=#{throughRowNumber}")
                    .contains("'SOURCE_ONLY'")
                    .doesNotContain(
                            "<foreach",
                            "LATERAL",
                            "official_warehouse_asn a",
                            "official_warehouse_asn_line",
                            " FROM product_master",
                            " JOIN product_variant",
                            " JOIN product_site_offer"
                    );
        }
        assertThat(FbnReportApplySql.insertReceiptLines())
                .startsWith("INSERT INTO official_warehouse_inbound_receipt_line")
                .contains("NULL,NULL,NULL,noonAsnNr,NULL")
                .contains("NULL,NULL,partnerSku,NULL,noonSku")
                .contains("'SHORT_RECEIVED'")
                .contains("'OVER_RECEIVED'");
        assertThat(FbnReportApplySql.insertReportRows())
                .startsWith("INSERT INTO official_warehouse_report_row");
        assertThat(selectSql(FbnReportBulkMapper.class, "selectChunkProof"))
                .contains("receipt.receipt_status<>'NORMAL'")
                .doesNotContain("receipt.match_status");
        assertThat(String.join(" ", method(FbnReportBulkMapper.class, "insertImportHeader")
                .getAnnotation(Insert.class).value()))
                .contains("NULL,'FBN_INBOUND_FBNRECEIVEDREPORT'")
                .doesNotContain("user_project");
        assertThat(method(FbnReportBulkMapper.class, "insertReportRows")
                .getAnnotation(Options.class).timeout()).isEqualTo(10);
        assertThat(method(FbnReportBulkMapper.class, "insertReceiptLines")
                .getAnnotation(Options.class).timeout()).isEqualTo(10);
    }

    private void assertTimeout(String methodName) throws Exception {
        Transactional transactional = method(MyBatisReportStageStore.class, methodName)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.timeout()).isEqualTo(10);
    }

    private Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private String selectSql(Class<?> type, String name) {
        Select annotation = method(type, name).getAnnotation(Select.class);
        assertThat(annotation).isNotNull();
        return String.join(" ", annotation.value());
    }
}
