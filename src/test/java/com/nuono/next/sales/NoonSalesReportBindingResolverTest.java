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
    void shouldAllowConfiguredEmailCredentialWhenStoreRowHasNoLegacySecret() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectCode("PRJ245027");
        store.setStoreCode("STR245027-NAE");
        store.setSite("AE");
        store.setNoonPartnerId("245027");
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);

        NoonSalesReportBinding binding = new NoonSalesReportBindingResolver(
                storeSyncMapper,
                "unified@example.com",
                "mail-auth-code"
        ).resolve(request());

        assertEquals("PRJ245027", binding.getProjectCode());
        assertEquals("STR245027-NAE", binding.getStoreCode());
        assertEquals("AE", binding.getSiteCode());
        assertEquals("245027", binding.getPartnerId());
        assertEquals("unified@example.com", binding.getNoonUser());
        assertEquals(null, binding.getNoonEmailAuthCode());
        assertEquals(null, binding.getNoonPassword());
    }

    private NoonSalesReportRequest request() {
        return new NoonSalesReportRequest(
                10002L,
                9001L,
                "STR245027-NAE",
                "AE",
                LocalDate.of(2026, 5, 21),
                LocalDate.of(2026, 5, 21)
        );
    }
}
