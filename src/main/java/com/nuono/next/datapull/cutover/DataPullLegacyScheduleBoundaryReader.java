package com.nuono.next.datapull.cutover;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullSchedule;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only legacy task boundary retained when zero-fact executions are superseded. */
final class DataPullLegacyScheduleBoundaryReader {
    private static final String SQL = "SELECT owner_user_id,store_code,site_code,"
            + "data_domain,MIN(target_date_to),SUM(target_date_to IS NULL) "
            + "FROM noon_pull_task WHERE status IN ('QUEUED','RUNNING','BLOCKED_AUTH') "
            + "AND trigger_mode='SCHEDULED_DAILY' AND is_deleted=b'0' "
            + "AND data_domain IN ('SALES','ORDER','FINANCE_TRANSACTION',"
            + "'NOON_ADVERTISING','OFFICIAL_WAREHOUSE_FBN_RECEIVED') "
            + "GROUP BY owner_user_id,store_code,site_code,data_domain";

    Map<OperationCode, Map<String, LocalDateTime>> read(
            Connection connection,
            List<DataPullScope> noonScopes
    ) throws Exception {
        List<BoundaryRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(SQL)) {
            while (result.next()) {
                Date targetDateTo = result.getDate(5);
                rows.add(new BoundaryRow(
                        result.getLong(1),
                        result.getString(2),
                        result.getString(3),
                        result.getString(4),
                        targetDateTo == null ? null : targetDateTo.toLocalDate(),
                        result.getLong(6)
                ));
            }
        }
        return resolve(rows, noonScopes);
    }

    Map<OperationCode, Map<String, LocalDateTime>> resolve(
            List<BoundaryRow> rows,
            List<DataPullScope> noonScopes
    ) {
        EnumMap<OperationCode, Map<String, LocalDateTime>> result =
                new EnumMap<>(OperationCode.class);
        for (BoundaryRow row : List.copyOf(rows)) {
            OperationCode operation = operation(row.dataDomain);
            if (row.nullTargetCount != 0L || row.targetDateTo == null) {
                throw new IllegalStateException("DP_CUTOVER_LEGACY_WINDOW_MISSING");
            }
            LocalDateTime boundary = row.targetDateTo.plusDays(1)
                    .atStartOfDay(DataPullSchedule.ZONE_ID)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
            int matched = 0;
            for (DataPullScope scope : noonScopes) {
                if (!row.matches(scope)) continue;
                result.computeIfAbsent(operation, ignored -> new HashMap<>())
                        .merge(scope.getStableScopeKey(), boundary,
                                (left, right) -> left.isBefore(right) ? left : right);
                matched++;
            }
            if (matched == 0) {
                throw new IllegalStateException("DP_CUTOVER_LEGACY_SCOPE_UNMAPPED");
            }
        }
        EnumMap<OperationCode, Map<String, LocalDateTime>> copied =
                new EnumMap<>(OperationCode.class);
        result.forEach((operation, values) -> copied.put(operation, Map.copyOf(values)));
        return Map.copyOf(copied);
    }

    private OperationCode operation(String dataDomain) {
        switch (Objects.requireNonNull(dataDomain, "legacy data domain")) {
            case "SALES": return OperationCode.DP01;
            case "ORDER": return OperationCode.DP02;
            case "FINANCE_TRANSACTION": return OperationCode.DP03;
            case "NOON_ADVERTISING": return OperationCode.DP06;
            case "OFFICIAL_WAREHOUSE_FBN_RECEIVED": return OperationCode.DP07B;
            default: throw new IllegalStateException("DP_CUTOVER_LEGACY_DOMAIN_UNMAPPED");
        }
    }

    static final class BoundaryRow {
        private final long ownerUserId;
        private final String storeCode;
        private final String siteCode;
        private final String dataDomain;
        private final LocalDate targetDateTo;
        private final long nullTargetCount;

        BoundaryRow(
                long ownerUserId,
                String storeCode,
                String siteCode,
                String dataDomain,
                LocalDate targetDateTo,
                long nullTargetCount
        ) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.dataDomain = dataDomain;
            this.targetDateTo = targetDateTo;
            this.nullTargetCount = nullTargetCount;
        }

        private boolean matches(DataPullScope scope) {
            return ownerUserId == scope.getOwnerUserId()
                    && Objects.equals(storeCode, scope.getStoreCode())
                    && Objects.equals(siteCode, scope.getSiteCode());
        }
    }
}
