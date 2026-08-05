package com.nuono.next.procurement.aliorder.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class Ali1688Dp10ScopeSourceTest {

    @Test
    void everyEffectiveOpenApiAuthorizationBecomesOneStableAccountScope() {
        Ali1688HistoricalOrderAuthorizationRow first = authorization(91_001L, 307L, "member-a");
        Ali1688HistoricalOrderAuthorizationRow second = authorization(91_002L, 307L, "member-b");
        Ali1688HistoricalOrderAuthorizationRow third = authorization(91_003L, 401L, "member-c");
        Ali1688Dp10RuntimeMapper mapper = mock(Ali1688Dp10RuntimeMapper.class);
        when(mapper.listEffectiveOpenApiAuthorizations()).thenReturn(List.of(first, second, third));

        List<DataPullScope> scopes = new Ali1688Dp10ScopeSource(mapper).listScopes();

        assertEquals(3, scopes.size());
        assertEquals(Ali1688Dp10ScopeIdentity.accountKey(first), scopes.get(0).getAccountKey());
        assertTrue(Ali1688Dp10ScopeIdentity.isAccountKey(scopes.get(0).getAccountKey()));
        assertEquals(Ali1688Dp10ScopeIdentity.scopeKey(first), scopes.get(0).getStableScopeKey());
        assertEquals(Ali1688Dp10ScopeIdentity.scopeKey(second), scopes.get(1).getStableScopeKey());
        assertFalse(
                scopes.get(0).getStableScopeKey().equals(scopes.get(1).getStableScopeKey()),
                "two authorizations under one owner remain separate scopes"
        );
    }

    @Test
    void reauthorizationRowIdDoesNotChangeExternalAccountOrScopeIdentity() {
        Ali1688HistoricalOrderAuthorizationRow before = authorization(
                91_001L,
                307L,
                "member-a"
        );
        Ali1688HistoricalOrderAuthorizationRow after = authorization(
                99_999L,
                307L,
                "member-a"
        );

        assertEquals(
                Ali1688Dp10ScopeIdentity.accountKey(before),
                Ali1688Dp10ScopeIdentity.accountKey(after)
        );
        assertEquals(
                Ali1688Dp10ScopeIdentity.scopeKey(before),
                Ali1688Dp10ScopeIdentity.scopeKey(after)
        );
    }

    @Test
    void authoritativeScopeQueryHasNoBusinessLimitAndExcludesRevokedRows() throws Exception {
        String sql = selectSql("listEffectiveOpenApiAuthorizations");

        assertTrue(sql.contains("provider_code = 'ALI1688_OPEN_API'"));
        assertTrue(sql.contains("status = 'authorized'"));
        assertTrue(sql.contains("revoked_at IS NULL"));
        assertTrue(sql.contains("is_deleted = b'0'"));
        assertFalse(sql.toUpperCase().contains(" LIMIT "));
    }

    @Test
    void taskFactGuardLocksOnlyTheCurrentlyLiveDp10Lease() throws Exception {
        String sql = selectSql("lockTask");

        assertTrue(sql.contains("operation_code = 'DP10'"));
        assertTrue(sql.contains("state = 'RUNNING'"));
        assertTrue(sql.contains("lease_until > UTC_TIMESTAMP(6)"));
        assertTrue(sql.contains("FOR UPDATE"));
    }

    private String selectSql(String methodName) throws Exception {
        Method method = Arrays.stream(Ali1688Dp10RuntimeMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }

    private Ali1688HistoricalOrderAuthorizationRow authorization(
            long id,
            long ownerUserId,
            String providerAccountId
    ) {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(id);
        row.setOwnerUserId(ownerUserId);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId(providerAccountId);
        row.setStatus("authorized");
        return row;
    }
}
