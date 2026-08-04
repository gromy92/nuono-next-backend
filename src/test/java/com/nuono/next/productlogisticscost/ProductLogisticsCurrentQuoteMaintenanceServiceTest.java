package com.nuono.next.productlogisticscost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductLogisticsCostMapper;
import com.nuono.next.procurementorder.ProductForwarderEligibilityProductScope;
import com.nuono.next.procurementorder.ProductForwarderEligibilityProductService;
import com.nuono.next.procurementorder.WarehouseForwarderEligibilityService;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ManualCurrentQuoteWithEligibilityCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ProductMatchRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductLogisticsCurrentQuoteMaintenanceServiceTest {

    @Mock
    private ProductLogisticsCostMapper mapper;

    @Mock
    private ProductLogisticsCostLedgerService ledgerService;

    @Mock
    private ProductForwarderEligibilityProductService eligibilityService;

    private ProductLogisticsCurrentQuoteMaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new ProductLogisticsCurrentQuoteMaintenanceService(
                mapper,
                ledgerService,
                eligibilityService
        );
    }

    @Test
    void shouldReadDefaultOrExceptionalStatusUsingCanonicalProductIdentity() {
        ProductMatchRow product = product();
        when(mapper.selectProductMatches(307L, "STR108065-NSA", "papersay001", "SA"))
                .thenReturn(List.of(product));
        when(eligibilityService.currentStatus(argThat(this::matchesScope)))
                .thenReturn("INQUIRY_REQUIRED");

        var result = service.currentEligibility(
                307L, "STR108065-NSA", "papersay001", "sa", "yite", "sea"
        );

        assertThat(result.partnerSku).isEqualTo("PAPERSAY001");
        assertThat(result.eligibilityStatus).isEqualTo("INQUIRY_REQUIRED");
    }

    @Test
    void shouldReadExceptionalStatusesForTheWholeRouteInOneBatch() {
        when(mapper.selectLogicalStoreIdByStoreCode(307L, "STR108065-NSA")).thenReturn(108065L);
        when(eligibilityService.currentStatusesForRoute(307L, 108065L, "SA", "YITE", "SEA"))
                .thenReturn(Map.of("PAPERSAY001", "UNSUPPORTED"));

        var result = service.currentEligibilities(
                307L, "STR108065-NSA", "sa", "yite", "sea"
        );

        assertThat(result.items).hasSize(1);
        assertThat(result.items.get(0).partnerSku).isEqualTo("PAPERSAY001");
        assertThat(result.items.get(0).eligibilityStatus).isEqualTo("UNSUPPORTED");
    }

    @Test
    void shouldSaveUnsupportedStatusWithoutCreatingAQuote() {
        ManualCurrentQuoteWithEligibilityCommand command = command("UNSUPPORTED");
        when(mapper.selectProductMatches(307L, "STR108065-NSA", "PAPERSAY001", "SA"))
                .thenReturn(List.of(product()));
        when(eligibilityService.updateProductRule(
                argThat(this::matchesScope),
                org.mockito.ArgumentMatchers.eq("UNSUPPORTED"),
                org.mockito.ArgumentMatchers.eq(901L)
        )).thenReturn("UNSUPPORTED");

        var result = service.maintainCurrentQuote(307L, 901L, command);

        assertThat(result.eligibilityStatus).isEqualTo("UNSUPPORTED");
        assertThat(result.currentCost).isNull();
        verify(ledgerService, never()).manualCurrentQuote(307L, 901L, command);
    }

    @Test
    void shouldSaveSupportedStatusAndQuoteInOneTransactionBoundary() {
        ManualCurrentQuoteWithEligibilityCommand command = command("SUPPORTED");
        CurrentCostRow currentCost = new CurrentCostRow();
        when(mapper.selectProductMatches(307L, "STR108065-NSA", "PAPERSAY001", "SA"))
                .thenReturn(List.of(product()));
        when(eligibilityService.updateProductRule(
                argThat(this::matchesScope),
                org.mockito.ArgumentMatchers.eq("SUPPORTED"),
                org.mockito.ArgumentMatchers.eq(901L)
        )).thenReturn("SUPPORTED");
        when(ledgerService.manualCurrentQuote(307L, 901L, command)).thenReturn(currentCost);

        var result = service.maintainCurrentQuote(307L, 901L, command);

        assertThat(result.eligibilityStatus).isEqualTo("SUPPORTED");
        assertThat(result.currentCost).isSameAs(currentCost);
    }

    private ManualCurrentQuoteWithEligibilityCommand command(String status) {
        ManualCurrentQuoteWithEligibilityCommand command = new ManualCurrentQuoteWithEligibilityCommand();
        command.storeCode = "STR108065-NSA";
        command.partnerSku = "PAPERSAY001";
        command.siteCode = "SA";
        command.forwarderCode = "YITE";
        command.forwarderName = "义特";
        command.transportMode = "SEA";
        command.cargoCategoryCode = "A";
        command.cargoCategoryName = "A类别运费";
        command.chargeUnit = "CBM";
        command.unitCostCny = new BigDecimal("1540.00");
        command.eligibilityStatus = status;
        return command;
    }

    private ProductMatchRow product() {
        ProductMatchRow row = new ProductMatchRow();
        row.logicalStoreId = 108065L;
        row.productMasterId = 701L;
        row.productVariantId = 702L;
        row.partnerSku = "PAPERSAY001";
        row.siteCode = "SA";
        return row;
    }

    private boolean matchesScope(ProductForwarderEligibilityProductScope scope) {
        return scope != null
                && Long.valueOf(307L).equals(scope.ownerUserId)
                && Long.valueOf(108065L).equals(scope.logicalStoreId)
                && "PAPERSAY001".equals(scope.partnerSku)
                && "SA".equals(scope.siteCode)
                && "YITE".equals(scope.forwarderCode)
                && "SEA".equals(scope.transportMode);
    }
}
