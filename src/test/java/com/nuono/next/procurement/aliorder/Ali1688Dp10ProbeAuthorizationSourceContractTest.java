package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class Ali1688Dp10ProbeAuthorizationSourceContractTest {

    @Test
    void canarySelectionUsesTheRuntimeScopePredicateBeforeCredentialValidation() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/procurement/aliorder/"
                        + "Ali1688Dp10OpenApiProbeAuthorizationSource.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("auth.provider_code = 'ALI1688_OPEN_API'")
                .contains("auth.status = 'authorized'")
                .contains("auth.revoked_at IS NULL")
                .contains("auth.is_deleted = b'0'")
                .contains("LIMIT 2")
                .contains("PROBE_AUTHORIZATION_DUPLICATE")
                .doesNotContain("auth.access_token_cipher IS NOT NULL")
                .doesNotContain("auth.expires_at >");
    }

    @Test
    void aSecondRuntimeEffectiveRowFailsEvenWhenItsCredentialsWouldBeUnusable()
            throws Exception {
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true, true);
        when(rows.getLong("id")).thenReturn(1L);
        when(rows.getLong("owner_user_id")).thenReturn(307L);
        when(rows.getString("provider_code")).thenReturn("ALI1688_OPEN_API");
        when(rows.getString("provider_account_id")).thenReturn("member-307");
        when(rows.getString("status")).thenReturn("authorized");
        when(rows.getString("access_token_cipher")).thenReturn("usable", null);

        assertThatThrownBy(
                () -> new Ali1688Dp10OpenApiProbeAuthorizationSource().selectUnique(rows)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("PROBE_AUTHORIZATION_DUPLICATE");
    }
}
