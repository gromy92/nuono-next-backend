package com.nuono.next.productlogisticscost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ManualCurrentQuoteWithEligibilityCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.EligibilityView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.EligibilityListView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.ManualCurrentQuoteWithEligibilityResult;
import java.lang.reflect.Method;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@ExtendWith(MockitoExtension.class)
class ProductLogisticsEligibilityControllerTest {

    @Mock
    private ProductLogisticsCurrentQuoteMaintenanceService service;

    @Mock
    private BusinessAccessResolver accessResolver;

    @Mock
    private HttpServletRequest request;

    private ProductLogisticsEligibilityController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductLogisticsEligibilityController(service, accessResolver);
    }

    @Test
    void shouldExposeEligibilityReadAndCompositeMaintenanceRoutes() throws NoSuchMethodException {
        RequestMapping base = ProductLogisticsEligibilityController.class.getAnnotation(RequestMapping.class);
        assertThat(base.value()).containsExactly("/api/product-logistics-costs");

        Method read = ProductLogisticsEligibilityController.class.getMethod(
                "currentEligibility",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                HttpServletRequest.class
        );
        assertThat(read.getAnnotation(GetMapping.class).value()).containsExactly("/eligibility/current");

        Method list = ProductLogisticsEligibilityController.class.getMethod(
                "currentEligibilities",
                String.class,
                String.class,
                String.class,
                String.class,
                HttpServletRequest.class
        );
        assertThat(list.getAnnotation(GetMapping.class).value()).containsExactly("/eligibility/current-list");

        Method maintain = ProductLogisticsEligibilityController.class.getMethod(
                "maintainCurrentQuote",
                ManualCurrentQuoteWithEligibilityCommand.class,
                HttpServletRequest.class
        );
        assertThat(maintain.getAnnotation(PostMapping.class).value())
                .containsExactly("/current/manual-with-eligibility");
    }

    @Test
    void shouldReadEligibilityWithinInTransitStoreScope() {
        BusinessAccessContext context = context();
        EligibilityView view = new EligibilityView();
        when(accessResolver.requireStoreAccess(
                request, BusinessCapability.IN_TRANSIT_GOODS, "STR108065-NSA"
        )).thenReturn(context);
        when(service.currentEligibility(
                307L, "STR108065-NSA", "PAPERSAY001", "SA", "YITE", "SEA"
        )).thenReturn(view);

        EligibilityView result = controller.currentEligibility(
                "STR108065-NSA", "PAPERSAY001", "SA", "YITE", "SEA", request
        );

        assertSame(view, result);
    }

    @Test
    void shouldReadRouteEligibilityListWithinInTransitStoreScope() {
        BusinessAccessContext context = context();
        EligibilityListView view = new EligibilityListView();
        when(accessResolver.requireStoreAccess(
                request, BusinessCapability.IN_TRANSIT_GOODS, "STR108065-NSA"
        )).thenReturn(context);
        when(service.currentEligibilities(
                307L, "STR108065-NSA", "SA", "YITE", "SEA"
        )).thenReturn(view);

        EligibilityListView result = controller.currentEligibilities(
                "STR108065-NSA", "SA", "YITE", "SEA", request
        );

        assertSame(view, result);
    }

    @Test
    void shouldRequireLogisticsQuoteCapabilityAndSessionOperatorForMaintenance() {
        BusinessAccessContext context = context();
        ManualCurrentQuoteWithEligibilityCommand command = new ManualCurrentQuoteWithEligibilityCommand();
        command.storeCode = "STR108065-NSA";
        ManualCurrentQuoteWithEligibilityResult view = new ManualCurrentQuoteWithEligibilityResult();
        when(accessResolver.requireStoreAccess(
                request, BusinessCapability.LOGISTICS_QUOTE, "STR108065-NSA"
        )).thenReturn(context);
        when(service.maintainCurrentQuote(307L, 901L, command)).thenReturn(view);

        ManualCurrentQuoteWithEligibilityResult result = controller.maintainCurrentQuote(command, request);

        assertSame(view, result);
        verify(service).maintainCurrentQuote(307L, 901L, command);
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR108065-NSA"))
                .menuPaths(Set.of(
                        "/purchase/product-logistics-costs",
                        "/purchase/logistics-quote"
                ))
                .build();
    }
}
