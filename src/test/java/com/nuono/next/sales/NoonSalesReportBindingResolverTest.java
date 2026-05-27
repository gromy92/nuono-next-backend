package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoonSalesReportBindingResolverTest {

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Test
    void resolvesNoonProjectAndSiteBindingFromStoreScope() {
        NoonSalesReportBindingResolver resolver = new NoonSalesReportBindingResolver(storeSyncMapper);
        StoreSyncStoreRecord store = store(
                7001L,
                "PRJ245027",
                "STR245027-NSA",
                "SA",
                "245027"
        );
        store.setNoonPartnerProjectUser("project-user@example.com");
        store.setNoonPartnerUser("fallback-user@example.com");
        store.setNoonPartnerPwd("secret");
        store.setNoonPartnerCookie("session=already-present");
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NSA")).thenReturn(store);

        NoonSalesReportBinding binding = resolver.resolve(new NoonSalesReportRequest(
                10002L,
                245027L,
                "STR245027-NSA",
                "SA",
                LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 5, 19)
        ));

        assertEquals(10002L, binding.getOwnerUserId());
        assertEquals("PRJ245027", binding.getProjectCode());
        assertEquals("STR245027-NSA", binding.getStoreCode());
        assertEquals("SA", binding.getSiteCode());
        assertEquals("245027", binding.getPartnerId());
        assertEquals("project-user@example.com", binding.getNoonUser());
        assertEquals("secret", binding.getNoonPassword());
        assertEquals("session=already-present", binding.getPersistedCookie());
    }

    private static StoreSyncStoreRecord store(
            Long id,
            String projectCode,
            String storeCode,
            String site,
            String noonPartnerId
    ) {
        StoreSyncStoreRecord record = new StoreSyncStoreRecord();
        record.setId(id);
        record.setProjectCode(projectCode);
        record.setStoreCode(storeCode);
        record.setSite(site);
        record.setNoonPartnerId(noonPartnerId);
        return record;
    }
}
