package com.nuono.next.procurement.aliorder;

import com.nuono.next.infrastructure.mapper.Ali1688OpenApiAuthorizationMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;

/** Persists a probe-time token refresh only when the selected authorization is unchanged. */
final class Ali1688Dp10OpenApiProbeAuthorizationUpdater
        implements Ali1688OpenApiAuthorizationMapper {
    private static final String UPDATE_SQL = String.join(" ",
            "UPDATE procurement_ali1688_order_authorization",
            "SET account_label = ?, status = ?, scope_summary = ?,",
            "access_token_cipher = ?, refresh_token_cipher = ?, expires_at = ?,",
            "revoked_at = NULL, updated_by = ?, gmt_updated = NOW()",
            "WHERE id = ? AND owner_user_id = ?",
            "AND provider_code = 'ALI1688_OPEN_API'",
            "AND BINARY provider_account_id = BINARY ?",
            "AND status = 'authorized' AND revoked_at IS NULL AND is_deleted = b'0'",
            "AND access_token_cipher <=> ? AND refresh_token_cipher <=> ?",
            "AND expires_at <=> ? AND gmt_updated <=> ?"
    );

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private final ConnectionFactory connections;
    private final Expected expected;

    static Ali1688Dp10OpenApiProbeAuthorizationUpdater create(
            Ali1688Dp10OpenApiProbeEnvironment environment,
            Ali1688Dp10OpenApiProbeAuthorizationSource.Selection selection
    ) throws Exception {
        String url = environment.require("NUONO_NEXT_DB_URL");
        if (!url.startsWith("jdbc:mysql:")) {
            throw new IllegalStateException("PROBE_DB_URL_INVALID");
        }
        String username = environment.require("NUONO_NEXT_DB_USERNAME");
        String password = environment.require("NUONO_NEXT_DB_PASSWORD");
        Class.forName("com.mysql.cj.jdbc.Driver");
        DriverManager.setLoginTimeout(5);
        return new Ali1688Dp10OpenApiProbeAuthorizationUpdater(
                () -> DriverManager.getConnection(url, username, password),
                Expected.capture(selection)
        );
    }

    Ali1688Dp10OpenApiProbeAuthorizationUpdater(
            ConnectionFactory connections,
            Ali1688Dp10OpenApiProbeAuthorizationSource.Selection selection
    ) {
        this(connections, Expected.capture(selection));
    }

    private Ali1688Dp10OpenApiProbeAuthorizationUpdater(
            ConnectionFactory connections,
            Expected expected
    ) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.expected = Objects.requireNonNull(expected, "expected");
    }

    @Override
    public int updateAuthorizationTokens(Ali1688HistoricalOrderAuthorizationRow updated) {
        expected.requireSameIdentity(updated);
        try (Connection connection = connections.open()) {
            connection.setReadOnly(false);
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
                statement.setQueryTimeout(5);
                bindUpdate(statement, updated);
                int affected = statement.executeUpdate();
                if (affected == 1) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                return affected;
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("PROBE_AUTH_REFRESH_PERSIST_FAILED", failure);
        }
    }

    private void bindUpdate(
            PreparedStatement statement,
            Ali1688HistoricalOrderAuthorizationRow updated
    ) throws SQLException {
        statement.setString(1, updated.getAccountLabel());
        statement.setString(2, updated.getStatus());
        statement.setString(3, updated.getScopeSummary());
        statement.setString(4, updated.getAccessTokenCipher());
        statement.setString(5, updated.getRefreshTokenCipher());
        statement.setTimestamp(6, timestamp(updated.getExpiresAt()));
        setNullableLong(statement, 7, updated.getUpdatedBy());
        statement.setLong(8, expected.id);
        statement.setLong(9, expected.ownerUserId);
        statement.setString(10, expected.providerAccountId);
        statement.setString(11, expected.accessTokenCipher);
        statement.setString(12, expected.refreshTokenCipher);
        statement.setTimestamp(13, timestamp(expected.expiresAt));
        statement.setTimestamp(14, expected.revision);
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.BIGINT);
        else statement.setLong(index, value);
    }

    private Timestamp timestamp(java.time.LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static final class Expected {
        private final long id;
        private final long ownerUserId;
        private final String providerAccountId;
        private final String accessTokenCipher;
        private final String refreshTokenCipher;
        private final java.time.LocalDateTime expiresAt;
        private final Timestamp revision;

        private Expected(
                Ali1688HistoricalOrderAuthorizationRow row,
                Timestamp revision
        ) {
            this.id = positive(row.getId(), "PROBE_AUTHORIZATION_ID_INVALID");
            this.ownerUserId = positive(
                    row.getOwnerUserId(),
                    "PROBE_AUTHORIZATION_OWNER_INVALID"
            );
            this.providerAccountId = requireText(
                    row.getProviderAccountId(),
                    "PROBE_AUTHORIZATION_ACCOUNT_INVALID"
            );
            this.accessTokenCipher = row.getAccessTokenCipher();
            this.refreshTokenCipher = row.getRefreshTokenCipher();
            this.expiresAt = row.getExpiresAt();
            this.revision = Objects.requireNonNull(
                    revision,
                    "PROBE_AUTHORIZATION_REVISION_MISSING"
            );
        }

        static Expected capture(
                Ali1688Dp10OpenApiProbeAuthorizationSource.Selection selection
        ) {
            Objects.requireNonNull(selection, "selection");
            return new Expected(selection.authorization(), selection.revision());
        }

        void requireSameIdentity(Ali1688HistoricalOrderAuthorizationRow updated) {
            if (updated == null
                    || !Objects.equals(updated.getId(), id)
                    || !Objects.equals(updated.getOwnerUserId(), ownerUserId)
                    || !"ALI1688_OPEN_API".equals(updated.getProviderCode())
                    || !providerAccountId.equals(updated.getProviderAccountId())) {
                throw new IllegalStateException("PROBE_AUTH_REFRESH_IDENTITY_CHANGED");
            }
        }

        private static long positive(Long value, String code) {
            if (value == null || value <= 0L) throw new IllegalStateException(code);
            return value;
        }

        private static String requireText(String value, String code) {
            if (value == null || value.trim().isEmpty()) throw new IllegalStateException(code);
            return value.trim();
        }
    }
}
