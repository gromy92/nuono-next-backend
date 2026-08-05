package com.nuono.next.datapull.cutover;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Strict release-only environment reader; values never enter logs or command arguments. */
final class DataPullRuntimeCutoverManifestEnvironment {

    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private final Map<String, String> values;

    private DataPullRuntimeCutoverManifestEnvironment(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    static DataPullRuntimeCutoverManifestEnvironment load(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("DP_CUTOVER_ENV_FILE_INVALID");
        }
        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring(7).trim();
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw invalidLine(index);
            }
            String key = line.substring(0, separator).trim();
            if (!KEY.matcher(key).matches() || values.containsKey(key)) {
                throw invalidLine(index);
            }
            values.put(key, parseValue(line.substring(separator + 1).trim(), index));
        }
        return new DataPullRuntimeCutoverManifestEnvironment(values);
    }

    String require(String key) {
        String value = values.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("DP_CUTOVER_REQUIRED_CONFIG_MISSING:" + key);
        }
        return value;
    }

    private static String parseValue(String raw, int line) {
        if (raw.isEmpty()) {
            return "";
        }
        char quote = raw.charAt(0);
        if (quote != '\'' && quote != '"') {
            int comment = raw.indexOf(" #");
            return (comment < 0 ? raw : raw.substring(0, comment)).trim();
        }
        int end = closingQuote(raw, quote, line);
        String trailing = raw.substring(end + 1).trim();
        if (!trailing.isEmpty() && !trailing.startsWith("#")) {
            throw invalidLine(line);
        }
        String content = raw.substring(1, end);
        return quote == '\'' ? content : decodeDoubleQuoted(content, line);
    }

    private static int closingQuote(String raw, char quote, int line) {
        boolean escaped = false;
        for (int index = 1; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (quote == '"' && current == '\\' && !escaped) {
                escaped = true;
                continue;
            }
            if (current == quote && !escaped) {
                return index;
            }
            escaped = false;
        }
        throw invalidLine(line);
    }

    private static String decodeDoubleQuoted(String value, int line) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (++index >= value.length()) {
                throw invalidLine(line);
            }
            char escaped = value.charAt(index);
            if (escaped == 'n') result.append('\n');
            else if (escaped == 'r') result.append('\r');
            else if (escaped == 't') result.append('\t');
            else if (escaped == '"' || escaped == '\\' || escaped == '$') result.append(escaped);
            else throw invalidLine(line);
        }
        return result.toString();
    }

    private static IllegalArgumentException invalidLine(int zeroBasedLine) {
        return new IllegalArgumentException(
                "DP_CUTOVER_ENV_LINE_INVALID_" + (zeroBasedLine + 1)
        );
    }
}
