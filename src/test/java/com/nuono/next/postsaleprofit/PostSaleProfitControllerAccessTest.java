package com.nuono.next.postsaleprofit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
class PostSaleProfitControllerAccessTest {

    @Mock
    private PostSaleProfitReadService readService;

    @Mock
    private PostSaleProfitRecalculationService recalculationService;

    @Mock
    private PostSaleProfitAttributionAdjustmentService attributionAdjustmentService;

    @Mock
    private PostSaleProfitFxRateService fxRateService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private PostSaleProfitController controller;

    @BeforeEach
    void setUp() {
        controller = new PostSaleProfitController(readService, recalculationService, attributionAdjustmentService, fxRateService, businessAccessResolver);
    }

    @Test
    void listBatchesUsesSalesDataCapabilityAndAuthorizedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(accessContext());
        when(readService.listBatches(any())).thenReturn(new PostSaleProfitBatchListView(List.of(), 0, false));

        controller.listBatches(
                "STR108065-NSA",
                "SA",
                "2026-05-01",
                "2026-05-31",
                "PSKU",
                "MISSING_HEADHAUL",
                true,
                false,
                true,
                2,
                50,
                request
        );

        ArgumentCaptor<PostSaleProfitBatchQuery> captor = ArgumentCaptor.forClass(PostSaleProfitBatchQuery.class);
        verify(readService).listBatches(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(307L);
        assertThat(captor.getValue().getStoreCode()).isEqualTo("STR108065-NSA");
        assertThat(captor.getValue().getSiteCode()).isEqualTo("SA");
        assertThat(captor.getValue().getDateFrom()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(captor.getValue().getDateTo()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(captor.getValue().getKeyword()).isEqualTo("PSKU");
        assertThat(captor.getValue().getQuality()).isEqualTo("MISSING_HEADHAUL");
        assertThat(captor.getValue().isOnlyLoss()).isTrue();
        assertThat(captor.getValue().isOnlyLowMargin()).isFalse();
        assertThat(captor.getValue().isOnlyMissing()).isTrue();
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void latestRunUsesSalesDataCapabilityAndAuthorizedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(accessContext());
        when(readService.latestRun(307L, "STR108065-NSA", "SA"))
                .thenReturn(PostSaleProfitLatestRunView.unavailable());

        controller.latestRun("STR108065-NSA", "SA", request);

        verify(readService).latestRun(307L, "STR108065-NSA", "SA");
    }

    @Test
    void recalculatePreviewUsesSalesDataCapabilityAndAuthorizedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(accessContext());
        when(recalculationService.recalculatePreview(any()))
                .thenReturn(new PostSaleProfitRecalculationView(10L, "PREVIEW", 8, 7, 2));
        PostSaleProfitRecalculationRequest body = new PostSaleProfitRecalculationRequest();
        body.setStoreCode("STR108065-NSA");
        body.setSiteCode("SA");
        body.setDateFrom("2026-05-01");
        body.setDateTo("2026-05-31");

        controller.recalculatePreview(body, request);

        ArgumentCaptor<PostSaleProfitRecalculationCommand> captor =
                ArgumentCaptor.forClass(PostSaleProfitRecalculationCommand.class);
        verify(recalculationService).recalculatePreview(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(307L);
        assertThat(captor.getValue().getStoreCode()).isEqualTo("STR108065-NSA");
        assertThat(captor.getValue().getSiteCode()).isEqualTo("SA");
        assertThat(captor.getValue().getDateFrom()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(captor.getValue().getDateTo()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void recalculatePreviewDoesNotCallServiceWhenStoreAccessIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));
        PostSaleProfitRecalculationRequest body = new PostSaleProfitRecalculationRequest();
        body.setStoreCode("STR108065-NSA");
        body.setSiteCode("SA");
        body.setDateFrom("2026-05-01");
        body.setDateTo("2026-05-31");

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> controller.recalculatePreview(body, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(recalculationService, never()).recalculatePreview(any());
    }

    @Test
    void batchDetailUsesSalesDataCapabilityAndAuthorizedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(accessContext());
        when(readService.getBatchDetail(307L, "STR108065-NSA", "SA", 700L))
                .thenReturn(new PostSaleProfitBatchDetailView(700L, List.of()));

        controller.batchDetail("STR108065-NSA", "SA", 700L, request);

        verify(readService).getBatchDetail(307L, "STR108065-NSA", "SA", 700L);
    }

    @Test
    void setBatchAttributionLockUsesSalesDataCapabilityAndAuthorizedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(accessContext());
        when(attributionAdjustmentService.setBatchLock(any()))
                .thenReturn(new PostSaleProfitAttributionAdjustmentView(700L, List.of("MANUAL_LOCKED")));
        PostSaleProfitBatchLockRequest body = new PostSaleProfitBatchLockRequest();
        body.setStoreCode("STR108065-NSA");
        body.setSiteCode("SA");
        body.setBatchId(700L);
        body.setLocked(true);
        body.setReason("人工确认");

        controller.setBatchAttributionLock(body, request);

        ArgumentCaptor<PostSaleProfitBatchLockCommand> captor = ArgumentCaptor.forClass(PostSaleProfitBatchLockCommand.class);
        verify(attributionAdjustmentService).setBatchLock(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(307L);
        assertThat(captor.getValue().getStoreCode()).isEqualTo("STR108065-NSA");
        assertThat(captor.getValue().getSiteCode()).isEqualTo("SA");
        assertThat(captor.getValue().getBatchId()).isEqualTo(700L);
        assertThat(captor.getValue().isLocked()).isTrue();
        assertThat(captor.getValue().getReason()).isEqualTo("人工确认");
    }

    @Test
    void moveAttributionUsesSalesDataCapabilityAndAuthorizedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(accessContext());
        when(attributionAdjustmentService.moveAttribution(any()))
                .thenReturn(new PostSaleProfitAttributionMoveView(700L, 701L, new java.math.BigDecimal("1")));
        PostSaleProfitAttributionMoveRequest body = new PostSaleProfitAttributionMoveRequest();
        body.setStoreCode("STR108065-NSA");
        body.setSiteCode("SA");
        body.setSourceBatchId(700L);
        body.setTargetBatchId(701L);
        body.setQuantity(new java.math.BigDecimal("1"));
        body.setReason("订单实际属于 6 月采购批次");

        controller.moveAttribution(body, request);

        ArgumentCaptor<PostSaleProfitAttributionMoveCommand> captor =
                ArgumentCaptor.forClass(PostSaleProfitAttributionMoveCommand.class);
        verify(attributionAdjustmentService).moveAttribution(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(307L);
        assertThat(captor.getValue().getStoreCode()).isEqualTo("STR108065-NSA");
        assertThat(captor.getValue().getSiteCode()).isEqualTo("SA");
        assertThat(captor.getValue().getSourceBatchId()).isEqualTo(700L);
        assertThat(captor.getValue().getTargetBatchId()).isEqualTo(701L);
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("1");
        assertThat(captor.getValue().getReason()).isEqualTo("订单实际属于 6 月采购批次");
    }

    @Test
    void listFxRatesUsesSalesDataCapabilityAndAuthorizedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(accessContext());
        when(fxRateService.listRates(307L, "SA"))
                .thenReturn(List.of());

        controller.listFxRates("STR108065-NSA", "SA", request);

        verify(fxRateService).listRates(307L, "SA");
    }

    @Test
    void saveFxRateUsesSalesDataCapabilityAndAuthorizedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(accessContext());
        PostSaleProfitFxRateRequest body = new PostSaleProfitFxRateRequest();
        body.setStoreCode("STR108065-NSA");
        body.setSiteCode("SA");
        body.setCurrency("SAR");
        body.setRateToCny(new java.math.BigDecimal("1.8833"));
        body.setEffectiveFrom("2026-05-01");
        body.setEffectiveTo("2026-05-31");
        body.setSourceLabel("运营手工");
        when(fxRateService.saveRate(any()))
                .thenReturn(new PostSaleProfitFxRateView(10L, "SA", "SAR", new java.math.BigDecimal("1.8833"), "2026-05-01", "2026-05-31", "运营手工"));

        controller.saveFxRate(body, request);

        ArgumentCaptor<PostSaleProfitFxRateCommand> captor = ArgumentCaptor.forClass(PostSaleProfitFxRateCommand.class);
        verify(fxRateService).saveRate(captor.capture());
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(307L);
        assertThat(captor.getValue().getSiteCode()).isEqualTo("SA");
        assertThat(captor.getValue().getCurrency()).isEqualTo("SAR");
        assertThat(captor.getValue().getRateToCny()).isEqualByComparingTo("1.8833");
        assertThat(captor.getValue().getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(captor.getValue().getEffectiveTo()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(captor.getValue().getSourceLabel()).isEqualTo("运营手工");
    }

    private static BusinessAccessContext accessContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(501L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(2)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/api/post-sale-profit"))
                .build();
    }
}
