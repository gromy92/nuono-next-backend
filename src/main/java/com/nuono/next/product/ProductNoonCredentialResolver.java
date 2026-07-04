package com.nuono.next.product;

import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import org.springframework.util.StringUtils;

class ProductNoonCredentialResolver {

    ProductNoonCredential resolve(
            ProductMasterFetchCommand command,
            StoreSyncStoreRecord store,
            StoreSyncOwnerContext owner
    ) {
        return new ProductNoonCredential(
                firstNonBlank(
                        command == null ? null : command.getNoonUser(),
                        store == null ? null : store.getNoonPartnerProjectUser(),
                        store == null ? null : store.getNoonPartnerUser(),
                        owner == null ? null : owner.getNoonPartnerProjectUser(),
                        owner == null ? null : owner.getNoonPartnerUser()
                ),
                firstNonBlank(
                        command == null ? null : command.getNoonPassword(),
                        store == null ? null : store.getNoonPartnerPwd(),
                        owner == null ? null : owner.getNoonPartnerPwd()
                ),
                firstNonBlank(
                        store == null ? null : store.getNoonPartnerMailAuthCode(),
                        owner == null ? null : owner.getNoonPartnerMailAuthCode()
                ),
                firstNonBlank(
                        store == null ? null : store.getNoonPartnerCookie(),
                        owner == null ? null : owner.getNoonPartnerCookie()
                ),
                firstNonBlank(
                        store == null ? null : store.getProjectCode(),
                        store == null ? null : store.getNoonPartnerId(),
                        owner == null ? null : owner.getNoonPartnerId()
                )
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
