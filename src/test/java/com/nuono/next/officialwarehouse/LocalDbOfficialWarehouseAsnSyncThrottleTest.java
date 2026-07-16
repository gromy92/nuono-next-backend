package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnListSyncThrottleRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import com.nuono.next.web.ApiProblemException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LocalDbOfficialWarehouseAsnSyncThrottleTest {

    @Test
    void acceptsFirstStoreSiteClaimAndPersistsSessionOperatorScope() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        LocalDbOfficialWarehouseService service = service(mapper);
        StoreSiteRecord site = storeSite();
        AtomicReference<String> claimedToken = new AtomicReference<>();
        when(mapper.claimOfficialWarehouseAsnListSync(
                eq(307L), eq("STR245027-NAE"), eq("AE"), anyString(), eq(901L)
        )).thenAnswer(invocation -> {
            claimedToken.set(invocation.getArgument(3));
            return 1;
        });
        when(mapper.selectOfficialWarehouseAsnListSyncThrottle(307L, "STR245027-NAE", "AE"))
                .thenAnswer(invocation -> throttle(claimedToken.get(), LocalDateTime.now()));

        assertThatCode(() -> service.claimOfficialWarehouseAsnListSync(307L, site, 901L))
                .doesNotThrowAnyException();

        verify(mapper).claimOfficialWarehouseAsnListSync(
                eq(307L), eq("STR245027-NAE"), eq("AE"), eq(claimedToken.get()), eq(901L)
        );
    }

    @Test
    void rejectsRepeatedStoreSiteClaimWithinRollingHourWithRetryMetadata() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        LocalDbOfficialWarehouseService service = service(mapper);
        StoreSiteRecord site = storeSite();
        when(mapper.selectOfficialWarehouseAsnListSyncThrottle(307L, "STR245027-NAE", "AE"))
                .thenReturn(throttle("previous-claim", LocalDateTime.now().minusMinutes(15)));

        assertThatThrownBy(() -> service.claimOfficialWarehouseAsnListSync(307L, site, 901L))
                .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                    assertThat(problem.getStatus().value()).isEqualTo(429);
                    assertThat(problem.getCode()).isEqualTo("OFFICIAL_WAREHOUSE_ASN_SYNC_RATE_LIMITED");
                    assertThat(problem.getMessage()).contains("每小时最多同步一次");
                    assertThat(problem.getDetails()).containsEntry("cooldownMinutes", 60);
                    assertThat(problem.getDetails()).containsKeys("retryAfterSeconds", "nextAllowedAt");
                });
    }

    private static LocalDbOfficialWarehouseService service(OfficialWarehouseMapper mapper) {
        return new LocalDbOfficialWarehouseService(
                mapper,
                mock(NoonSessionGateway.class),
                mock(NoonSalesReportBindingResolver.class),
                mock(NoonHttpCallLogService.class),
                mock(OfficialWarehouseNoonInboundClient.class),
                new ObjectMapper(),
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy()
        );
    }

    private static StoreSiteRecord storeSite() {
        StoreSiteRecord site = new StoreSiteRecord();
        site.ownerUserId = 307L;
        site.storeCode = "STR245027-NAE";
        site.siteCode = "AE";
        return site;
    }

    private static AsnListSyncThrottleRecord throttle(String claimToken, LocalDateTime lastStartedAt) {
        AsnListSyncThrottleRecord throttle = new AsnListSyncThrottleRecord();
        throttle.ownerUserId = 307L;
        throttle.storeCode = "STR245027-NAE";
        throttle.siteCode = "AE";
        throttle.claimToken = claimToken;
        throttle.lastStartedAt = lastStartedAt;
        throttle.operatorUserId = 901L;
        return throttle;
    }
}
