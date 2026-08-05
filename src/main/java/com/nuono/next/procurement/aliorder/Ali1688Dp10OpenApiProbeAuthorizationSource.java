package com.nuono.next.procurement.aliorder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/** Selects the managed canary authorization and an optional local order identity, read-only. */
final class Ali1688Dp10OpenApiProbeAuthorizationSource {
    private static final String CANARY_OWNER =
            "NUONO_DP10_OPEN_API_PROBE_CANARY_OWNER_USER_ID";
    private static final String CANARY_ACCOUNT =
            "NUONO_DP10_OPEN_API_PROBE_CANARY_PROVIDER_ACCOUNT_ID";
    private static final String SQL = String.join(" ",
            "SELECT auth.id, auth.owner_user_id, auth.provider_code,",
            "auth.provider_account_id, auth.status, auth.access_token_cipher,",
            "auth.expires_at, auth.revoked_at,",
            "(SELECT header.provider_order_no",
            " FROM procurement_ali1688_order_header header",
            " WHERE header.authorization_id = auth.id",
            "   AND header.is_deleted = b'0'",
            "   AND header.provider_order_no IS NOT NULL",
            "   AND TRIM(header.provider_order_no) <> ''",
            " ORDER BY header.gmt_updated DESC, header.id DESC LIMIT 1) AS probe_order_no",
            "FROM procurement_ali1688_order_authorization auth",
            "WHERE auth.provider_code = 'ALI1688_OPEN_API'",
            "  AND auth.owner_user_id = ?",
            "  AND BINARY auth.provider_account_id = BINARY ?",
            "  AND auth.status = 'authorized'",
            "  AND auth.revoked_at IS NULL",
            "  AND auth.is_deleted = b'0'",
            "ORDER BY auth.gmt_updated DESC, auth.id DESC LIMIT 2"
    );

    Selection select(Ali1688Dp10OpenApiProbeEnvironment environment) throws Exception {
        long canaryOwner = canaryOwner(environment.require(CANARY_OWNER));
        String canaryAccount = canaryAccount(environment.require(CANARY_ACCOUNT));
        String url = environment.require("NUONO_NEXT_DB_URL");
        if (!url.startsWith("jdbc:mysql:")) {
            throw new IllegalStateException("PROBE_DB_URL_INVALID");
        }
        String username = environment.require("NUONO_NEXT_DB_USERNAME");
        String password = environment.require("NUONO_NEXT_DB_PASSWORD");
        Class.forName("com.mysql.cj.jdbc.Driver");
        DriverManager.setLoginTimeout(5);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(SQL)) {
                statement.setQueryTimeout(5);
                statement.setLong(1, canaryOwner);
                statement.setString(2, canaryAccount);
                try (ResultSet result = statement.executeQuery()) {
                    return selectUnique(result);
                }
            }
        }
    }

    Selection selectUnique(ResultSet result) throws SQLException {
        if (!result.next()) {
            throw new IllegalStateException("PROBE_AUTHORIZATION_UNAVAILABLE");
        }
        Selection selection = new Selection(mapAuthorization(result), trim(
                result.getString("probe_order_no")
        ));
        if (result.next()) {
            throw new IllegalStateException("PROBE_AUTHORIZATION_DUPLICATE");
        }
        return selection;
    }

    private long canaryOwner(String value) {
        try {
            long owner = Long.parseLong(value);
            if (owner > 0L) return owner;
        } catch (NumberFormatException ignored) {
            // Fail below without reflecting the configured identity.
        }
        throw new IllegalStateException("PROBE_CANARY_OWNER_INVALID");
    }

    private String canaryAccount(String value) {
        if (value.length() > 120 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("PROBE_CANARY_ACCOUNT_INVALID");
        }
        return value;
    }

    private Ali1688HistoricalOrderAuthorizationRow mapAuthorization(ResultSet result)
            throws SQLException {
        Ali1688HistoricalOrderAuthorizationRow row =
                new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(result.getLong("id"));
        row.setOwnerUserId(result.getLong("owner_user_id"));
        row.setProviderCode(result.getString("provider_code"));
        row.setProviderAccountId(result.getString("provider_account_id"));
        row.setStatus(result.getString("status"));
        row.setAccessTokenCipher(result.getString("access_token_cipher"));
        row.setExpiresAt(localDateTime(result.getTimestamp("expires_at")));
        row.setRevokedAt(localDateTime(result.getTimestamp("revoked_at")));
        return row;
    }

    private java.time.LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    static final class Selection {
        private final Ali1688HistoricalOrderAuthorizationRow authorization;
        private final String providerOrderNo;

        private Selection(
                Ali1688HistoricalOrderAuthorizationRow authorization,
                String providerOrderNo
        ) {
            this.authorization = authorization;
            this.providerOrderNo = providerOrderNo;
        }

        Ali1688HistoricalOrderAuthorizationRow authorization() { return authorization; }
        String providerOrderNo() { return providerOrderNo; }
    }
}
