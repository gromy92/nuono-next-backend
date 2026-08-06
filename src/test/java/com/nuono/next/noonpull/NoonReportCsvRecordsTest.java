package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonReportCsvRecordsTest {

    @Test
    void parsesQuotedCommasEscapedQuotesAndEmbeddedNewlinesAsOneRecord() {
        List<String[]> records = NoonReportCsvRecords.parse(
                ("id,title,note\r\n"
                        + "1,\"paper, yellow\",\"line one\nline \"\"two\"\"\"\r\n")
                        .getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(2, records.size());
        assertArrayEquals(new String[]{"1", "paper, yellow", "line one\nline \"two\""},
                records.get(1));
    }

    @Test
    void rejectsMalformedUtf8AndCharactersAfterClosingQuote() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NoonReportCsvRecords.parse(new byte[]{(byte) 0xc3, (byte) 0x28})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> NoonReportCsvRecords.parse("id\n\"one\"two\n".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void stripsUtf8BomFromTheFirstHeader() {
        List<String[]> records = NoonReportCsvRecords.parse(
                "\ufeffid,title\n1,paper\n".getBytes(StandardCharsets.UTF_8)
        );

        assertArrayEquals(new String[]{"id", "title"}, records.get(0));
    }

    @Test
    void rectangularModeRejectsDuplicateHeadersAndEveryMismatchedDelimitedRow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NoonReportCsvRecords.parseRectangular(
                        "id,ID\n1,2\n".getBytes(StandardCharsets.UTF_8)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> NoonReportCsvRecords.parseRectangular(
                        "id,title\n1,ok,extra\n".getBytes(StandardCharsets.UTF_8)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> NoonReportCsvRecords.parseRectangular(
                        "id,title\n,,\n".getBytes(StandardCharsets.UTF_8)
                )
        );
    }
}
