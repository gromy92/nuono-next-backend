package com.nuono.next.datapull.report;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strict UTF-8 CSV cursor that resumes only at a durable record byte offset. */
final class ReportCsvCursor {
    private ReportCsvCursor() {
    }

    static Header readHeader(byte[] content) {
        return readHeader(content, true);
    }

    static Header readHeader(byte[] content, boolean artifactEnd) {
        byte[] bytes = content == null ? new byte[0] : content;
        Parse parse = nextRecord(bytes, 0L, artifactEnd);
        while (parse.record != null && parse.record.ignorablePhysicalBlank) {
            parse = nextRecord(bytes, parse.record.nextByteOffset, artifactEnd);
        }
        if (parse.incomplete) {
            throw new IllegalArgumentException("CSV_HEADER_EXCEEDS_READ_WINDOW");
        }
        Record record = parse.record;
        if (record == null) {
            throw new IllegalArgumentException("downloaded report has no header");
        }
        String[] values = record.values;
        if (values.length > 0 && values[0].startsWith("\ufeff")) {
            values = values.clone();
            values[0] = values[0].substring(1);
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            String header = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (header.isEmpty() || !normalized.add(header)) {
                throw new IllegalArgumentException("CSV contains a blank or duplicate header");
            }
        }
        return new Header(values, record.nextByteOffset);
    }

    static Chunk readRows(byte[] content, long byteOffset, int expectedWidth, int limit) {
        return readRows(content, byteOffset, expectedWidth, limit, true);
    }

    static Chunk readRows(
            byte[] content,
            long byteOffset,
            int expectedWidth,
            int limit,
            boolean artifactEnd
    ) {
        byte[] bytes = content == null ? new byte[0] : content;
        if (byteOffset < 0L || byteOffset > bytes.length) {
            throw new IllegalArgumentException("CSV byte cursor is outside the artifact");
        }
        if (expectedWidth <= 0 || limit <= 0) {
            throw new IllegalArgumentException("CSV chunk shape is invalid");
        }
        long cursor = byteOffset;
        List<String[]> rows = new ArrayList<>(limit);
        while (rows.size() < limit) {
            if (cursor == bytes.length && !artifactEnd) {
                return new Chunk(rows, cursor, false);
            }
            Parse parse = nextRecord(bytes, cursor, artifactEnd);
            if (parse.incomplete) {
                if (rows.isEmpty() && byteOffset == 0L) {
                    throw new IllegalArgumentException("CSV_RECORD_EXCEEDS_READ_WINDOW");
                }
                return new Chunk(rows, cursor, false);
            }
            Record record = parse.record;
            if (record == null) {
                return new Chunk(rows, cursor, artifactEnd);
            }
            cursor = record.nextByteOffset;
            if (record.ignorablePhysicalBlank) {
                // Provider-declared row counts include data-region separator lines. Preserve
                // them as one source row; the domain classifier deterministically skips them.
                String[] blankRow = new String[expectedWidth];
                Arrays.fill(blankRow, "");
                rows.add(blankRow);
                continue;
            }
            if (record.values.length != expectedWidth) {
                throw new IllegalArgumentException("CSV row width does not match its header");
            }
            rows.add(record.values);
        }
        return new Chunk(rows, cursor, artifactEnd && cursor == bytes.length);
    }

    private static Parse nextRecord(
            byte[] bytes,
            long requestedOffset,
            boolean artifactEnd
    ) {
        if (requestedOffset == bytes.length) {
            return artifactEnd ? Parse.endOfFile() : Parse.incomplete();
        }
        int offset = Math.toIntExact(requestedOffset);
        if (offset < 0 || offset > bytes.length) {
            throw new IllegalArgumentException("CSV byte cursor is outside the artifact");
        }
        List<String> fields = new ArrayList<>();
        ByteArrayOutputStream field = new ByteArrayOutputStream();
        boolean quoted = false;
        boolean quoteClosed = false;
        boolean delimiterSeen = false;
        int index = offset;
        while (index < bytes.length) {
            int value = bytes[index] & 0xff;
            if (quoted) {
                if (value == '"') {
                    if (index + 1 < bytes.length && (bytes[index + 1] & 0xff) == '"') {
                        field.write('"');
                        index += 2;
                    } else if (index + 1 == bytes.length && !artifactEnd) {
                        return Parse.incomplete();
                    } else {
                        quoted = false;
                        quoteClosed = true;
                        index++;
                    }
                } else {
                    field.write(value);
                    index++;
                }
                continue;
            }
            if (quoteClosed) {
                if (value == ',') {
                    fields.add(decode(field.toByteArray()));
                    field.reset();
                    delimiterSeen = true;
                    quoteClosed = false;
                    index++;
                    continue;
                }
                if (value == '\r' || value == '\n') {
                    if (value == '\r' && index + 1 == bytes.length && !artifactEnd) {
                        return Parse.incomplete();
                    }
                    fields.add(decode(field.toByteArray()));
                    index = consumeLineEnding(bytes, index);
                    return Parse.record(record(fields, delimiterSeen, index));
                }
                throw new IllegalArgumentException("CSV has content after a closing quote");
            }
            if (value == '"') {
                if (field.size() != 0) {
                    throw new IllegalArgumentException("CSV quote begins inside an unquoted field");
                }
                quoted = true;
                index++;
            } else if (value == ',') {
                fields.add(decode(field.toByteArray()));
                field.reset();
                delimiterSeen = true;
                index++;
            } else if (value == '\r' || value == '\n') {
                if (value == '\r' && index + 1 == bytes.length && !artifactEnd) {
                    return Parse.incomplete();
                }
                fields.add(decode(field.toByteArray()));
                index = consumeLineEnding(bytes, index);
                return Parse.record(record(fields, delimiterSeen, index));
            } else {
                field.write(value);
                index++;
            }
        }
        if (!artifactEnd) {
            return Parse.incomplete();
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV contains an unclosed quoted field");
        }
        fields.add(decode(field.toByteArray()));
        return Parse.record(record(fields, delimiterSeen, index));
    }

    private static Record record(List<String> fields, boolean delimiterSeen, int nextOffset) {
        boolean allBlank = true;
        for (String value : fields) {
            if (!value.trim().isEmpty()) {
                allBlank = false;
                break;
            }
        }
        return new Record(
                fields.toArray(new String[0]),
                nextOffset,
                allBlank && !delimiterSeen
        );
    }

    private static int consumeLineEnding(byte[] bytes, int index) {
        if ((bytes[index] & 0xff) == '\r'
                && index + 1 < bytes.length
                && (bytes[index + 1] & 0xff) == '\n') {
            return index + 2;
        }
        return index + 1;
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw new IllegalArgumentException("CSV is not valid UTF-8", invalidUtf8);
        }
    }

    static final class Header {
        private final String[] values;
        private final long nextByteOffset;

        Header(String[] values, long nextByteOffset) {
            this.values = values.clone();
            this.nextByteOffset = nextByteOffset;
        }

        String[] values() { return values.clone(); }
        long nextByteOffset() { return nextByteOffset; }
    }

    static final class Chunk {
        private final List<String[]> rows;
        private final long nextByteOffset;
        private final boolean endOfFile;

        Chunk(List<String[]> rows, long nextByteOffset, boolean endOfFile) {
            this.rows = List.copyOf(rows);
            this.nextByteOffset = nextByteOffset;
            this.endOfFile = endOfFile;
        }

        List<String[]> rows() { return rows; }
        long nextByteOffset() { return nextByteOffset; }
        boolean endOfFile() { return endOfFile; }
    }

    private static final class Record {
        private final String[] values;
        private final long nextByteOffset;
        private final boolean ignorablePhysicalBlank;

        private Record(String[] values, long nextByteOffset, boolean ignorablePhysicalBlank) {
            this.values = values;
            this.nextByteOffset = nextByteOffset;
            this.ignorablePhysicalBlank = ignorablePhysicalBlank;
        }
    }

    private static final class Parse {
        private final Record record;
        private final boolean incomplete;

        private Parse(Record record, boolean incomplete) {
            this.record = record;
            this.incomplete = incomplete;
        }

        private static Parse record(Record record) {
            return new Parse(record, false);
        }

        private static Parse endOfFile() {
            return new Parse(null, false);
        }

        private static Parse incomplete() {
            return new Parse(null, true);
        }
    }
}
