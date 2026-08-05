package com.nuono.next.noonads;

import com.nuono.next.noonpull.NoonReportDownloadedFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Strict UTF-8 and quoted-field decoder for the legacy Ads CSV interchange. */
final class NoonAdvertisingCsvDecoder {

    private NoonAdvertisingCsvDecoder() {
    }

    static List<List<String>> parse(NoonReportDownloadedFile file) {
        String csv = decode(file);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean sawAnyCharacter = false;
        for (int index = 0; index < csv.length(); index++) {
            char current = csv.charAt(index);
            sawAnyCharacter = true;
            if (inQuotes) {
                if (current == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }
            if (current == '"') {
                inQuotes = true;
            } else if (current == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (current == '\n') {
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
            } else if (current != '\r') {
                field.append(current);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Unclosed quoted CSV field.");
        }
        if (sawAnyCharacter || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String decode(NoonReportDownloadedFile file) {
        try {
            String csv = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file == null ? new byte[0] : file.getContent()))
                    .toString();
            return !csv.isEmpty() && csv.charAt(0) == '\ufeff' ? csv.substring(1) : csv;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF-8 report.", exception);
        }
    }
}
