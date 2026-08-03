package com.nuono.next.filemanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FileParseRetirementContractTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "nuono", "next");
    private static final Path DB_INIT = Path.of("src", "main", "resources", "db", "init");
    private static final Path DB_POSTCHECK = Path.of(
            "src", "main", "resources", "db", "postcheck"
    );

    @Test
    void backendRuntimeAndDedicatedDependenciesStayRemoved() throws IOException {
        Path parsePackage = MAIN_JAVA.resolve(Path.of("filemanagement", "parse"));
        if (Files.exists(parsePackage)) {
            try (Stream<Path> files = Files.walk(parsePackage)) {
                assertFalse(files.anyMatch(path -> path.toString().endsWith(".java")));
            }
        }
        assertFalse(Files.exists(MAIN_JAVA.resolve(
                Path.of("infrastructure", "mapper", "FileManagementParseMapper.java")
        )));

        String applicationConfig = Files.readString(Path.of("src", "main", "resources", "application.yml"));
        assertFalse(applicationConfig.contains("NUONO_FILE_PARSE"));
        assertFalse(applicationConfig.contains("file-management:\n    parse:"));

        String pom = Files.readString(Path.of("pom.xml"));
        assertFalse(pom.contains("<artifactId>poi-scratchpad</artifactId>"));
        assertFalse(pom.contains("<artifactId>pdfbox</artifactId>"));
    }

    @Test
    void retirementKeepsHistoricalDataAndOnlySoftDisablesEntrypoints() throws IOException {
        assertTrue(Files.exists(DB_INIT.resolve("037_file_management_parse_v1.sql")));
        assertTrue(Files.exists(DB_INIT.resolve("065_unified_logistics_quote_facts.sql")));
        assertTrue(Files.exists(DB_INIT.resolve("067_official_fbn_outbound_fee_facts.sql")));

        String migration = Files.readString(DB_INIT.resolve("242_file_management_parse_retirement.sql"))
                .toLowerCase(Locale.ROOT);
        assertTrue(migration.contains("menu_id` = 9301"));
        assertTrue(migration.contains("file_mgmt_parse_target_plan"));
        assertTrue(migration.contains("@nuono_242_all_legacy_parse_runtimes_drained"));
        assertTrue(migration.contains("blocking_task_count"));
        assertTrue(migration.contains("not in ('published', 'failed')"));
        assertTrue(migration.contains("where `status` = 'active'\n  and `is_deleted` = b'0'"));
        assertFalse(migration.contains("delete from"));
        assertFalse(migration.contains("drop table"));
        assertFalse(migration.contains("logistics_service_line"));
        assertFalse(migration.contains("official_outbound_"));

        String postcheck = Files.readString(DB_POSTCHECK.resolve(
                "242_file_management_parse_retirement.sql"
        )).toLowerCase(Locale.ROOT);
        assertTrue(postcheck.stripLeading().startsWith("select if("));
        assertTrue(postcheck.contains("file_mgmt_parse_task"));
        assertFalse(Pattern.compile(
                "(?is)(?:^|;)\\s*(?:set|insert|update|delete|alter|create|drop|truncate)\\b"
        ).matcher(postcheck).find());

        String executableMigration = migration.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (left, right) -> left + "\n" + right);
        Matcher updateMatcher = Pattern.compile("(?is)\\bupdate\\s+.*?;").matcher(executableMigration);
        List<String> updateStatements = new ArrayList<>();
        Set<String> updatedTables = new HashSet<>();
        while (updateMatcher.find()) {
            String statement = updateMatcher.group().trim();
            updateStatements.add(statement);
            Matcher safeHeader = Pattern.compile(
                    "(?is)^update\\s+`([a-z0-9_]+)`\\s+set\\s+"
            ).matcher(statement);
            assertTrue(safeHeader.find(), "Every UPDATE must target one quoted table followed directly by SET");
            updatedTables.add(safeHeader.group(1));
        }
        assertEquals(5, updateStatements.size());
        assertEquals(Set.of(
                "user_menu",
                "role_menu",
                "menu",
                "file_mgmt_parse_target_plan_scope",
                "file_mgmt_parse_target_plan"
        ), updatedTables);
        assertActiveOnlyUpdate(updateStatementFor(updateStatements, "file_mgmt_parse_target_plan_scope"));
        assertActiveOnlyUpdate(updateStatementFor(updateStatements, "file_mgmt_parse_target_plan"));
    }

    private static String updateStatementFor(List<String> statements, String table) {
        String prefix = "update `" + table + "`";
        return statements.stream()
                .filter(statement -> statement.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing UPDATE for " + table));
    }

    private static void assertActiveOnlyUpdate(String statement) {
        assertTrue(Pattern.compile(
                "(?is)\\bwhere\\s+`status`\\s*=\\s*'active'\\s+and\\s+`is_deleted`\\s*=\\s*b'0'\\s*;\\s*$"
        ).matcher(statement).find());
    }
}
