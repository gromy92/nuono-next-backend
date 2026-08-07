package com.nuono.next.procurement.aliorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class Ali1688Dp10OpenApiProbeAuthorizationUpdaterTest {

    @Test
    void commitsOnlyTheExactSelectedAuthorizationRevision() throws Exception {
        Ali1688Dp10OpenApiProbeAuthorizationSource.Selection selection = selection();
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        Ali1688Dp10OpenApiProbeAuthorizationUpdater updater =
                new Ali1688Dp10OpenApiProbeAuthorizationUpdater(
                        () -> connection,
                        selection
                );
        Ali1688HistoricalOrderAuthorizationRow row = selection.authorization();
        row.setAccessTokenCipher("new-access");
        row.setRefreshTokenCipher("new-refresh");
        row.setExpiresAt(LocalDateTime.of(2026, 9, 1, 0, 0));

        assertEquals(1, updater.updateAuthorizationTokens(row));

        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(statement).setString(11, "old-access");
        verify(statement).setString(12, "old-refresh");
        verify(statement).setTimestamp(
                14,
                Timestamp.valueOf("2026-08-07 14:00:00")
        );
    }

    @Test
    void rollsBackWhenTheAuthorizationChangedConcurrently() throws Exception {
        Ali1688Dp10OpenApiProbeAuthorizationSource.Selection selection = selection();
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        Ali1688Dp10OpenApiProbeAuthorizationUpdater updater =
                new Ali1688Dp10OpenApiProbeAuthorizationUpdater(
                        () -> connection,
                        selection
                );

        assertEquals(0, updater.updateAuthorizationTokens(selection.authorization()));

        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    private Ali1688Dp10OpenApiProbeAuthorizationSource.Selection selection()
            throws Exception {
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true, false);
        when(rows.getLong("id")).thenReturn(91004L);
        when(rows.getLong("owner_user_id")).thenReturn(307L);
        when(rows.getString("provider_code")).thenReturn("ALI1688_OPEN_API");
        when(rows.getString("provider_account_id")).thenReturn("b2b-793110249");
        when(rows.getString("status")).thenReturn("authorized");
        when(rows.getString("access_token_cipher")).thenReturn("old-access");
        when(rows.getString("refresh_token_cipher")).thenReturn("old-refresh");
        when(rows.getTimestamp("expires_at"))
                .thenReturn(Timestamp.valueOf("2026-08-03 03:01:39"));
        when(rows.getTimestamp("authorization_revision"))
                .thenReturn(Timestamp.valueOf("2026-08-07 14:00:00"));
        when(rows.wasNull()).thenReturn(true);
        return new Ali1688Dp10OpenApiProbeAuthorizationSource().selectUnique(rows);
    }
}
