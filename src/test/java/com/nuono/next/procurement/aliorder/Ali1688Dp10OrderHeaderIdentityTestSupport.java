package com.nuono.next.procurement.aliorder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.Ali1688Dp10FactLookupMapper;
import java.util.List;

final class Ali1688Dp10OrderHeaderIdentityTestSupport {
    private Ali1688Dp10OrderHeaderIdentityTestSupport() {}

    static void stubCanonicalOrder(
            Ali1688Dp10FactLookupMapper mapper,
            Ali1688Dp10OrderHeaderIdentityRow identity
    ) {
        when(mapper.selectCanonicalOrderHeadersForUpdate(
                eq(307L), eq("ALI1688_OPEN_API"), eq("member-307"),
                eq("ORDER-1"), anyString())).thenReturn(List.of(identity));
    }

    static Ali1688Dp10OrderHeaderIdentityRow activeIdentity(long id, String naturalKey) {
        return identity(id, naturalKey, false);
    }

    static Ali1688Dp10OrderHeaderIdentityRow deletedIdentity(long id, String naturalKey) {
        return identity(id, naturalKey, true);
    }

    private static Ali1688Dp10OrderHeaderIdentityRow identity(
            long id, String naturalKey, boolean deleted
    ) {
        Ali1688Dp10OrderHeaderIdentityRow row =
                new Ali1688Dp10OrderHeaderIdentityRow();
        row.setId(id);
        row.setAuthorizationId(91_001L);
        row.setOrderNaturalKey(naturalKey);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId("member-307");
        row.setProviderOrderNo("ORDER-1");
        row.setDeleted(deleted);
        return row;
    }
}
