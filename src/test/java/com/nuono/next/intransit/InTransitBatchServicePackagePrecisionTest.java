package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchCommands.SavePackageCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitBatchServicePackagePrecisionTest {

    @Mock
    private InTransitGoodsMapper mapper;
    @Mock
    private InTransitForwarderService forwarderService;
    @Mock
    private InTransitOperationAuditService auditService;
    private InTransitBatchService service;

    @BeforeEach
    void setUp() {
        service = new InTransitBatchService(mapper, forwarderService, auditService);
        when(mapper.selectBatchById(10002L, 53001L)).thenReturn(batch());
    }

    @Test
    void shouldPreserveHigherPrecisionPackageWeightsForEquivalentChicRounding() {
        SavePackageCommand command = command("11.1", "7.8", "11.1", true);
        when(mapper.selectPackageByBoxNo(10002L, 53001L, "XGGEKSA04085-1"))
                .thenReturn(packageWithWeights("11.065", "7.826", "11.065"));

        service.savePackage(command);

        PackageRow saved = captureSavedPackage();
        assertEquals(new BigDecimal("11.065"), saved.getWeightKg());
        assertEquals(new BigDecimal("7.826"), saved.getVolumeWeightKg());
        assertEquals(new BigDecimal("11.065"), saved.getChargeableWeightKg());
    }

    @Test
    void shouldStillAcceptMaterialChicWeightChangesWhilePreservingEquivalentFields() {
        SavePackageCommand command = command("12.1", "7.8", "12.1", true);
        when(mapper.selectPackageByBoxNo(10002L, 53001L, "XGGEKSA04085-1"))
                .thenReturn(packageWithWeights("11.065", "7.826", "11.065"));

        service.savePackage(command);

        PackageRow saved = captureSavedPackage();
        assertEquals(new BigDecimal("12.1"), saved.getWeightKg());
        assertEquals(new BigDecimal("7.826"), saved.getVolumeWeightKg());
        assertEquals(new BigDecimal("12.1"), saved.getChargeableWeightKg());
    }

    @Test
    void shouldKeepDefaultPackageWeightOverwriteWhenPrecisionGuardIsDisabled() {
        SavePackageCommand command = command("11.1", "7.8", "11.1", false);
        when(mapper.selectPackageByBoxNo(10002L, 53001L, "XGGEKSA04085-1"))
                .thenReturn(packageWithWeights("11.065", "7.826", "11.065"));

        service.savePackage(command);

        PackageRow saved = captureSavedPackage();
        assertEquals(new BigDecimal("11.1"), saved.getWeightKg());
        assertEquals(new BigDecimal("7.8"), saved.getVolumeWeightKg());
        assertEquals(new BigDecimal("11.1"), saved.getChargeableWeightKg());
    }

    private PackageRow captureSavedPackage() {
        ArgumentCaptor<PackageRow> captor = ArgumentCaptor.forClass(PackageRow.class);
        verify(mapper).updatePackage(captor.capture());
        return captor.getValue();
    }

    private SavePackageCommand command(String weight, String volumeWeight, String chargeable, boolean preserve) {
        SavePackageCommand command = new SavePackageCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53001L);
        command.setBoxNo("XGGEKSA04085-1");
        command.setPackageWeightKg(new BigDecimal(weight));
        command.setPackageVolumeWeightKg(new BigDecimal(volumeWeight));
        command.setPackageChargeableWeightKg(new BigDecimal(chargeable));
        command.setPackageSnapshotAuthoritative(true);
        command.setPreserveHigherPrecisionEquivalentWeights(preserve);
        return command;
    }

    private PackageRow packageWithWeights(String weight, String volumeWeight, String chargeable) {
        PackageRow row = new PackageRow();
        row.setId(58001L);
        row.setOwnerUserId(10002L);
        row.setBatchId(53001L);
        row.setBoxNo("XGGEKSA04085-1");
        row.setWeightKg(new BigDecimal(weight));
        row.setVolumeWeightKg(new BigDecimal(volumeWeight));
        row.setChargeableWeightKg(new BigDecimal(chargeable));
        return row;
    }

    private BatchRow batch() {
        BatchRow row = new BatchRow();
        row.setId(53001L);
        row.setOwnerUserId(10002L);
        row.setBatchStatus("warehouse_received");
        row.setRawForwarderName("启客");
        row.setForwarderQualityStatus("forwarder_unmatched");
        return row;
    }
}
