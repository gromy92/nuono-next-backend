package com.nuono.next.postsaleprofit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PostSaleProfitQueryControllerTest {

    @Mock
    private PostSaleProfitReadService readService;

    @Mock
    private PostSaleProfitFxRateService fxRateService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private PostSaleProfitQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new PostSaleProfitQueryController(
                readService,
                fxRateService,
                new PostSaleProfitHttpSupport(businessAccessResolver)
        );
    }

    @Test
    void listBatchesUsesAuthorizedOwnerAndPreservesQueryNormalization() {
        MockHttpServletRequest request = authorizedRequest();
        when(readService.listBatches(any())).thenReturn(new PostSaleProfitBatchListView(List.of(), 0, false));

        controller.listBatches(
                " STR108065-NSA ", " SA ", "2026-05-01", "2026-05-31",
                " PSKU ", " MISSING_HEADHAUL ", true, false, true, 0, 500, request
        );

        ArgumentCaptor<PostSaleProfitBatchQuery> captor = ArgumentCaptor.forClass(PostSaleProfitBatchQuery.class);
        verify(readService).listBatches(captor.capture());
        PostSaleProfitBatchQuery query = captor.getValue();
        assertThat(query.getOwnerUserId()).isEqualTo(307L);
        assertThat(query.getStoreCode()).isEqualTo("STR108065-NSA");
        assertThat(query.getSiteCode()).isEqualTo("SA");
        assertThat(query.getDateFrom()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(query.getDateTo()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(query.getKeyword()).isEqualTo("PSKU");
        assertThat(query.getQuality()).isEqualTo("MISSING_HEADHAUL");
        assertThat(query.isOnlyLoss()).isTrue();
        assertThat(query.isOnlyLowMargin()).isFalse();
        assertThat(query.isOnlyMissing()).isTrue();
        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(200);
    }

    @Test
    void remainingGetEndpointsKeepTheirServiceCalls() {
        MockHttpServletRequest request = authorizedRequest();
        when(readService.latestRun(307L, "STR108065-NSA", "SA"))
                .thenReturn(PostSaleProfitLatestRunView.unavailable());
        when(readService.getBatchDetail(307L, "STR108065-NSA", "SA", 700L))
                .thenReturn(new PostSaleProfitBatchDetailView(700L, List.of()));
        when(fxRateService.listRates(307L, "SA")).thenReturn(List.of());

        controller.latestRun("STR108065-NSA", "SA", request);
        controller.batchDetail("STR108065-NSA", "SA", 700L, request);
        controller.listFxRates("STR108065-NSA", "SA", request);

        verify(readService).latestRun(307L, "STR108065-NSA", "SA");
        verify(readService).getBatchDetail(307L, "STR108065-NSA", "SA", 700L);
        verify(fxRateService).listRates(307L, "SA");
    }

    @Test
    void latestRunFallsBackToBusinessOwnerWhenStoreOwnerIsNotMapped() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext fallbackContext = BusinessAccessContext.builder()
                .sessionUserId(501L)
                .businessOwnerUserId(999L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of())
                .build();
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                "STR108065-NSA"
        )).thenReturn(fallbackContext);
        when(readService.latestRun(999L, "STR108065-NSA", "SA"))
                .thenReturn(PostSaleProfitLatestRunView.unavailable());

        controller.latestRun("STR108065-NSA", "SA", request);

        verify(readService).latestRun(999L, "STR108065-NSA", "SA");
    }

    @Test
    void queryValidationRemainsBeforeAccessResolution() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertBadRequest(
                "storeCode and siteCode are required.",
                () -> controller.latestRun(" ", "SA", request)
        );
        assertBadRequest(
                "storeCode, siteCode, dateFrom and dateTo are required.",
                () -> controller.listBatches(
                        "STR108065-NSA", "SA", " ", "2026-05-31",
                        null, null, null, null, null, 1, 50, request
                )
        );
        assertBadRequest(
                "dateFrom and dateTo must be ISO dates.",
                () -> controller.listBatches(
                        "STR108065-NSA", "SA", "not-a-date", "2026-05-31",
                        null, null, null, null, null, 1, 50, request
                )
        );
        assertBadRequest(
                "dateTo must be on or after dateFrom.",
                () -> controller.listBatches(
                        "STR108065-NSA", "SA", "2026-06-01", "2026-05-31",
                        null, null, null, null, null, 1, 50, request
                )
        );
        assertBadRequest(
                "batchId is required.",
                () -> controller.batchDetail(" ", " ", 0L, request)
        );

        verifyNoInteractions(businessAccessResolver, readService, fxRateService);
    }

    @Test
    void rejectedAccessStopsBeforeReadServices() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                "STR108065-NSA"
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.latestRun("STR108065-NSA", "SA", request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(readService, never()).latestRun(any(), any(), any());
        verifyNoInteractions(fxRateService);
    }

    private MockHttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                "STR108065-NSA"
        )).thenReturn(accessContext());
        return request;
    }

    private void assertBadRequest(String reason, org.junit.jupiter.api.function.Executable executable) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, executable);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals(reason, error.getReason());
    }

    private static BusinessAccessContext accessContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(501L)
                .businessOwnerUserId(999L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(2)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/api/post-sale-profit"))
                .build();
    }
}
