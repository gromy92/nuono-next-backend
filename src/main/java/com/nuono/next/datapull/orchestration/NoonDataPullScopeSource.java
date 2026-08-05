package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.ScheduleScopeSource;
import com.nuono.next.datapull.schedule.ScheduleSourcePage;
import com.nuono.next.datapull.schedule.ScheduleSourceScope;
import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Maps authoritative Noon bindings to immutable runtime scopes. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class NoonDataPullScopeSource implements DataPullScopeProvider, ScheduleScopeSource {

    private static final String SCOPE_NAMESPACE = "NOON";

    private final NoonDataPullScopeMapper mapper;

    public NoonDataPullScopeSource(NoonDataPullScopeMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<DataPullScope> listScopes() {
        List<NoonDataPullScopeRow> rows = Objects.requireNonNull(
                mapper.listActiveBoundScopes(),
                "active Noon scopes"
        );
        List<DataPullScope> scopes = new ArrayList<>(rows.size());
        Set<String> seen = new HashSet<>();
        for (NoonDataPullScopeRow row : rows) {
            NoonDataPullScopeRow sourceRow = Objects.requireNonNull(row, "scope row");
            requireSourceRowIdentity(sourceRow);
            DataPullScope scope = toScope(sourceRow);
            if (!seen.add(scope.getStableScopeKey())) {
                throw new IllegalStateException(
                        "DP_NOON_SCOPE_DUPLICATE_ACTIVE_SOURCE:" + scope.getStableScopeKey()
                );
            }
            scopes.add(scope);
        }
        return List.copyOf(scopes);
    }

    @Override
    public Set<OperationCode> operations() {
        return EnumSet.of(
                OperationCode.DP01, OperationCode.DP02, OperationCode.DP03,
                OperationCode.DP04, OperationCode.DP05, OperationCode.DP06,
                OperationCode.DP07A, OperationCode.DP07B
        );
    }

    @Override
    public ScheduleSourcePage readPage(
            OperationCode operationCode,
            String afterNativeCursorExclusive,
            Instant reconcileUntil,
            int limit
    ) {
        if (!operations().contains(Objects.requireNonNull(operationCode, "operationCode"))) {
            throw new IllegalArgumentException("operation is not a Noon scope operation");
        }
        Objects.requireNonNull(reconcileUntil, "reconcileUntil");
        requireLimit(limit);
        Cursor after = Cursor.parse(afterNativeCursorExclusive);
        List<NoonDataPullScopeRow> rows = Objects.requireNonNull(
                mapper.listActiveBoundScopesAfter(
                        after == null ? null : after.ownerUserId,
                        after == null ? null : after.logicalStoreId,
                        after == null ? null : after.logicalStoreSiteId,
                        after == null ? null : after.userProjectId,
                        after == null ? null : after.userStoreId,
                        limit + 1
                ),
                "bounded active Noon scopes"
        );
        List<ScheduleSourceScope> items = new ArrayList<>(Math.min(rows.size(), limit));
        for (int index = 0; index < rows.size() && index < limit; index++) {
            NoonDataPullScopeRow row = Objects.requireNonNull(rows.get(index), "scope row");
            requireSourceRowIdentity(row);
            items.add(ScheduleSourceScope.scope(Cursor.from(row).encode(), toScope(row)));
        }
        return new ScheduleSourcePage(items, rows.size() > limit, limit);
    }

    private DataPullScope toScope(NoonDataPullScopeRow row) {
        Long ownerUserId = Objects.requireNonNull(row.getOwnerUserId(), "ownerUserId");
        Long logicalStoreId = Objects.requireNonNull(row.getLogicalStoreId(), "logicalStoreId");
        String projectCode = requireText(row.getProjectCode(), "projectCode");
        String storeCode = requireText(row.getStoreCode(), "storeCode");
        String siteCode = requireText(row.getSiteCode(), "siteCode");
        return new DataPullScope(
                SCOPE_NAMESPACE,
                ownerUserId,
                logicalStoreId,
                projectCode,
                null,
                projectCode,
                storeCode,
                siteCode,
                DataPullScopeKey.from(
                        SCOPE_NAMESPACE,
                        String.valueOf(ownerUserId),
                        String.valueOf(logicalStoreId),
                        projectCode,
                        storeCode,
                        siteCode
                )
        );
    }

    private static void requireSourceRowIdentity(NoonDataPullScopeRow row) {
        positive(row.getLogicalStoreId(), "logicalStoreId");
        positive(row.getLogicalStoreSiteId(), "logicalStoreSiteId");
        positive(row.getUserProjectId(), "userProjectId");
        positive(row.getUserStoreId(), "userStoreId");
    }

    private static long positive(Long value, String name) {
        Long nonNull = Objects.requireNonNull(value, name);
        if (nonNull <= 0L) {
            throw new IllegalArgumentException(name + " must identify one active source row");
        }
        return nonNull;
    }

    private static String requireText(String value, String name) {
        String nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
            throw new IllegalArgumentException(name + " must be a stable non-blank value");
        }
        return nonNull;
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 64) {
            throw new IllegalArgumentException("scope page limit must be between 1 and 64");
        }
    }

    private static final class Cursor {
        private final long ownerUserId;
        private final long logicalStoreId;
        private final long logicalStoreSiteId;
        private final long userProjectId;
        private final long userStoreId;

        private Cursor(long owner, long store, long site, long project, long userStore) {
            ownerUserId = owner;
            logicalStoreId = store;
            logicalStoreSiteId = site;
            userProjectId = project;
            userStoreId = userStore;
        }

        private static Cursor from(NoonDataPullScopeRow row) {
            return new Cursor(
                    positive(row.getOwnerUserId(), "ownerUserId"),
                    positive(row.getLogicalStoreId(), "logicalStoreId"),
                    positive(row.getLogicalStoreSiteId(), "logicalStoreSiteId"),
                    positive(row.getUserProjectId(), "userProjectId"),
                    positive(row.getUserStoreId(), "userStoreId")
            );
        }

        private static Cursor parse(String value) {
            if (value == null) return null;
            String[] fields = value.split(":", -1);
            if (fields.length != 6 || !"NOON1".equals(fields[0])) {
                throw new IllegalArgumentException("invalid Noon native source cursor");
            }
            try {
                return new Cursor(
                        Long.parseLong(fields[1]), Long.parseLong(fields[2]),
                        Long.parseLong(fields[3]), Long.parseLong(fields[4]),
                        Long.parseLong(fields[5])
                );
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("invalid Noon native source cursor", invalid);
            }
        }

        private String encode() {
            return "NOON1:" + ownerUserId + ":" + logicalStoreId + ":"
                    + logicalStoreSiteId + ":" + userProjectId + ":" + userStoreId;
        }
    }
}
