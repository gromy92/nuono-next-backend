package com.nuono.next.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SqlMigrationNamingTest {
    private static final Pattern MIGRATION = Pattern.compile("^(\\d{3})_.+\\.sql$");

    @Test
    void migrationPrefixesAreUnique() throws IOException, InterruptedException {
        Map<String, List<String>> byPrefix = TrackedSqlFiles.initSqlFiles().stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.groupingBy(name -> {
                    Matcher matcher = MIGRATION.matcher(name);
                    assertTrue(matcher.matches(),
                            "Migration filename must match NNN_description.sql: " + name);
                    return matcher.group(1);
                }));

        Map<String, List<String>> duplicates = new HashMap<>();
        byPrefix.forEach((prefix, names) -> {
            if (names.size() > 1) {
                duplicates.put(prefix, names);
            }
        });

        assertTrue(duplicates.isEmpty(), "Duplicate migration prefixes: " + duplicates);
    }
}
