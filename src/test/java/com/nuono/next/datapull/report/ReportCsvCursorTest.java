package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ReportCsvCursorTest {

    @Test
    void parsesUtf8QuotedNewlineEscapedQuoteAndCrLf() {
        byte[] csv = ("id,note\r\n"
                + "1,\"مرحبا,\r\n\"\"世界\"\"\"\r\n"
                + "2,ok\r\n").getBytes(StandardCharsets.UTF_8);

        ReportCsvCursor.Header header = ReportCsvCursor.readHeader(csv, true);
        ReportCsvCursor.Chunk rows = ReportCsvCursor.readRows(
                csv,
                header.nextByteOffset(),
                2,
                200,
                true
        );

        assertThat(header.values()).containsExactly("id", "note");
        assertThat(rows.rows()).hasSize(2);
        assertThat(rows.rows().get(0)).containsExactly("1", "مرحبا,\r\n\"世界\"");
        assertThat(rows.rows().get(1)).containsExactly("2", "ok");
        assertThat(rows.nextByteOffset()).isEqualTo(csv.length);
        assertThat(rows.endOfFile()).isTrue();
    }

    @Test
    void restartsAnIncompleteFirstDataRecordAtItsDurableByteBoundary() {
        byte[] csv = "id,note\n1,\"line-one\nline-two\"\n2,ok\n"
                .getBytes(StandardCharsets.UTF_8);
        int cut = "id,note\n1,\"line-one\n".getBytes(StandardCharsets.UTF_8).length;
        byte[] firstRange = Arrays.copyOfRange(csv, 0, cut);
        ReportCsvCursor.Header header = ReportCsvCursor.readHeader(firstRange, false);

        ReportCsvCursor.Chunk incomplete = ReportCsvCursor.readRows(
                firstRange,
                header.nextByteOffset(),
                2,
                200,
                false
        );
        assertThat(incomplete.rows()).isEmpty();
        assertThat(incomplete.nextByteOffset()).isEqualTo(header.nextByteOffset());
        assertThat(incomplete.endOfFile()).isFalse();

        byte[] resumedRange = Arrays.copyOfRange(csv, (int) header.nextByteOffset(), csv.length);
        ReportCsvCursor.Chunk resumed = ReportCsvCursor.readRows(
                resumedRange,
                0L,
                2,
                200,
                true
        );
        assertThat(resumed.rows()).hasSize(2);
        assertThat(resumed.rows().get(0)).containsExactly("1", "line-one\nline-two");
        assertThat(resumed.rows().get(1)).containsExactly("2", "ok");
    }

    @Test
    void physicalBlankDataLineCountsAsOneDeterministicEmptySourceRow() {
        byte[] csv = "id,note\n1,ok\n\r\n2,ok\n".getBytes(StandardCharsets.UTF_8);
        ReportCsvCursor.Header header = ReportCsvCursor.readHeader(csv, true);

        ReportCsvCursor.Chunk rows = ReportCsvCursor.readRows(
                csv,
                header.nextByteOffset(),
                2,
                200,
                true
        );

        assertThat(rows.rows()).hasSize(3);
        assertThat(rows.rows().get(1)).containsExactly("", "");
    }

    @Test
    void failsClosedForMalformedUtf8AndForARecordLargerThanTheReadRange() {
        assertThatThrownBy(() -> ReportCsvCursor.readHeader(
                new byte[] {'i', 'd', ',', (byte) 0xc3, '\n'},
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV is not valid UTF-8");

        assertThatThrownBy(() -> ReportCsvCursor.readRows(
                "1,\"incomplete".getBytes(StandardCharsets.UTF_8),
                0L,
                2,
                200,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV_RECORD_EXCEEDS_READ_WINDOW");
    }
}
