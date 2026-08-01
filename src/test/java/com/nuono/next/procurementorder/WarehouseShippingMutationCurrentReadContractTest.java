package com.nuono.next.procurementorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseShippingMutationCurrentReadContractTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/com/nuono/next/procurementorder/LocalDbProcurementPurchaseOrderService.java");
    private static final String CURRENT_LOCK = "requireShippingOrderAccessForUpdate(";
    private static final String PROJECTION_READ = "requireShippingOrderAccess(";

    @Test
    void allTenShippingMutationsStartFromTheScopedCurrentLock() throws IOException {
        String source = Files.readString(SERVICE);
        List<String> mutations = List.of(
                "public ShippingOrderView updateShippingOrder(",
                "public ShippingOrderView updateShippingOrderLineYiteMaterial(",
                "public ShippingOrderView updateShippingOrderLineQuote(",
                "public ShippingOrderView updateShippingOrderLineQuotes(",
                "public ShippingOrderView updateShippingOrderLineEligibility(",
                "public ShippingOrderView reassignShippingOrderLines(",
                "public PurchaseOrderLogisticsQuoteReportExportView exportShippingOrderLogisticsQuoteReport(\n"
                        + "            BusinessAccessContext access,\n            String shippingOrderId,\n"
                        + "            String forwarderCode,\n            String routeCode,\n            boolean missingOnly,",
                "public PurchaseOrderLogisticsQuoteImportView importShippingOrderLogisticsQuoteReport(\n"
                        + "            BusinessAccessContext access,\n            String shippingOrderId,\n"
                        + "            InputStream input,\n            String filename,\n"
                        + "            ShippingOrderSegmentScopeCommand command",
                "public ShippingOrderSubmitView submitShippingOrder(",
                "public LogisticsBillView generateShippingOrderExpectedBill(\n"
                        + "            BusinessAccessContext access,\n            String shippingOrderId,\n"
                        + "            ShippingOrderSegmentScopeCommand command"
        );

        assertEquals(10, mutations.size());
        for (String signature : mutations) {
            String body = method(source, signature);
            int currentLock = body.indexOf(CURRENT_LOCK);
            int directMapperRead = body.indexOf("mapper.");
            assertTrue(currentLock >= 0, signature);
            assertFalse(body.contains(PROJECTION_READ), signature);
            assertTrue(directMapperRead < 0 || currentLock < directMapperRead, signature);
        }
        assertEquals(11, occurrences(source, CURRENT_LOCK));
    }

    @Test
    void onlyTheTwoShippingReadEndpointsUseTheProjectionRead() throws IOException {
        String source = Files.readString(SERVICE);
        List<String> readEndpoints = List.of(
                "public ShippingOrderView getShippingOrder(",
                "public PurchaseOrderLogisticsQuoteOptionsView listShippingOrderLogisticsQuoteOptions(\n"
                        + "            BusinessAccessContext access,\n            String shippingOrderId,\n"
                        + "            ShippingOrderSegmentScopeCommand command"
        );

        for (String signature : readEndpoints) {
            String body = method(source, signature);
            assertTrue(body.contains(PROJECTION_READ), signature);
            assertFalse(body.contains(CURRENT_LOCK), signature);
        }
        assertEquals(3, occurrences(source, PROJECTION_READ));
    }

    @Test
    void failedMutationStopsAfterScopedLockWithoutProjectionReadOrWrite() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        LocalDbProcurementPurchaseOrderService service = ProcurementPurchaseOrderServiceTestFactory.create(
                mapper, mock(ProductSelectionMapper.class), mock(LocalDbAli1688CollectionService.class),
                new ObjectMapper(), mock(WarehouseLogisticsQuotePriceService.class));
        ShippingOrderRecord order = new ShippingOrderRecord();
        order.id = 290001L;
        order.ownerUserId = 307L;
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(order);
        UpdateShippingOrderCommand command = new UpdateShippingOrderCommand();
        command.title = " ";

        assertThrows(IllegalArgumentException.class,
                () -> service.updateShippingOrder(access(), "290001", command));

        verify(mapper).selectShippingOrderByIdForUpdate(290001L, 307L);
        verify(mapper, never()).selectShippingOrderById(290001L);
        verifyNoMoreInteractions(mapper);
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .build();
    }

    private static int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method signature: " + signature);
        }
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char value = source.charAt(index);
            depth += value == '{' ? 1 : value == '}' ? -1 : 0;
            if (depth == 0) {
                return source.substring(start, index + 1);
            }
        }
        throw new AssertionError("Unclosed method: " + signature);
    }
}
