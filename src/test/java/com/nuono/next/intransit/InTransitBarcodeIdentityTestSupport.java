package com.nuono.next.intransit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchRecords.BatchAggregateRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import java.math.BigDecimal;
import java.time.LocalDate;

final class InTransitBarcodeIdentityTestSupport {

    private InTransitBarcodeIdentityTestSupport() {
    }

    static void stubDefaultProductIdentityLookup(InTransitGoodsMapper mapper) {
        lenient().when(mapper.selectProductIdentityByBarcode(anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    String barcode = invocation.getArgument(1);
                    String partnerSku;
                    if ("SOURCE-SKU-ONLY".equals(barcode)) {
                        partnerSku = "PSKU-DERIVED";
                    } else if ("SKU-AE-001".equals(barcode)) {
                        partnerSku = "PSKU-001";
                    } else if ("SKU-AE-002".equals(barcode)) {
                        partnerSku = "PSKU-002";
                    } else {
                        partnerSku = barcode.startsWith("SKU-") ? "P" + barcode : barcode;
                    }
                    return new BarcodeProductIdentity(50001L, partnerSku);
                });
    }

    static BatchRow batch(Long id, String status, String rawForwarderName, String qualityStatus) {
        BatchRow row = new BatchRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setStandardForwarderId("forwarder_matched".equals(qualityStatus) ? 51001L : null);
        row.setStandardForwarderCode("forwarder_matched".equals(qualityStatus) ? "YITE" : null);
        row.setStandardForwarderName("forwarder_matched".equals(qualityStatus) ? "义特" : null);
        row.setRawForwarderName(rawForwarderName);
        row.setNormalizedRawForwarderName(rawForwarderName == null ? null : rawForwarderName.toLowerCase());
        row.setForwarderQualityStatus(qualityStatus);
        row.setTransportMode("AIR");
        row.setBatchStatus(status);
        row.setTargetStoreCode("DB");
        row.setTargetSiteCode("AE");
        row.setTargetWarehouseName("FBN-DXB");
        row.setDepartureDate(LocalDate.parse("2026-05-20"));
        row.setEtaDate(LocalDate.parse("2026-06-08"));
        row.setTrackingNo("TRK-001");
        row.setContainerNo("CONT-001");
        row.setBatchReferenceNo("BATCH-001");
        row.setMissingFieldsJson("[\"transportMode\",\"targetStoreCode\",\"targetWarehouseName\"]");
        return row;
    }

    static LineRow line(
            Long id,
            Long batchId,
            String sku,
            Integer shippedQuantity,
            Integer receivedQuantity,
            Integer remainingQuantity
    ) {
        LineRow row = new LineRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setBatchId(batchId);
        row.setSku(sku);
        row.setMsku("MSKU-" + sku);
        row.setPsku("PSKU-" + sku);
        row.setProductName("折叠手机壳");
        row.setStoreCode("STR245027-NAE");
        row.setSiteCode("AE");
        row.setShippedQuantity(shippedQuantity);
        row.setReceivedQuantity(receivedQuantity);
        row.setRemainingQuantity(remainingQuantity);
        row.setCartonCount(2);
        row.setUnitsPerCarton(5);
        row.setCartonWeightKg(new BigDecimal("12.500000"));
        row.setCartonVolumeCbm(new BigDecimal("0.250000"));
        return row;
    }

    static BatchAggregateRow aggregate(
            Integer skuCount,
            Integer boxCount,
            Integer shippedQuantityTotal,
            Integer receivedQuantityTotal,
            Integer remainingQuantityTotal,
            Integer cartonCountTotal,
            BigDecimal totalWeightKg,
            BigDecimal totalVolumeCbm
    ) {
        BatchAggregateRow row = new BatchAggregateRow();
        row.setSkuCount(skuCount);
        row.setBoxCount(boxCount);
        row.setShippedQuantityTotal(shippedQuantityTotal);
        row.setReceivedQuantityTotal(receivedQuantityTotal);
        row.setRemainingQuantityTotal(remainingQuantityTotal);
        row.setCartonCountTotal(cartonCountTotal);
        row.setTotalWeightKg(totalWeightKg);
        row.setTotalVolumeCbm(totalVolumeCbm);
        return row;
    }
}
