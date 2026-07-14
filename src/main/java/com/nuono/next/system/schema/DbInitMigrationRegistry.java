package com.nuono.next.system.schema;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public final class DbInitMigrationRegistry {
    private static final Pattern MIGRATION_FILE_NAME = Pattern.compile("^(\\d{3})_[a-z0-9][a-z0-9_]*\\.sql$");
    private static final String INIT_SQL_PATTERN = "classpath*:db/init/*.sql";
    private static final String INIT_LOCATION_PREFIX = "classpath:db/init/";
    private static final String GOVERNANCE_RESOURCE = "db/init/migration-governance.tsv";

    private DbInitMigrationRegistry() {
    }

    public static List<String> initScriptResourceLocations() {
        return initScriptResourceLocations(new PathMatchingResourcePatternResolver());
    }

    public static List<String> initScriptResourceLocations(ResourcePatternResolver resolver) {
        List<String> fileNames = initScriptFileNames(resolver);
        MigrationGovernanceReport report = inspectFileNames(fileNames, legacyDuplicatePrefixes());
        if (!report.isValid()) {
            throw new IllegalStateException("Invalid db init migration governance: " + report.getViolations());
        }
        return report.getMigrationFileNames().stream()
                .map(fileName -> INIT_LOCATION_PREFIX + fileName)
                .collect(Collectors.toList());
    }

    public static List<String> initScriptFileNames(ResourcePatternResolver resolver) {
        Objects.requireNonNull(resolver, "resolver must not be null");
        try {
            Resource[] resources = resolver.getResources(INIT_SQL_PATTERN);
            TreeSet<String> fileNames = new TreeSet<>(migrationFileComparator());
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                if (fileName != null) {
                    fileNames.add(fileName);
                }
            }
            return List.copyOf(fileNames);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to list db init migrations", exception);
        }
    }

    public static List<LegacyDuplicatePrefix> legacyDuplicatePrefixes() {
        InputStream input = DbInitMigrationRegistry.class.getClassLoader().getResourceAsStream(GOVERNANCE_RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Missing db init migration governance resource: " + GOVERNANCE_RESOURCE);
        }

        List<LegacyDuplicatePrefix> duplicates = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                if (columns.length != 5) {
                    throw new IllegalStateException("Invalid migration governance row " + lineNumber + ": " + line);
                }
                if (!"legacy_duplicate_prefix".equals(columns[0])) {
                    continue;
                }
                duplicates.add(new LegacyDuplicatePrefix(
                        columns[1],
                        columns[2],
                        columns[3],
                        splitFiles(columns[4])
                ));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read db init migration governance", exception);
        }
        return List.copyOf(duplicates);
    }

    public static MigrationGovernanceReport inspectFileNames(
            Collection<String> fileNames,
            Collection<LegacyDuplicatePrefix> legacyDuplicatePrefixes
    ) {
        List<String> sortedFileNames = fileNames.stream()
                .sorted(migrationFileComparator())
                .collect(Collectors.toList());
        List<String> violations = new ArrayList<>();
        Map<String, List<String>> filesByPrefix = new LinkedHashMap<>();

        for (String fileName : sortedFileNames) {
            Matcher matcher = MIGRATION_FILE_NAME.matcher(fileName);
            if (!matcher.matches()) {
                violations.add("Migration filename must match NNN_description.sql with lowercase slug: " + fileName);
                continue;
            }
            filesByPrefix.computeIfAbsent(matcher.group(1), ignored -> new ArrayList<>()).add(fileName);
        }

        Map<String, LegacyDuplicatePrefix> legacyByPrefix = legacyDuplicatePrefixes.stream()
                .collect(Collectors.toMap(
                        LegacyDuplicatePrefix::getPrefix,
                        duplicate -> duplicate,
                        (left, right) -> {
                            throw new IllegalStateException("Duplicate legacy migration governance prefix: "
                                    + left.getPrefix());
                        },
                        LinkedHashMap::new
                ));

        for (Map.Entry<String, LegacyDuplicatePrefix> entry : legacyByPrefix.entrySet()) {
            LegacyDuplicatePrefix legacy = entry.getValue();
            if (legacy.getOwner().isBlank()) {
                violations.add("Legacy duplicate prefix " + legacy.getPrefix() + " must declare an owner");
            }
            if (legacy.getReason().isBlank()) {
                violations.add("Legacy duplicate prefix " + legacy.getPrefix() + " must declare a reason");
            }
            if (legacy.getFileNames().size() < 2) {
                violations.add("Legacy duplicate prefix " + legacy.getPrefix() + " must list at least two files");
            }
        }

        filesByPrefix.forEach((prefix, names) -> {
            if (names.size() <= 1) {
                if (legacyByPrefix.containsKey(prefix)) {
                    violations.add("Legacy duplicate prefix " + prefix + " is registered but actual prefix is not duplicated");
                }
                return;
            }

            LegacyDuplicatePrefix legacy = legacyByPrefix.get(prefix);
            if (legacy == null) {
                violations.add("Duplicate migration prefix " + prefix
                        + " is not registered in db/init/migration-governance.tsv");
                return;
            }

            if (!legacy.getFileNames().equals(names)) {
                violations.add("Duplicate migration prefix " + prefix
                        + " registered files do not match actual files: registered="
                        + legacy.getFileNames()
                        + ", actual="
                        + names);
            }
        });

        return new MigrationGovernanceReport(sortedFileNames, violations);
    }

    private static Comparator<String> migrationFileComparator() {
        return Comparator
                .comparingInt(DbInitMigrationRegistry::migrationNumber)
                .thenComparing(fileName -> fileName);
    }

    private static int migrationNumber(String fileName) {
        Matcher matcher = Pattern.compile("^(\\d{3})_.*\\.sql$").matcher(fileName);
        if (!matcher.matches()) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static List<String> splitFiles(String filesColumn) {
        if (filesColumn.isBlank()) {
            return List.of();
        }
        List<String> fileNames = new ArrayList<>();
        for (String fileName : filesColumn.split(",")) {
            fileNames.add(fileName.trim());
        }
        fileNames.sort(migrationFileComparator());
        return List.copyOf(fileNames);
    }

    public static final class LegacyDuplicatePrefix {
        private final String prefix;
        private final String owner;
        private final String reason;
        private final List<String> fileNames;

        public LegacyDuplicatePrefix(String prefix, String owner, String reason, List<String> fileNames) {
            this.prefix = Objects.requireNonNull(prefix, "prefix must not be null");
            this.owner = Objects.requireNonNull(owner, "owner must not be null");
            this.reason = Objects.requireNonNull(reason, "reason must not be null");
            List<String> sorted = new ArrayList<>(Objects.requireNonNull(fileNames, "fileNames must not be null"));
            sorted.sort(migrationFileComparator());
            this.fileNames = List.copyOf(sorted);
        }

        public String getPrefix() {
            return prefix;
        }

        public String getOwner() {
            return owner;
        }

        public String getReason() {
            return reason;
        }

        public List<String> getFileNames() {
            return fileNames;
        }
    }

    public static final class MigrationGovernanceReport {
        private final List<String> migrationFileNames;
        private final List<String> violations;

        private MigrationGovernanceReport(List<String> migrationFileNames, List<String> violations) {
            this.migrationFileNames = List.copyOf(migrationFileNames);
            this.violations = List.copyOf(violations);
        }

        public boolean isValid() {
            return violations.isEmpty();
        }

        public List<String> getMigrationFileNames() {
            return migrationFileNames;
        }

        public List<String> getViolations() {
            return violations;
        }
    }
}
