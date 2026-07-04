package com.nuono.next.productkeyword;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ProductKeywordTagJson {
    private ProductKeywordTagJson() {
    }

    static Set<String> parse(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Set.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        StringBuilder token = new StringBuilder();
        boolean inString = false;
        boolean escaping = false;
        for (int index = 0; index < tagsJson.length(); index++) {
            char current = tagsJson.charAt(index);
            if (escaping) {
                token.append(current);
                escaping = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaping = true;
                continue;
            }
            if (current == '"') {
                if (inString) {
                    add(tags, token.toString());
                    token.setLength(0);
                }
                inString = !inString;
                continue;
            }
            if (inString) {
                token.append(current);
                continue;
            }
            if (Character.isLetterOrDigit(current) || current == '_') {
                token.append(current);
                continue;
            }
            if (token.length() > 0) {
                add(tags, token.toString());
                token.setLength(0);
            }
        }
        if (token.length() > 0) {
            add(tags, token.toString());
        }
        return tags;
    }

    static String merge(String tagsJson, List<String> tagsToAdd) {
        Set<String> tags = new LinkedHashSet<>(parse(tagsJson));
        if (tagsToAdd != null) {
            tagsToAdd.forEach(tag -> add(tags, tag));
        }
        return write(new ArrayList<>(tags));
    }

    private static void add(Set<String> tags, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        tags.add(value.trim().toUpperCase(Locale.ROOT));
    }

    private static String write(List<String> tags) {
        return "[" + tags.stream().map(ProductKeywordTagJson::quote).reduce((left, right) -> left + "," + right).orElse("") + "]";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
