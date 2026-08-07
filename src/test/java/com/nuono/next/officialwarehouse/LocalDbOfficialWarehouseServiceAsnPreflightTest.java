package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseAsnPreflightTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AsnDetail;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CreateAsnCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CreateAsnLineCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnShippingBatchLinkInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ProductCandidateRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchSourceAllocationRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import com.nuono.next.web.ApiProblemException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;

class LocalDbOfficialWarehouseServiceAsnPreflightTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private OfficialWarehouseMapper mapper;
    private NoonSessionGateway gateway;
    private NoonSalesReportBindingResolver bindingResolver;
    private OfficialWarehouseNoonInboundClient inboundClient;
    private NoonSession session;
    private LocalDbOfficialWarehouseService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OfficialWarehouseMapper.class);
        gateway = mock(NoonSessionGateway.class);
        bindingResolver = mock(NoonSalesReportBindingResolver.class);
        inboundClient = mock(OfficialWarehouseNoonInboundClient.class);
        session = testNoonSession("_npsid=read-session");
        service = new LocalDbOfficialWarehouseService(
                mapper, gateway, bindingResolver, mock(NoonHttpCallLogService.class), inboundClient,
                objectMapper, NoonRiskBackoffGuard.disabled(), new NoonPullFailurePolicy(),
                OfficialWarehouseAppointmentAuthRecovery.disabled());
        when(mapper.selectStoreSite(307L, "STR108065-NSA", "SA")).thenReturn(site());
        when(bindingResolver.resolve(any())).thenReturn(binding());
        when(gateway.loginWithPersistedCookiePinnedEgress(
                307L, "merchant@example.com", "persisted-cookie", "PRJ108065",
                "STR108065-NSA", "fbn.noon.partners", 443)).thenReturn(session);
        when(gateway.loginWithPersistedCookiePinnedEgress(
                307L, "merchant@example.com", session.exportAuthCookieHeader(), "PRJ108065",
                "STR108065-NSA", "fbn.noon.partners", 443)).thenReturn(session);
    }

    @Test
    void sggrb329MissingPbarcodeBlocksAtTheRealCreateAsnCallSeam() {
        stubCandidates(List.of(candidate("SGGRB329", "PSKU-329", "N329", 329L)));
        when(mapper.nextAsnLineId()).thenReturn(510001L);
        when(mapper.nextAsnId()).thenReturn(500001L);
        when(inboundClient.searchProductOffersPage(any(), any(), any(), any()))
                .thenReturn(offerPage("SGGRB329", "PSKU-329"));

        assertThatThrownBy(() -> service.createAsn(access(), command(line("SGGRB329", 20))))
                .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                    assertThat(problem.getCode())
                            .isEqualTo("OFFICIAL_WAREHOUSE_ASN_PRODUCT_PREFLIGHT_FAILED");
                    assertThat(problem.isPartialSuccess()).isFalse();
                });

        verify(inboundClient).searchProductOffersPage(any(), any(), any(), any());
        verify(inboundClient, never()).createAsn(any(), any(), any(), any());
        verify(mapper, never()).insertAsn(any());
        verify(mapper, never()).insertAsnLine(any());
    }

    @Test
    void changedBatchQuantityBlocksBeforeSessionOrProviderRequest() {
        stubCandidates(List.of(candidate("SGGRB329", "PSKU-329", "N329", 329L)));
        ShippingBatchSourceAllocationRecord fresh = allocation(20);
        ShippingBatchSourceAllocationRecord stale = allocation(10);
        when(mapper.listShippingBatchSourceAllocations(
                anyLong(), anyString(), anyString(), anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(new ArrayList<>(List.of(fresh)), new ArrayList<>(List.of(stale)));
        when(mapper.nextAsnLineId()).thenReturn(510001L);
        when(mapper.nextAsnId()).thenReturn(500001L);
        CreateAsnCommand command = command(line("SGGRB329", 20));
        command.shippingBatchIds = List.of("53023");

        assertThatThrownBy(() -> service.createAsn(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("批次可用 10");

        verify(bindingResolver, never()).resolve(any());
        verify(inboundClient, never()).searchProductOffersPage(any(), any(), any(), any());
        verify(inboundClient, never()).createAsn(any(), any(), any(), any());
        verify(mapper, never()).insertAsn(any());
    }

    @Test
    void selectedLogisticsBarcodeMustEqualTheCurrentNoonPbarcode() {
        stubCandidates(List.of(candidate("SGGRB290", "PSKU-290", "N290", 290L)));
        ShippingBatchSourceAllocationRecord allocation = allocation("SGGRB290", "SGGRB329", 20);
        when(mapper.listShippingBatchSourceAllocations(
                anyLong(), anyString(), anyString(), anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(new ArrayList<>(List.of(allocation)), new ArrayList<>(List.of(allocation)));
        when(mapper.nextAsnLineId()).thenReturn(510001L);
        when(mapper.nextAsnShippingBatchLinkId()).thenReturn(520001L);
        when(mapper.nextAsnId()).thenReturn(500001L);
        when(inboundClient.searchProductOffersPage(any(), any(), any(), any()))
                .thenReturn(offerPage("SGGRB290", "PSKU-290", "SGGRB290"));
        CreateAsnCommand command = command(line("SGGRB290", 20));
        command.shippingBatchIds = List.of("53023");

        assertThatThrownBy(() -> service.createAsn(access(), command))
                .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                    List<?> invalidLines = (List<?>) problem.getDetails().get("invalidLines");
                    Map<?, ?> issue = (Map<?, ?>) invalidLines.get(0);
                    assertThat(issue.get("sourceBarcode")).isEqualTo("SGGRB329");
                    assertThat(issue.get("reasonCode")).isEqualTo("BARCODE_PBARCODE_MISMATCH");
                });

        verify(mapper, never()).insertAsn(any());
        verify(inboundClient, never()).createAsn(any(), any(), any(), any());
    }

    @Test
    void validMultiLineProofPreservesTheExistingProviderWriteOrder() {
        stubCandidates(List.of(
                candidate("SGGRB329", "PSKU-329", "N329", 329L),
                candidate("PAPERSAYSB014", "PSKU-014", "N014", 14L)));
        when(mapper.nextAsnLineId()).thenReturn(510001L, 510002L);
        when(mapper.nextAsnId()).thenReturn(500001L);
        when(inboundClient.searchProductOffersPage(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String search = invocation.<JsonNode>getArgument(3).path("search").asText();
                    return "SGGRB329".equals(search)
                            ? offerPage(search, "PSKU-329", "PB-329")
                            : offerPage(search, "PSKU-014", "PB-014");
                });
        when(inboundClient.createAsn(any(), any(), any(), any())).thenReturn(createResponse());
        when(inboundClient.routeWarehouse(any(), any(), any(), anyString(), any()))
                .thenReturn(routingResponse());
        when(inboundClient.createLines(any(), any(), any(), anyString(), any()))
                .thenReturn(objectMapper.createObjectNode());
        when(inboundClient.queryAsnDetail(any(), any(), any(), anyString()))
                .thenReturn(new AsnDetail("SEALED"));
        when(mapper.selectAuthorizedAsn(
                Map.of("STR108065-NSA", 307L),
                500001L
        )).thenReturn(asnRecord());
        when(mapper.listAsnShippingBatchLinks(500001L)).thenReturn(List.of());
        when(mapper.listAsnLines(500001L)).thenReturn(List.of());
        when(mapper.listAsnInboundReceipts(anyLong(), any())).thenReturn(List.of());

        assertThat(service.createAsn(access(), command(
                line("SGGRB329", 2), line("PAPERSAYSB014", 3))).noonAsnNr)
                .isEqualTo("A05834999PN");

        InOrder order = inOrder(inboundClient);
        order.verify(inboundClient, times(2))
                .searchProductOffersPage(any(), any(), any(), any());
        order.verify(inboundClient).createAsn(any(), any(), any(), any());
        order.verify(inboundClient).routeWarehouse(any(), any(), any(), anyString(), any());
        order.verify(inboundClient).createLines(any(), any(), any(), anyString(), any());
    }

    @Test
    void unrelatedManualSkuJoinsTheAsnWithoutConsumingTheSelectedBatch() {
        stubCandidates(List.of(
                candidate("SGGRB329", "PSKU-329", "N329", 329L),
                candidate("PAPERSAYSB014", "PSKU-014", "N014", 14L)));
        ShippingBatchSourceAllocationRecord allocation = allocation("SGGRB329", "PB-329", 5);
        when(mapper.listShippingBatchSourceAllocations(
                anyLong(), anyString(), anyString(), anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(new ArrayList<>(List.of(allocation)), new ArrayList<>(List.of(allocation)));
        when(mapper.nextAsnLineId()).thenReturn(510001L, 510002L);
        when(mapper.nextAsnShippingBatchLinkId()).thenReturn(520001L);
        when(mapper.nextAsnId()).thenReturn(500001L);
        when(inboundClient.searchProductOffersPage(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String search = invocation.<JsonNode>getArgument(3).path("search").asText();
                    return "SGGRB329".equals(search)
                            ? offerPage(search, "PSKU-329", "PB-329")
                            : offerPage(search, "PSKU-014", "PB-014");
                });
        when(inboundClient.createAsn(any(), any(), any(), any())).thenReturn(createResponse());
        when(inboundClient.routeWarehouse(any(), any(), any(), anyString(), any())).thenReturn(routingResponse());
        when(inboundClient.createLines(any(), any(), any(), anyString(), any())).thenReturn(objectMapper.createObjectNode());
        when(inboundClient.queryAsnDetail(any(), any(), any(), anyString())).thenReturn(new AsnDetail("SEALED"));
        when(mapper.selectAuthorizedAsn(Map.of("STR108065-NSA", 307L), 500001L)).thenReturn(asnRecord());
        when(mapper.listAsnShippingBatchLinks(500001L)).thenReturn(List.of());
        when(mapper.listAsnLines(500001L)).thenReturn(List.of());
        when(mapper.listAsnInboundReceipts(anyLong(), any())).thenReturn(List.of());

        CreateAsnLineCommand manual = line("PAPERSAYSB014", 3);
        manual.manualQuantity = 3;
        CreateAsnCommand command = command(line("SGGRB329", 5), manual);
        command.shippingBatchIds = List.of("53023");

        service.createAsn(access(), command);

        ArgumentCaptor<AsnShippingBatchLinkInsertRecord> linkCaptor =
                ArgumentCaptor.forClass(AsnShippingBatchLinkInsertRecord.class);
        verify(mapper).insertAsnShippingBatchLink(linkCaptor.capture());
        assertThat(linkCaptor.getValue().partnerSku).isEqualTo("SGGRB329");
        assertThat(linkCaptor.getValue().quantity).isEqualTo(5);
        ArgumentCaptor<AsnLineInsertRecord> lineCaptor = ArgumentCaptor.forClass(AsnLineInsertRecord.class);
        verify(mapper, times(2)).insertAsnLine(lineCaptor.capture());
        assertThat(lineCaptor.getAllValues()).anySatisfy(row -> {
            assertThat(row.partnerSku).isEqualTo("PAPERSAYSB014");
            assertThat(row.quantity).isEqualTo(3);
            assertThat(row.manualQuantity).isEqualTo(3);
            assertThat(row.shippingBatchQuantity).isZero();
            assertThat(row.sourceBarcodes).isEmpty();
        });
    }

    @Test
    void rejectsManualQuantityGreaterThanTheLineTotalBeforeAnyProviderWork() {
        CreateAsnLineCommand invalid = line("SGGRB329", 2);
        invalid.manualQuantity = 3;

        assertThatThrownBy(() -> service.createAsn(access(), command(invalid)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("手工添加数量");

        verify(bindingResolver, never()).resolve(any());
        verify(inboundClient, never()).createAsn(any(), any(), any(), any());
        verify(mapper, never()).insertAsn(any());
    }

    @Test
    void timeoutAfterCreateKeepsTheExistingReconciliationStateWithoutReplay() {
        stubCandidates(List.of(candidate("SGGRB329", "PSKU-329", "N329", 329L)));
        when(mapper.nextAsnLineId()).thenReturn(510001L);
        when(mapper.nextAsnId()).thenReturn(500001L);
        when(inboundClient.searchProductOffersPage(any(), any(), any(), any()))
                .thenReturn(offerPage("SGGRB329", "PSKU-329", "PB-329"));
        when(inboundClient.createAsn(any(), any(), any(), any())).thenReturn(createResponse());
        when(inboundClient.routeWarehouse(any(), any(), any(), anyString(), any()))
                .thenThrow(new IllegalStateException("provider timeout"));

        assertThatThrownBy(() -> service.createAsn(access(), command(line("SGGRB329", 5))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider timeout");

        verify(inboundClient).createAsn(any(), any(), any(), any());
        verify(inboundClient, never()).createLines(any(), any(), any(), anyString(), any());
        verify(mapper, never()).softDeleteAsnShippingBatchLinks(anyLong(), anyLong());
        verify(mapper, never()).softDeleteAsnLines(anyLong(), anyLong());
        verify(mapper, never()).softDeletePreSubmitAsn(anyLong(), anyLong());
    }

    private void stubCandidates(List<ProductCandidateRecord> candidates) {
        when(mapper.listProductCandidates(
                anyLong(), anyString(), anyString(), isNull(), anyCollection(), anyCollection(), anyInt()))
                .thenReturn(candidates);
    }

}
