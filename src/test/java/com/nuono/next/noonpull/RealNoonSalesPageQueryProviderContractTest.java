package com.nuono.next.noonpull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RealNoonSalesPageQueryProviderContractTest {
    private static final String LIST_URL = "https://reports.noon.test/sales-dashboard/list";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void responseCarriesTheRequestedPageSizeAndAuthoritativeTotal() {
        ObjectNode response = objectMapper.createObjectNode();
        response.withArray("hits").add(objectMapper.createObjectNode()
                .put("item_nr", "NAEI50000000001-1"));
        response.put("total", 21);

        NoonInterfacePullPage page = provider(response, 10).fetchPage(request(), 1);

        assertThat(page.getPageSize()).isEqualTo(10);
        assertThat(page.getTotalItems()).isEqualTo(21);
        assertThat(page.isHasNextPage()).isTrue();
    }

    @Test
    void missingAuthoritativeTotalIsAContractFailure() {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("hits", objectMapper.createArrayNode());

        assertThatThrownBy(() -> provider(response, 10).fetchPage(request(), 1))
                .isInstanceOf(NoonInterfacePullException.class)
                .hasMessageContaining("authoritative total is missing");
    }

    private RealNoonSalesPageQueryProvider provider(JsonNode response, int pageSize) {
        StoreSyncMapper mapper = mock(StoreSyncMapper.class);
        when(mapper.selectOwnerStore(307L, "STR244978-NAE")).thenReturn(binding());
        NoonPullGatewaySession session = mock(NoonPullGatewaySession.class);
        when(session.postJson(anyString(), any(JsonNode.class), anyBoolean(), anyMap()))
                .thenReturn(response);
        NoonPullGatewaySessionFactory sessions = mock(NoonPullGatewaySessionFactory.class);
        when(sessions.login(any(NoonPullStoreBinding.class))).thenReturn(session);
        return new RealNoonSalesPageQueryProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(mapper),
                sessions,
                LIST_URL,
                pageSize
        );
    }

    private NoonInterfacePullRequest request() {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR244978-NAE")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.ORDER)
                .requestName("dp02-exact-order-page")
                .targetIdentity("DP02:date-range:2026-07-10..2026-07-10")
                .dateFrom(LocalDate.of(2026, 7, 10))
                .dateTo(LocalDate.of(2026, 7, 10))
                .build();
    }

    private StoreSyncStoreRecord binding() {
        StoreSyncStoreRecord record = new StoreSyncStoreRecord();
        record.setProjectCode("PRJ244978");
        record.setStoreCode("STR244978-NAE");
        record.setSite("AE");
        record.setNoonPartnerId("244978");
        record.setNoonPartnerProjectUser("project-user@example.com");
        record.setNoonPartnerCookie("session=redacted");
        return record;
    }
}
