package com.nuono.next.productanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
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
class ProductLifecycleAnalysisControllerAccessTest {

    @Mock
    private ProductLifecycleAnalysisService service;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private ProductLifecycleAnalysisController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductLifecycleAnalysisController(service, businessAccessResolver);
    }

    @Test
    void overviewUsesSalesDataAccessAndStoreOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = BusinessAccessContext.builder()
                .businessOwnerUserId(10001L)
                .storeCodes(Set.of("STR245027"))
                .storeOwnerUserIds(Map.of("STR245027", 307L))
                .build();
        ProductLifecycleAnalysisOverviewView expected = ProductLifecycleAnalysisOverviewView.empty("STR245027", "AE");
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027"))
                .thenReturn(context);
        when(service.getOverview(any(ProductLifecycleAnalysisQuery.class))).thenReturn(expected);

        ProductLifecycleAnalysisOverviewView result = controller.getOverview("STR245027", "AE", request);

        assertSame(expected, result);
        verify(businessAccessResolver).requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027");
        ArgumentCaptor<ProductLifecycleAnalysisQuery> queryCaptor =
                ArgumentCaptor.forClass(ProductLifecycleAnalysisQuery.class);
        verify(service).getOverview(queryCaptor.capture());
        ProductLifecycleAnalysisQuery query = queryCaptor.getValue();
        assertEquals(307L, query.getOwnerUserId());
        assertEquals("STR245027", query.getStoreCode());
        assertEquals("AE", query.getSiteCode());
    }

    @Test
    void overviewRejectsBlankStoreOrSiteBeforeResolvingAccess() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.getOverview(" ", "AE", new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verifyNoInteractions(businessAccessResolver, service);
    }

    @Test
    void recalculateUsesSalesDataAccessStoreOwnerAndSessionUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(10002L)
                .businessOwnerUserId(10001L)
                .storeCodes(Set.of("STR245027-NAE"))
                .storeOwnerUserIds(Map.of("STR245027-NAE", 307L))
                .build();
        ProductLifecycleAnalysisRecalculationView expected = new ProductLifecycleAnalysisRecalculationView(
                72010L,
                "succeeded",
                "生命周期计算完成。",
                "STR245027-NAE",
                "AE",
                java.time.LocalDate.of(2026, 5, 22),
                37,
                2,
                1,
                12
        );
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-NAE"))
                .thenReturn(context);
        when(service.recalculate(any(ProductLifecycleAnalysisQuery.class), any(Long.class))).thenReturn(expected);

        ProductLifecycleAnalysisRecalculationView result = controller.recalculate("STR245027-NAE", "AE", request);

        assertSame(expected, result);
        verify(businessAccessResolver).requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-NAE");
        ArgumentCaptor<ProductLifecycleAnalysisQuery> queryCaptor =
                ArgumentCaptor.forClass(ProductLifecycleAnalysisQuery.class);
        ArgumentCaptor<Long> operatorCaptor = ArgumentCaptor.forClass(Long.class);
        verify(service).recalculate(queryCaptor.capture(), operatorCaptor.capture());
        assertEquals(307L, queryCaptor.getValue().getOwnerUserId());
        assertEquals("STR245027-NAE", queryCaptor.getValue().getStoreCode());
        assertEquals("AE", queryCaptor.getValue().getSiteCode());
        assertEquals(10002L, operatorCaptor.getValue());
    }

    @Test
    void recalculateRejectsBlankStoreOrSiteBeforeResolvingAccess() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.recalculate("STR245027-NAE", " ", new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verifyNoInteractions(businessAccessResolver, service);
    }
}
