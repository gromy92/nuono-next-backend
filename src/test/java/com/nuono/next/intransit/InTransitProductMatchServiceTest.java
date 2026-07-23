package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import com.nuono.next.intransit.InTransitProductMatchViews.RematchView;
import com.nuono.next.intransit.InTransitProductMatchViews.PreparationView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitProductMatchServiceTest {
    @Mock
    private InTransitGoodsMapper mapper;
    @Mock
    private InTransitBatchService batchService;
    @Mock
    private InTransitGoodsAccessScopeService accessScopeService;
    private InTransitProductMatchService service;

    @BeforeEach
    void setUp() {
        service = new InTransitProductMatchService(mapper, batchService, accessScopeService);
    }

    @Test
    void shouldPersistRawLineWithoutProductIdentity() {
        SaveLineCommand source = sourceLine();
        PackageRow itemPackage = new PackageRow();
        itemPackage.setId(54001L);
        when(mapper.nextProductMatchCandidateId()).thenReturn(59001L);
        when(mapper.selectPackageByBoxNo(10002L, 53001L, "-3")).thenReturn(itemPackage);

        service.saveCandidate(source, "PAPERSAYSB440");

        ArgumentCaptor<InTransitProductMatchCandidate> captor =
                ArgumentCaptor.forClass(InTransitProductMatchCandidate.class);
        verify(mapper).insertProductMatchCandidate(captor.capture());
        InTransitProductMatchCandidate saved = captor.getValue();
        assertEquals(59001L, saved.getId());
        assertEquals(54001L, saved.getPackageId());
        assertEquals("PAPERSAYSB440", saved.getSourceBarcode());
        assertEquals("SOURCE-MSKU", saved.getSourceMsku());
        assertEquals(new BigDecimal("3.25"), saved.getCartonWeightKg());
        assertEquals(new BigDecimal("0.018"), saved.getCartonVolumeCbm());
        assertEquals("UNMATCHED", saved.getMatchStatus());
        verify(batchService, never()).saveLine(any());
    }

    @Test
    void shouldKeepMatchedStatusWhenRepeatedLandingDataIsUnchanged() {
        SaveLineCommand source = sourceLine();
        InTransitProductMatchCandidate existing = matchedCandidateFromSource(source);
        when(mapper.selectProductMatchCandidate(10002L, 53001L, "-3", "PAPERSAYSB440"))
                .thenReturn(existing);

        service.saveCandidate(source, "PAPERSAYSB440");

        ArgumentCaptor<InTransitProductMatchCandidate> captor =
                ArgumentCaptor.forClass(InTransitProductMatchCandidate.class);
        verify(mapper).updateProductMatchCandidate(captor.capture());
        assertEquals("MATCHED", captor.getValue().getMatchStatus());
        assertEquals(null, captor.getValue().getMatchMessage());
    }

    @Test
    void shouldReopenMatchingWhenRawBusinessDataChanges() {
        SaveLineCommand source = sourceLine();
        InTransitProductMatchCandidate existing = matchedCandidateFromSource(source);
        source.setShippedQuantity(13);
        when(mapper.selectProductMatchCandidate(10002L, 53001L, "-3", "PAPERSAYSB440"))
                .thenReturn(existing);

        service.saveCandidate(source, "PAPERSAYSB440");

        ArgumentCaptor<InTransitProductMatchCandidate> captor =
                ArgumentCaptor.forClass(InTransitProductMatchCandidate.class);
        verify(mapper).updateProductMatchCandidate(captor.capture());
        assertEquals("UNMATCHED", captor.getValue().getMatchStatus());
        assertEquals("物流 barcode 尚未匹配系统商品。", captor.getValue().getMatchMessage());
    }

    @Test
    void shouldPromoteOnlyCandidatesWhoseBarcodeNowMatches() {
        InTransitProductMatchCandidate matched = candidate("PAPERSAYSB440");
        InTransitProductMatchCandidate pending = candidate("STILL-UNKNOWN");
        when(mapper.listProductMatchCandidates(10002L, 53001L))
                .thenReturn(List.of(matched, pending), List.of(pending));
        when(mapper.selectProductIdentityByBarcode(10002L, "PAPERSAYSB440"))
                .thenReturn(new BarcodeProductIdentity(50001L, "PAPERSAYS440"));

        RematchView result = service.rematch(context(), 10002L, 90001L, 53001L);

        ArgumentCaptor<SaveLineCommand> captor = ArgumentCaptor.forClass(SaveLineCommand.class);
        verify(batchService).saveLine(captor.capture());
        assertEquals("PAPERSAYS440", captor.getValue().getPsku());
        assertEquals("PAPERSAYSB440", captor.getValue().getSku());
        assertEquals("SOURCE-MSKU", captor.getValue().getMsku());
        verify(mapper).resolveProductMatchCandidate(
                10002L, 53001L, "-3", "PAPERSAYSB440", 90001L
        );
        verify(mapper).markProductMatchCandidateUnmatched(
                10002L, pending.getId(), "物流 barcode 尚未匹配系统商品。", 90001L
        );
        assertEquals(1, result.getMatchedCount());
        assertEquals(1, result.getPendingCount());
        assertEquals("STILL-UNKNOWN", result.getPendingItems().get(0).getSourceBarcode());
    }

    @Test
    void shouldPrepareAllRawRowsWhenOfficialWarehouseNeedsProducts() {
        InTransitProductMatchCandidate matched = candidate("PAPERSAYSB440");
        InTransitProductMatchCandidate pending = candidate("STILL-UNKNOWN");
        when(mapper.listProductLandingBatchIds(10002L, "STR100", "SA")).thenReturn(List.of(53001L));
        when(mapper.listProductMatchCandidates(10002L, 53001L))
                .thenReturn(List.of(matched, pending), List.of(pending));
        when(mapper.selectProductIdentityByBarcode(10002L, "PAPERSAYSB440"))
                .thenReturn(new BarcodeProductIdentity(50001L, "PAPERSAYS440"));

        PreparationView result = service.prepareForStoreSite(context(), "STR100", "SA");

        assertEquals(1, result.getBatchCount());
        assertEquals(1, result.getMatchedCount());
        assertEquals(1, result.getPendingCount());
        verify(batchService).saveLine(any(SaveLineCommand.class));
    }

    private SaveLineCommand sourceLine() {
        SaveLineCommand command = new SaveLineCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setBoxNo("-3");
        command.setSku("PAPERSAYSB440");
        command.setMsku("SOURCE-MSKU");
        command.setProductName("物流原始名称");
        command.setStoreCode("STR100");
        command.setSiteCode("SA");
        command.setShippedQuantity(12);
        command.setReceivedQuantity(2);
        command.setCartonCount(1);
        command.setUnitsPerCarton(12);
        command.setCartonWeightKg(new BigDecimal("3.25"));
        command.setCartonVolumeCbm(new BigDecimal("0.018"));
        return command;
    }

    private InTransitProductMatchCandidate candidate(String barcode) {
        InTransitProductMatchCandidate candidate = new InTransitProductMatchCandidate();
        candidate.setId((long) barcode.hashCode());
        candidate.setOwnerUserId(10002L);
        candidate.setBatchId(53001L);
        candidate.setBoxNo("-3");
        candidate.setSourceBarcode(barcode);
        candidate.setSourceMsku("SOURCE-MSKU");
        candidate.setShippedQuantity(12);
        candidate.setReceivedQuantity(2);
        candidate.setCartonCount(1);
        candidate.setUnitsPerCarton(12);
        return candidate;
    }

    private InTransitProductMatchCandidate matchedCandidateFromSource(SaveLineCommand source) {
        InTransitProductMatchCandidate candidate = candidate(source.getSku());
        candidate.setSourcePsku("PAPERSAYSB440");
        candidate.setProductName(source.getProductName());
        candidate.setStoreCode(source.getStoreCode());
        candidate.setSiteCode(source.getSiteCode());
        candidate.setCartonWeightKg(source.getCartonWeightKg());
        candidate.setCartonVolumeCbm(source.getCartonVolumeCbm());
        candidate.setMatchStatus("MATCHED");
        return candidate;
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .businessOwnerUserId(10002L)
                .sessionUserId(90001L)
                .build();
    }
}
