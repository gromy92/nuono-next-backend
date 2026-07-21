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
import java.math.BigDecimal;
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
class PostSaleProfitCommandControllerTest {

    @Mock
    private PostSaleProfitRecalculationService recalculationService;

    @Mock
    private PostSaleProfitAttributionAdjustmentService attributionAdjustmentService;

    @Mock
    private PostSaleProfitFxRateService fxRateService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private PostSaleProfitCommandController controller;

    @BeforeEach
    void setUp() {
        controller = new PostSaleProfitCommandController(
                recalculationService,
                attributionAdjustmentService,
                fxRateService,
                new PostSaleProfitHttpSupport(businessAccessResolver)
        );
    }

    @Test
    void recalculatePreviewKeepsAuthorizedOwnerAndDatedScope() {
        MockHttpServletRequest request = authorizedRequest();
        when(recalculationService.recalculatePreview(any()))
                .thenReturn(new PostSaleProfitRecalculationView(10L, "PREVIEW", 8, 7, 2));
        PostSaleProfitRecalculationRequest body = recalculationRequest();

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
    void attributionCommandsKeepExistingPayloadMapping() {
        MockHttpServletRequest request = authorizedRequest();
        when(attributionAdjustmentService.setBatchLock(any()))
                .thenReturn(new PostSaleProfitAttributionAdjustmentView(700L, List.of("MANUAL_LOCKED")));
        when(attributionAdjustmentService.moveAttribution(any()))
                .thenReturn(new PostSaleProfitAttributionMoveView(700L, 701L, BigDecimal.ONE));
        PostSaleProfitBatchLockRequest lockBody = new PostSaleProfitBatchLockRequest();
        lockBody.setStoreCode("STR108065-NSA");
        lockBody.setSiteCode("SA");
        lockBody.setBatchId(700L);
        lockBody.setLocked(true);
        lockBody.setReason(" 人工确认 ");
        PostSaleProfitAttributionMoveRequest moveBody = new PostSaleProfitAttributionMoveRequest();
        moveBody.setStoreCode("STR108065-NSA");
        moveBody.setSiteCode("SA");
        moveBody.setSourceBatchId(700L);
        moveBody.setTargetBatchId(701L);
        moveBody.setQuantity(BigDecimal.ONE);
        moveBody.setReason(" 订单实际属于 6 月采购批次 ");

        controller.setBatchAttributionLock(lockBody, request);
        controller.moveAttribution(moveBody, request);

        ArgumentCaptor<PostSaleProfitBatchLockCommand> lockCaptor =
                ArgumentCaptor.forClass(PostSaleProfitBatchLockCommand.class);
        ArgumentCaptor<PostSaleProfitAttributionMoveCommand> moveCaptor =
                ArgumentCaptor.forClass(PostSaleProfitAttributionMoveCommand.class);
        verify(attributionAdjustmentService).setBatchLock(lockCaptor.capture());
        verify(attributionAdjustmentService).moveAttribution(moveCaptor.capture());
        assertThat(lockCaptor.getValue().getOwnerUserId()).isEqualTo(307L);
        assertThat(lockCaptor.getValue().getStoreCode()).isEqualTo("STR108065-NSA");
        assertThat(lockCaptor.getValue().getSiteCode()).isEqualTo("SA");
        assertThat(lockCaptor.getValue().getBatchId()).isEqualTo(700L);
        assertThat(lockCaptor.getValue().isLocked()).isTrue();
        assertThat(lockCaptor.getValue().getReason()).isEqualTo("人工确认");
        assertThat(moveCaptor.getValue().getOwnerUserId()).isEqualTo(307L);
        assertThat(moveCaptor.getValue().getStoreCode()).isEqualTo("STR108065-NSA");
        assertThat(moveCaptor.getValue().getSiteCode()).isEqualTo("SA");
        assertThat(moveCaptor.getValue().getSourceBatchId()).isEqualTo(700L);
        assertThat(moveCaptor.getValue().getTargetBatchId()).isEqualTo(701L);
        assertThat(moveCaptor.getValue().getQuantity()).isEqualByComparingTo("1");
        assertThat(moveCaptor.getValue().getReason()).isEqualTo("订单实际属于 6 月采购批次");
    }

    @Test
    void saveFxRateKeepsPostAuthorizationNormalization() {
        MockHttpServletRequest request = authorizedRequest();
        when(fxRateService.saveRate(any())).thenReturn(new PostSaleProfitFxRateView(
                10L, "SA", "SAR", new BigDecimal("1.8833"),
                "2026-05-01", "2026-05-31", "运营手工"
        ));
        PostSaleProfitFxRateRequest body = fxRateRequest();

        controller.saveFxRate(body, request);

        ArgumentCaptor<PostSaleProfitFxRateCommand> captor =
                ArgumentCaptor.forClass(PostSaleProfitFxRateCommand.class);
        verify(fxRateService).saveRate(captor.capture());
        PostSaleProfitFxRateCommand command = captor.getValue();
        assertThat(command.getOwnerUserId()).isEqualTo(307L);
        assertThat(command.getSiteCode()).isEqualTo("SA");
        assertThat(command.getCurrency()).isEqualTo("SAR");
        assertThat(command.getRateToCny()).isEqualByComparingTo("1.8833");
        assertThat(command.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(command.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(command.getSourceLabel()).isEqualTo("运营手工");
    }

    @Test
    void missingRequiredFxDateKeepsItsPostAuthorizationReason() {
        MockHttpServletRequest request = authorizedRequest();
        PostSaleProfitFxRateRequest body = fxRateRequest();
        body.setEffectiveFrom(" ");

        assertBadRequest(
                "effectiveFrom is required.",
                () -> controller.saveFxRate(body, request)
        );

        verify(fxRateService, never()).saveRate(any());
    }

    @Test
    void commandBaseValidationRemainsBeforeAccessResolution() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PostSaleProfitBatchLockRequest lockBody = new PostSaleProfitBatchLockRequest();
        PostSaleProfitAttributionMoveRequest moveBody = new PostSaleProfitAttributionMoveRequest();
        PostSaleProfitFxRateRequest fxBody = new PostSaleProfitFxRateRequest();
        PostSaleProfitRecalculationRequest invalidDateBody = recalculationRequest();
        invalidDateBody.setDateFrom("not-a-date");

        assertBadRequest(
                "Request body is required.",
                () -> controller.recalculatePreview(null, request)
        );
        assertBadRequest(
                "storeCode, siteCode, batchId and locked are required.",
                () -> controller.setBatchAttributionLock(lockBody, request)
        );
        assertBadRequest(
                "storeCode, siteCode, sourceBatchId, targetBatchId and quantity are required.",
                () -> controller.moveAttribution(moveBody, request)
        );
        assertBadRequest(
                "storeCode, siteCode, currency, rateToCny and effectiveFrom are required.",
                () -> controller.saveFxRate(fxBody, request)
        );
        fxBody.setRateToCny(BigDecimal.ONE);
        assertBadRequest(
                "storeCode and siteCode are required.",
                () -> controller.saveFxRate(fxBody, request)
        );
        assertBadRequest(
                "dateFrom and dateTo must be ISO dates.",
                () -> controller.recalculatePreview(invalidDateBody, request)
        );

        verifyNoInteractions(businessAccessResolver, recalculationService, attributionAdjustmentService, fxRateService);
    }

    @Test
    void invalidFxDatesStillWaitForAccessResolution() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PostSaleProfitFxRateRequest body = fxRateRequest();
        body.setEffectiveFrom("not-a-date");
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                "STR108065-NSA"
        )).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_REQUIRED"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.saveFxRate(body, request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        verify(fxRateService, never()).saveRate(any());
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

    private PostSaleProfitRecalculationRequest recalculationRequest() {
        PostSaleProfitRecalculationRequest body = new PostSaleProfitRecalculationRequest();
        body.setStoreCode("STR108065-NSA");
        body.setSiteCode("SA");
        body.setDateFrom("2026-05-01");
        body.setDateTo("2026-05-31");
        return body;
    }

    private PostSaleProfitFxRateRequest fxRateRequest() {
        PostSaleProfitFxRateRequest body = new PostSaleProfitFxRateRequest();
        body.setStoreCode("STR108065-NSA");
        body.setSiteCode("SA");
        body.setCurrency(" SAR ");
        body.setRateToCny(new BigDecimal("1.8833"));
        body.setEffectiveFrom("2026-05-01");
        body.setEffectiveTo("2026-05-31");
        body.setSourceLabel(" ");
        return body;
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
