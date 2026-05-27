package com.nuono.next.systemreports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.SystemReportMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreDataReportServiceTest {

    @Mock
    private SystemReportMapper mapper;

    private StoreDataReportService service;

    @BeforeEach
    void setUp() {
        service = new StoreDataReportService(mapper);
    }

    @Test
    void overviewReadsAllSystemStoresInsteadOfSessionStoreScope() {
        when(mapper.selectStoreDataReportRows(null)).thenReturn(List.of(row("STR-AE", "AE"), row("STR-SA", "SA")));

        StoreDataReportOverview overview = service.overview(operatorWithOneStore(), null);

        assertEquals(2, overview.getRows().size());
        verify(mapper).selectStoreDataReportRows(isNull());
    }

    @Test
    void overviewCanFilterSpecificStoreWithoutCheckingSessionStoreScope() {
        when(mapper.selectStoreDataReportRows(List.of("STR-OUTSIDE-SESSION"))).thenReturn(List.of(row("STR-OUTSIDE-SESSION", "AE")));

        StoreDataReportOverview overview = service.overview(operatorWithOneStore(), " str-outside-session ");

        assertEquals("STR-OUTSIDE-SESSION", overview.getRows().get(0).getStoreCode());
        verify(mapper).selectStoreDataReportRows(List.of("STR-OUTSIDE-SESSION"));
    }

    private BusinessAccessContext operatorWithOneStore() {
        return BusinessAccessContext.builder()
                .sessionUserId(555L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR-AUTHORIZED-ONLY"))
                .storeOwnerUserIds(Map.of("STR-AUTHORIZED-ONLY", 10002L))
                .menuPaths(Set.of("/system-reports/store-data"))
                .build();
    }

    private StoreDataReportRow row(String storeCode, String siteCode) {
        StoreDataReportRow row = new StoreDataReportRow();
        row.setProjectCode("PRJ");
        row.setProjectName("测试店铺");
        row.setStoreCode(storeCode);
        row.setSiteCode(siteCode);
        row.setSiteOfferCount(1);
        row.setOffersWithSalesFacts(1);
        row.setLifecycleCurrentCount(1);
        return row;
    }
}
