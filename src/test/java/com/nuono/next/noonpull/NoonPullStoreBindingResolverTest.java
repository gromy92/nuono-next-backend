package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncStoreRecord;
import org.junit.jupiter.api.Test;

class NoonPullStoreBindingResolverTest {

    @Test
    void persistedCookieAndSessionProjectUserDoNotRequirePasswordOrMailboxSecret() {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        StoreSyncStoreRecord store = store();
        store.setNoonPartnerUser(null);
        store.setNoonPartnerProjectUser(null);
        store.setNoonPartnerUserCode("canonical-user-code");
        store.setNoonPartnerPwd(null);
        store.setNoonPartnerMailAuthCode(null);
        store.setNoonPartnerCookie("sid=persisted");
        when(mapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        NoonPullStoreBindingResolver resolver = new NoonPullStoreBindingResolver(mapper);

        NoonPullStoreBinding binding = resolver.resolve(request());

        assertEquals("canonical-user-code", binding.getSessionProjectUser());
        assertEquals("sid=persisted", binding.getPersistedCookie());
        assertNull(binding.getNoonPassword());
        assertNull(binding.getNoonEmailAuthCode());
    }

    @Test
    void missingSessionAndRecoveryCredentialFailsClosed() {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        StoreSyncStoreRecord store = store();
        store.setNoonPartnerProjectUser("project-session-user");
        when(mapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        NoonPullStoreBindingResolver resolver = new NoonPullStoreBindingResolver(mapper);

        NoonInterfacePullException exception = assertThrows(
                NoonInterfacePullException.class,
                () -> resolver.resolve(request())
        );

        assertEquals(
                true,
                exception.getMessage().contains(
                        "missing persisted Noon project session or recoverable login credential")
        );
    }

    private NoonInterfacePullRequest request() {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .targetIdentity("catalog:list")
                .build();
    }

    private StoreSyncStoreRecord store() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectCode("PRJ245027");
        store.setStoreCode("STR245027-NAE");
        store.setSite("AE");
        store.setNoonPartnerId("245027");
        return store;
    }
}
