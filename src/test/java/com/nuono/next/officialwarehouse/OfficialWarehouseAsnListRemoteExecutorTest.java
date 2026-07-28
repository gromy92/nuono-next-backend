package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseAsnSyncThrottleMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnListSyncThrottleRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.sales.NoonSalesReportBinding;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAsnListRemoteExecutorTest {
    private OfficialWarehouseMapper mapper;
    private OfficialWarehouseAsnSyncThrottleMapper throttleMapper;
    private OfficialWarehouseNoonInboundClient client;
    private OfficialWarehouseAsnListRemoteExecutor executor;
    private AtomicReference<String> claimToken;

    @BeforeEach
    void setUp() {
        mapper = mock(OfficialWarehouseMapper.class);
        throttleMapper = mock(OfficialWarehouseAsnSyncThrottleMapper.class);
        client = mock(OfficialWarehouseNoonInboundClient.class);
        executor = new OfficialWarehouseAsnListRemoteExecutor(
                mapper,
                client,
                new ObjectMapper(),
                new NoonPullFailurePolicy()
        );
        executor.setThrottleMapper(throttleMapper);
        claimToken = new AtomicReference<>();
        when(mapper.claimOfficialWarehouseAsnListSync(
                eq(65267L),
                eq("STR65267-NSA"),
                eq("SA"),
                any(),
                eq(901L)
        )).thenAnswer(invocation -> {
            claimToken.set(invocation.getArgument(3));
            return 1;
        });
        when(mapper.selectOfficialWarehouseAsnListSyncThrottle(
                65267L,
                "STR65267-NSA",
                "SA"
        )).thenAnswer(invocation -> throttle(claimToken.get()));
    }

    @Test
    void releasesCurrentThrottleClaimWhenNoonReturns502() {
        IllegalStateException failure = new IllegalStateException("HTTP 502 Bad Gateway");
        when(client.syncAsnList(nullable(NoonSession.class), any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> executor.execute(
                null,
                binding(),
                65267L,
                site(),
                901L,
                (result, ownerUserId, site, binding, session, row, operatorUserId) -> {
                }
        )).isSameAs(failure);

        verify(throttleMapper).release(
                65267L,
                "STR65267-NSA",
                "SA",
                claimToken.get()
        );
    }

    private static StoreSiteRecord site() {
        StoreSiteRecord site = new StoreSiteRecord();
        site.ownerUserId = 65267L;
        site.logicalStoreId = 65267L;
        site.storeCode = "STR65267-NSA";
        site.siteCode = "SA";
        site.projectCode = "PRJ65267";
        return site;
    }

    private static NoonSalesReportBinding binding() {
        return new NoonSalesReportBinding(
                65267L,
                65267L,
                "PRJ65267",
                "STR65267-NSA",
                "SA",
                "PARTNER",
                "merchant@example.com",
                null,
                "mail-auth-code",
                "cookie"
        );
    }

    private static AsnListSyncThrottleRecord throttle(String token) {
        AsnListSyncThrottleRecord throttle = new AsnListSyncThrottleRecord();
        throttle.ownerUserId = 65267L;
        throttle.storeCode = "STR65267-NSA";
        throttle.siteCode = "SA";
        throttle.claimToken = token;
        throttle.operatorUserId = 901L;
        return throttle;
    }
}
