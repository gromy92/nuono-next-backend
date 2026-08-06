package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportPullRequest;
import com.nuono.next.noonpull.NoonReportRowDecision;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfficialWarehouseFbnStageClassifierTest {
    private static final String[] HEADER = {
            "partner_sku", "sku", "asn", "qty_expected", "received_qty",
            "qc_failed_qty", "unidentified_qty", "asn_schedule_date", "country_code"
    };

    private final OfficialWarehouseFbnStageClassifier classifier =
            new OfficialWarehouseFbnStageClassifier(
                    new OfficialWarehouseFbnReceivedReportCsvParser()
            );

    @Test
    void siteMismatchWinsBeforeInvalidDateAndNumericBusinessDefects() {
        String[] row = {
                "P-1", "N-1", "ASN-1", "bad", "0", "0", "0", "bad-date", "AE"
        };

        assertThat(classify(row)).isEqualTo(
                NoonReportRowDecision.Kind.CONTAINER_CONTRACT_ERROR
        );
    }

    @Test
    void windowMismatchWinsBeforeInvalidNumericBusinessDefect() {
        String[] row = {
                "P-1", "N-1", "ASN-1", "bad", "0", "0", "0", "2026-08-02", "SA"
        };

        assertThat(classify(row)).isEqualTo(
                NoonReportRowDecision.Kind.CONTAINER_CONTRACT_ERROR
        );
    }

    @Test
    void sameContainerInvalidValuesAndPhysicalBlankAreBusinessSkips() {
        String[] invalidValues = {
                "P-1", "N-1", "ASN-1", "bad", "0", "0", "0", "bad-date", "SA"
        };
        String[] oversizedProviderIdentity = {
                "P".repeat(101), "N-1", "ASN-1", "1", "1", "0", "0", "2026-08-01", "SA"
        };
        String[] dateOutsideMysqlRange = {
                "P-1", "N-1", "ASN-1", "1", "1", "0", "0", "0999-08-01", "SA"
        };
        String[] physicalBlank = new String[HEADER.length];

        assertThat(classifier.classify(
                file(), HEADER, List.of(
                        invalidValues, oversizedProviderIdentity,
                        dateOutsideMysqlRange, physicalBlank
                )
        ))
                .extracting(NoonReportRowDecision::getKind)
                .containsExactly(
                        NoonReportRowDecision.Kind.BUSINESS_SKIP,
                        NoonReportRowDecision.Kind.BUSINESS_SKIP,
                        NoonReportRowDecision.Kind.BUSINESS_SKIP,
                        NoonReportRowDecision.Kind.BUSINESS_SKIP
                );
    }

    private NoonReportRowDecision.Kind classify(String[] row) {
        return classifier.classify(file(), HEADER, List.<String[]>of(row)).get(0).getKind();
    }

    private NoonReportDownloadedFile file() {
        NoonReportPullRequest request = NoonReportPullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .dataDomain(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED)
                .reportType("fbn_inbound_fbnreceivedreport")
                .dateFrom(LocalDate.of(2026, 8, 1))
                .dateTo(LocalDate.of(2026, 8, 1))
                .build();
        return new NoonReportDownloadedFile(request, "export", "export", "sha", new byte[0]);
    }
}
