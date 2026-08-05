package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReportCsvPreflightTest {

    @Test
    void validatesEveryRecordWidthIncludingQuotedMultilineRecords() {
        assertDoesNotThrow(() -> ReportCsvPreflight.validate(
                "id,note\n1,\"line one\nline two\"\n".getBytes(StandardCharsets.UTF_8)
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportCsvPreflight.validate(
                        "id,note\n1,ok,extra\n".getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    @Test
    void rejectsDuplicateOrBlankHeadersBeforeImport() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportCsvPreflight.validate("id,ID\n1,2\n".getBytes(StandardCharsets.UTF_8))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportCsvPreflight.validate("id,\n1,2\n".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void rejectsNonAuthoritativeBlankDownloadsButAllowsHeaderOnlyReports() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportCsvPreflight.validate("  \n".getBytes(StandardCharsets.UTF_8))
        );
        assertDoesNotThrow(() -> ReportCsvPreflight.validate(
                "id,note\n".getBytes(StandardCharsets.UTF_8)
        ));
    }

    @Test
    void countsDelimitedBlankRowsForExactApplyAccounting() {
        assertEquals(
                2L,
                ReportCsvPreflight.validate(
                        "id,note\n1,ok\n,\n".getBytes(StandardCharsets.UTF_8)
                )
        );
    }
}
