package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import org.junit.jupiter.api.Test;

class ProductNoonCredentialResolverTest {

    private final ProductNoonCredentialResolver resolver = new ProductNoonCredentialResolver();

    @Test
    void resolvePrefersExactStoreProjectCredentialOverOwnerAggregateCredential() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        StoreSyncStoreRecord store = storeCredential(
                "STR353172-NSA",
                "PRJ353172",
                "353172",
                "store-main-user",
                "store-project-user",
                "store-password",
                "store-cookie"
        );
        StoreSyncOwnerContext owner = ownerCredential(
                "308",
                "other-main-user",
                "other-project-user",
                "other-password",
                "other-cookie",
                "other-partner"
        );

        ProductNoonCredential credential = resolver.resolve(command, store, owner);

        assertEquals("store-project-user", credential.getNoonUser());
        assertEquals("store-password", credential.getNoonPassword());
        assertEquals("store-cookie", credential.getNoonCookie());
        assertEquals("PRJ353172", credential.getProjectCode());
    }

    @Test
    void resolveKeepsManualLoginOverrideAheadOfStoreCredential() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setNoonUser("manual-user");
        command.setNoonPassword("manual-password");
        StoreSyncStoreRecord store = storeCredential(
                "STR353172-NSA",
                "PRJ353172",
                "353172",
                "store-main-user",
                "store-project-user",
                "store-password",
                "store-cookie"
        );
        StoreSyncOwnerContext owner = ownerCredential(
                "308",
                "owner-main-user",
                "owner-project-user",
                "owner-password",
                "owner-cookie",
                "owner-partner"
        );

        ProductNoonCredential credential = resolver.resolve(command, store, owner);

        assertEquals("manual-user", credential.getNoonUser());
        assertEquals("manual-password", credential.getNoonPassword());
        assertEquals("store-cookie", credential.getNoonCookie());
        assertEquals("PRJ353172", credential.getProjectCode());
    }

    @Test
    void resolveFallsBackToOwnerAggregateWhenStoreCredentialIsMissing() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        StoreSyncStoreRecord store = storeCredential(
                "STR353172-NSA",
                null,
                null,
                null,
                null,
                null,
                null
        );
        StoreSyncOwnerContext owner = ownerCredential(
                "308",
                "owner-main-user",
                "owner-project-user",
                "owner-password",
                "owner-cookie",
                "owner-partner"
        );

        ProductNoonCredential credential = resolver.resolve(command, store, owner);

        assertEquals("owner-project-user", credential.getNoonUser());
        assertEquals("owner-password", credential.getNoonPassword());
        assertEquals("owner-cookie", credential.getNoonCookie());
        assertEquals("owner-partner", credential.getProjectCode());
    }

    private StoreSyncStoreRecord storeCredential(
            String storeCode,
            String projectCode,
            String partnerId,
            String noonPartnerUser,
            String noonPartnerProjectUser,
            String noonPartnerPwd,
            String noonPartnerCookie
    ) {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode(storeCode);
        store.setProjectCode(projectCode);
        store.setNoonPartnerId(partnerId);
        store.setNoonPartnerUser(noonPartnerUser);
        store.setNoonPartnerProjectUser(noonPartnerProjectUser);
        store.setNoonPartnerPwd(noonPartnerPwd);
        store.setNoonPartnerCookie(noonPartnerCookie);
        return store;
    }

    private StoreSyncOwnerContext ownerCredential(
            String ownerId,
            String noonPartnerUser,
            String noonPartnerProjectUser,
            String noonPartnerPwd,
            String noonPartnerCookie,
            String partnerId
    ) {
        StoreSyncOwnerContext owner = new StoreSyncOwnerContext();
        owner.setId(Long.valueOf(ownerId));
        owner.setNoonPartnerUser(noonPartnerUser);
        owner.setNoonPartnerProjectUser(noonPartnerProjectUser);
        owner.setNoonPartnerPwd(noonPartnerPwd);
        owner.setNoonPartnerCookie(noonPartnerCookie);
        owner.setNoonPartnerId(partnerId);
        return owner;
    }
}
