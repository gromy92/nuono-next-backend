package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopeProvider;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.ScheduleScopeSource;
import com.nuono.next.datapull.schedule.ScheduleSourcePage;
import com.nuono.next.datapull.schedule.ScheduleSourceScope;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Discovers every effective 1688 OpenAPI authorization as an account-level DP-10 scope. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class Ali1688Dp10ScopeSource implements DataPullScopeProvider, ScheduleScopeSource {

    private final Ali1688Dp10RuntimeMapper mapper;

    public Ali1688Dp10ScopeSource(Ali1688Dp10RuntimeMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<DataPullScope> listScopes() {
        List<Ali1688HistoricalOrderAuthorizationRow> rows = Objects.requireNonNull(
                mapper.listEffectiveOpenApiAuthorizations(),
                "DP-10 authorization scopes"
        );
        List<DataPullScope> scopes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Ali1688HistoricalOrderAuthorizationRow row : rows) {
            DataPullScope scope = toScope(Objects.requireNonNull(row, "DP-10 authorization"));
            if (!seen.add(scope.getStableScopeKey())) {
                throw new IllegalStateException("duplicate DP-10 authorization scope");
            }
            scopes.add(scope);
        }
        return List.copyOf(scopes);
    }

    @Override
    public Set<OperationCode> operations() {
        return Set.of(OperationCode.DP10);
    }

    @Override
    public ScheduleSourcePage readPage(
            OperationCode operationCode,
            String afterNativeCursorExclusive,
            Instant reconcileUntil,
            int limit
    ) {
        if (operationCode != OperationCode.DP10) {
            throw new IllegalArgumentException("Ali1688 source only supports DP10");
        }
        Objects.requireNonNull(reconcileUntil, "reconcileUntil");
        if (limit < 1 || limit > 64) {
            throw new IllegalArgumentException("scope page limit must be between 1 and 64");
        }
        Cursor after = Cursor.parse(afterNativeCursorExclusive);
        List<Ali1688HistoricalOrderAuthorizationRow> rows = Objects.requireNonNull(
                mapper.listEffectiveOpenApiAuthorizationsAfter(
                        after == null ? null : after.ownerUserId,
                        after == null ? null : after.authorizationId,
                        limit + 1
                ),
                "bounded DP-10 authorization scopes"
        );
        List<ScheduleSourceScope> items = new ArrayList<>(Math.min(rows.size(), limit));
        for (int index = 0; index < rows.size() && index < limit; index++) {
            Ali1688HistoricalOrderAuthorizationRow row = Objects.requireNonNull(
                    rows.get(index), "DP-10 authorization"
            );
            items.add(ScheduleSourceScope.scope(Cursor.from(row).encode(), toScope(row)));
        }
        return new ScheduleSourcePage(items, rows.size() > limit, limit);
    }

    public Optional<Ali1688HistoricalOrderAuthorizationRow> findForTask(DataPullTask task) {
        DataPullTask nonNull = Objects.requireNonNull(task, "task");
        Ali1688HistoricalOrderAuthorizationRow matched = null;
        for (Ali1688HistoricalOrderAuthorizationRow row : Objects.requireNonNull(
                mapper.listEffectiveOpenApiAuthorizations(),
                "DP-10 authorization scopes"
        )) {
            if (!Objects.equals(row.getOwnerUserId(), nonNull.getOwnerUserId())
                    || !Ali1688Dp10ScopeIdentity.accountKey(row).equals(nonNull.getAccountKey())
                    || !Ali1688Dp10ScopeIdentity.scopeKey(row).equals(nonNull.getScopeKey())) {
                continue;
            }
            if (matched != null) {
                throw new IllegalStateException("duplicate active DP-10 provider account identity");
            }
            matched = row;
        }
        return Optional.ofNullable(matched);
    }

    private DataPullScope toScope(Ali1688HistoricalOrderAuthorizationRow row) {
        Long ownerUserId = Objects.requireNonNull(row.getOwnerUserId(), "authorization.ownerUserId");
        return new DataPullScope(
                Ali1688Dp10ScopeIdentity.SCOPE_NAMESPACE,
                ownerUserId,
                null,
                Ali1688Dp10ScopeIdentity.accountKey(row),
                null,
                null,
                null,
                null,
                Ali1688Dp10ScopeIdentity.scopeKey(row)
        );
    }

    private static final class Cursor {
        private final long ownerUserId;
        private final long authorizationId;

        private Cursor(long ownerUserId, long authorizationId) {
            if (ownerUserId < 1L || authorizationId < 1L) {
                throw new IllegalArgumentException("DP10 native cursor identities must be positive");
            }
            this.ownerUserId = ownerUserId;
            this.authorizationId = authorizationId;
        }

        private static Cursor from(Ali1688HistoricalOrderAuthorizationRow row) {
            return new Cursor(
                    Objects.requireNonNull(row.getOwnerUserId(), "authorization.ownerUserId"),
                    Objects.requireNonNull(row.getId(), "authorization.id")
            );
        }

        private static Cursor parse(String value) {
            if (value == null) return null;
            String[] fields = value.split(":", -1);
            if (fields.length != 3 || !"ALI1".equals(fields[0])) {
                throw new IllegalArgumentException("invalid DP10 native source cursor");
            }
            try {
                return new Cursor(Long.parseLong(fields[1]), Long.parseLong(fields[2]));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("invalid DP10 native source cursor", invalid);
            }
        }

        private String encode() {
            return "ALI1:" + ownerUserId + ":" + authorizationId;
        }
    }
}
