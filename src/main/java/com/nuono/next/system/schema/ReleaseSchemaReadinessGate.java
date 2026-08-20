package com.nuono.next.system.schema;

import com.nuono.next.infrastructure.mapper.ReleaseSchemaMigrationMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
@ConditionalOnProperty(
        name = "nuono.schema-release-gate.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReleaseSchemaReadinessGate implements InitializingBean {
    private static final Pattern MIGRATION_KEY = Pattern.compile(
            "^(\\d{3})_[a-z0-9_]+\\.sql$"
    );
    private final ReleaseSchemaMigrationMapper mapper;
    private final ReleaseSchemaCatalogLoader catalogLoader;

    public ReleaseSchemaReadinessGate(
            ReleaseSchemaMigrationMapper mapper,
            ReleaseSchemaCatalogLoader catalogLoader
    ) {
        this.mapper = mapper;
        this.catalogLoader = catalogLoader;
    }

    @Override
    public void afterPropertiesSet() {
        List<ReleaseSchemaMigrationDescriptor> catalog = catalogLoader.load();
        List<ReleaseSchemaMigrationRow> history;
        long orphanAttempts;
        try {
            history = mapper.selectMigrationHistory();
            orphanAttempts = mapper.countOrphanAttempts();
        } catch (RuntimeException error) {
            throw blocked(
                    "migration history cannot be read; its tables may be missing",
                    error
            );
        }
        if (orphanAttempts != 0) {
            throw blocked(
                    "database history contains "
                            + orphanAttempts
                            + " orphan attempt row(s)"
            );
        }
        Map<String, ReleaseSchemaMigrationRow> rows = index(history);
        for (int index = 0; index < catalog.size(); index++) {
            verify(catalog.get(index), rows.get(catalog.get(index).getKey()), index);
        }
        verifyFutureHistorySuffix(catalog, rows);
    }

    private static Map<String, ReleaseSchemaMigrationRow> index(
            List<ReleaseSchemaMigrationRow> rows
    ) {
        if (rows == null) {
            throw blocked("database history query returned no result");
        }
        Map<String, ReleaseSchemaMigrationRow> indexed = new HashMap<>();
        for (ReleaseSchemaMigrationRow row : rows) {
            if (row.getMigrationKey() == null
                    || indexed.put(row.getMigrationKey(), row) != null) {
                throw blocked("database history contains a duplicate/empty key");
            }
        }
        return indexed;
    }

    private static void verifyFutureHistorySuffix(
            List<ReleaseSchemaMigrationDescriptor> catalog,
            Map<String, ReleaseSchemaMigrationRow> history
    ) {
        Set<String> catalogKeys = new HashSet<>();
        for (ReleaseSchemaMigrationDescriptor migration : catalog) {
            catalogKeys.add(migration.getKey());
        }
        int expectedOrder = catalog.get(catalog.size() - 1).getOrder() + 1;
        Map<Integer, ReleaseSchemaMigrationRow> suffix = new TreeMap<>();
        for (Map.Entry<String, ReleaseSchemaMigrationRow> entry : history.entrySet()) {
            if (catalogKeys.contains(entry.getKey())) continue;
            Matcher matcher = MIGRATION_KEY.matcher(entry.getKey());
            if (!matcher.matches()) {
                throw blocked("database history contains an invalid future migration key");
            }
            int order = Integer.parseInt(matcher.group(1));
            if (order < expectedOrder || suffix.put(order, entry.getValue()) != null) {
                throw blocked(
                        "database history migration is not a future catalog suffix: "
                                + entry.getKey()
                );
            }
        }
        for (Map.Entry<Integer, ReleaseSchemaMigrationRow> entry : suffix.entrySet()) {
            if (entry.getKey() != expectedOrder || !validAppliedAudit(entry.getValue())) {
                throw blocked("database future migration history suffix is invalid");
            }
            expectedOrder++;
        }
    }

    private static void verify(
            ReleaseSchemaMigrationDescriptor expected,
            ReleaseSchemaMigrationRow actual,
            int index
    ) {
        if (actual == null) {
            throw blocked(expected.getKey() + " is missing");
        }
        boolean validState = "APPLIED".equals(actual.getState())
                || (
                index == 0
                        && "BOOTSTRAP".equals(expected.getKind())
                        && "BASELINED".equals(actual.getState())
        );
        if (!validState) {
            throw blocked(
                    expected.getKey() + " has state " + actual.getState()
            );
        }
        if (!expected.getChecksum().equals(actual.getChecksum())
                || !expected.getPostcheckChecksum().equals(
                actual.getPostcheckChecksum()
        )) {
            throw blocked(expected.getKey() + " checksum differs from this Jar");
        }
        if (!validAudit(actual)) {
            throw blocked(
                    expected.getKey() + " history/attempt audit rows disagree"
            );
        }
    }

    private static boolean validAppliedAudit(ReleaseSchemaMigrationRow row) {
        return "APPLIED".equals(row.getState())
                && row.getChecksum() != null
                && row.getChecksum().matches("[0-9a-f]{64}")
                && row.getPostcheckChecksum() != null
                && row.getPostcheckChecksum().matches("[0-9a-f]{64}")
                && validAudit(row);
    }

    private static boolean validAudit(ReleaseSchemaMigrationRow row) {
        return row.getAttemptNo() != null
                && row.getAttemptNo() >= 1
                && row.getAttemptNo().equals(row.getJoinedAttemptNo())
                && row.getChecksum() != null
                && row.getChecksum().equals(row.getAttemptChecksum())
                && row.getPostcheckChecksum() != null
                && row.getPostcheckChecksum().equals(row.getAttemptPostcheckChecksum())
                && row.getState() != null
                && row.getState().equals(row.getAttemptState());
    }

    private static IllegalStateException blocked(String detail) {
        return blocked(detail, null);
    }

    private static IllegalStateException blocked(
            String detail,
            RuntimeException cause
    ) {
        return new IllegalStateException(
                "release schema is not ready for this application Jar: "
                        + detail
                        + "; run the governed Database Migration Module first",
                cause
        );
    }
}
