package com.nuono.next.system.schema;

import com.nuono.next.infrastructure.mapper.ReleaseSchemaMigrationMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
        rejectUnknownHistoryRows(catalog, rows.keySet());
        for (int index = 0; index < catalog.size(); index++) {
            verify(catalog.get(index), rows.get(catalog.get(index).getKey()), index);
        }
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

    private static void rejectUnknownHistoryRows(
            List<ReleaseSchemaMigrationDescriptor> catalog,
            Set<String> historyKeys
    ) {
        Set<String> catalogKeys = new HashSet<>();
        for (ReleaseSchemaMigrationDescriptor migration : catalog) {
            catalogKeys.add(migration.getKey());
        }
        Set<String> unknown = new TreeSet<>(historyKeys);
        unknown.removeAll(catalogKeys);
        if (!unknown.isEmpty()) {
            throw blocked(
                    "database history migration(s) not present in this Jar catalog: "
                            + String.join(", ", unknown)
            );
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
        if (actual.getAttemptNo() == null
                || actual.getAttemptNo() < 1
                || !actual.getAttemptNo().equals(actual.getJoinedAttemptNo())
                || !actual.getChecksum().equals(actual.getAttemptChecksum())
                || !actual.getPostcheckChecksum().equals(
                actual.getAttemptPostcheckChecksum()
        )
                || !actual.getState().equals(actual.getAttemptState())) {
            throw blocked(
                    expected.getKey() + " history/attempt audit rows disagree"
            );
        }
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
