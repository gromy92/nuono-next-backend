package com.nuono.next.preorderprofit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PreOrderProfitControllerTest {

    @Mock
    private ObjectProvider<PreOrderProfitService> serviceProvider;

    @Mock
    private PreOrderProfitService service;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private PreOrderProfitController controller;

    @BeforeEach
    void setUp() {
        controller = new PreOrderProfitController(
                serviceProvider,
                new PreOrderProfitCalculator(),
                businessAccessResolver
        );
    }

    @Test
    void listCandidatesRequiresPreOrderProfitStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context();
        List<PreOrderProfitCandidateView> expected = List.of(new PreOrderProfitCandidateView());
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRE_ORDER_PROFIT, "CANMAN")).thenReturn(context);
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(service.listCandidates(context, "CANMAN", "SA", null, null, null, null)).thenReturn(expected);

        List<PreOrderProfitCandidateView> result = controller.listCandidates(
                request,
                "CANMAN",
                "SA",
                null,
                null,
                null,
                null
        );

        assertSame(expected, result);
        verify(businessAccessResolver).requireStoreAccess(request, BusinessCapability.PRE_ORDER_PROFIT, "CANMAN");
    }

    @Test
    void createCandidateMapsValidationErrorToBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PreOrderProfitCandidateCommand command = candidateCommand();
        BusinessAccessContext context = context();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRE_ORDER_PROFIT, "CANMAN")).thenReturn(context);
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(service.createCandidate(context, command)).thenThrow(new IllegalArgumentException("采购单价必须大于 0。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.createCandidate(request, command)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    void calculateUsesBusinessAccessButDoesNotRequirePersistenceService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PreOrderProfitCalculationRequest command = calculationRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRE_ORDER_PROFIT, "CANMAN")).thenReturn(context());

        PreOrderProfitCalculationView view = controller.calculate(request, command);

        assertEquals("READY", view.getStatus());
        assertEquals(new BigDecimal("17.08"), view.getEstimatedProfit());
        verify(businessAccessResolver).requireStoreAccess(request, BusinessCapability.PRE_ORDER_PROFIT, "CANMAN");
    }

    @Test
    void missingPersistenceServiceReturnsServiceUnavailableForCrud() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRE_ORDER_PROFIT, "CANMAN")).thenReturn(context());
        when(serviceProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.listCandidates(request, "CANMAN", null, null, null, null, null)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(501L)
                .storeCodes(java.util.Set.of("CANMAN"))
                .storeOwnerUserIds(Map.of("CANMAN", 501L))
                .build();
    }

    private PreOrderProfitCandidateCommand candidateCommand() {
        PreOrderProfitCandidateCommand command = new PreOrderProfitCandidateCommand();
        command.setStoreCode("CANMAN");
        command.setSiteCode("SA");
        command.setPurchaseUrl("https://detail.1688.com/offer/123.html");
        command.setPurchasePriceRmb(new BigDecimal("12.50"));
        command.setLengthCm(new BigDecimal("18"));
        command.setWidthCm(new BigDecimal("8"));
        command.setHeightCm(new BigDecimal("8"));
        command.setActualWeightKg(new BigDecimal("0.30"));
        command.setCategoryId("home-kitchen-sa");
        command.setLogisticsCarrierId("et-sa-air-standard");
        command.setSalePrice(new BigDecimal("49"));
        command.setTargetMarginRate(new BigDecimal("0.30"));
        return command;
    }

    private PreOrderProfitCalculationRequest calculationRequest() {
        PreOrderProfitCalculationRequest request = new PreOrderProfitCalculationRequest();
        request.setStoreCode("CANMAN");
        request.setSiteCode("SA");
        request.setPurchaseUrl("https://detail.1688.com/offer/123.html");
        request.setPurchasePriceRmb(new BigDecimal("12.50"));
        request.setLengthCm(new BigDecimal("18"));
        request.setWidthCm(new BigDecimal("8"));
        request.setHeightCm(new BigDecimal("8"));
        request.setActualWeightKg(new BigDecimal("0.30"));
        request.setCategoryId("home-kitchen-sa");
        request.setLogisticsCarrierId("et-sa-air-standard");
        request.setSalePrice(new BigDecimal("49"));
        request.setTargetMarginRate(new BigDecimal("0.30"));
        return request;
    }
}
