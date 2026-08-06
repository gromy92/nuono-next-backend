package com.nuono.next.noonpull;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strict complete-file CSV parser shared by Noon report preflight and adapters. */
public final class NoonReportCsvRecords {
    private NoonReportCsvRecords() {
    }

    public static List<String[]> parse(byte[] content) {
        return parse(content, false);
    }

    private static List<String[]> parse(byte[] content, boolean retainDelimitedBlankRows) {
        String csv = decode(content == null ? new byte[0] : content);
        List<String[]> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;
        for (int index = 0; index < csv.length(); index++) {
            char value = csv.charAt(index);
            if (quoted) {
                if (value == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(value);
                }
                continue;
            }
            if (quoteClosed) {
                if (value == ',') {
                    addField(record, field);
                    quoteClosed = false;
                } else if (value == '\r' || value == '\n') {
                    addField(record, field);
                    addRecord(records, record, retainDelimitedBlankRows);
                    record = new ArrayList<>();
                    quoteClosed = false;
                    if (value == '\r' && index + 1 < csv.length()
                            && csv.charAt(index + 1) == '\n') {
                        index++;
                    }
                } else {
                    throw new IllegalArgumentException("CSV has content after a closing quote");
                }
                continue;
            }
            if (value == '"') {
                if (field.length() != 0) {
                    throw new IllegalArgumentException("CSV quote begins inside an unquoted field");
                }
                quoted = true;
            } else if (value == ',') {
                addField(record, field);
            } else if (value == '\r' || value == '\n') {
                addField(record, field);
                addRecord(records, record, retainDelimitedBlankRows);
                record = new ArrayList<>();
                if (value == '\r' && index + 1 < csv.length()
                        && csv.charAt(index + 1) == '\n') {
                    index++;
                }
            } else {
                field.append(value);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV contains an unclosed quoted field");
        }
        if (quoteClosed || field.length() > 0 || !record.isEmpty()) {
            addField(record, field);
            addRecord(records, record, retainDelimitedBlankRows);
        }
        return List.copyOf(records);
    }

    /** Parses one complete CSV artifact and rejects ambiguous headers or non-rectangular rows. */
    public static List<String[]> parseRectangular(byte[] content) {
        List<String[]> records = parse(content, true);
        if (records.isEmpty()) {
            return records;
        }
        String[] header = records.get(0);
        Set<String> normalizedHeaders = new HashSet<>();
        for (String value : header) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || !normalizedHeaders.add(normalized)) {
                throw new IllegalArgumentException("CSV contains a blank or duplicate header");
            }
        }
        for (int index = 1; index < records.size(); index++) {
            if (records.get(index).length != header.length) {
                throw new IllegalArgumentException("CSV row width does not match its header");
            }
        }
        return records;
    }

    private static String decode(byte[] content) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            return !decoded.isEmpty() && decoded.charAt(0) == '\ufeff'
                    ? decoded.substring(1)
                    : decoded;
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("CSV is not valid UTF-8", failure);
        }
    }

    private static void addField(List<String> record, StringBuilder field) {
        record.add(field.toString());
        field.setLength(0);
    }

    private static void addRecord(
            List<String[]> records,
            List<String> record,
            boolean retainDelimitedBlankRows
    ) {
        for (String value : record) {
            if (!value.trim().isEmpty()) {
                records.add(record.toArray(new String[0]));
                return;
            }
        }
        if (retainDelimitedBlankRows && record.size() > 1) {
            records.add(record.toArray(new String[0]));
        }
    }
}
