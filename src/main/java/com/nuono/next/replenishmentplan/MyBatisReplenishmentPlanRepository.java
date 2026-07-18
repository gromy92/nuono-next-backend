package com.nuono.next.replenishmentplan;

import com.nuono.next.infrastructure.mapper.ReplenishmentPlanMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisReplenishmentPlanRepository implements ReplenishmentPlanRepository {

    private final ReplenishmentPlanMapper mapper;

    public MyBatisReplenishmentPlanRepository(ReplenishmentPlanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<StockRow> listFbnSupermallStock(Long ownerUserId, String storeCode, String siteCode) {
        return mapper.selectFbnSupermallStock(ownerUserId, storeCode, siteCode);
    }

    @Override
    public List<InboundRow> listActiveInbound(Long ownerUserId, String storeCode, String siteCode) {
        List<InboundLineRow> inboundLines = mapper.selectActiveInboundLines(ownerUserId, storeCode, siteCode);
        if (inboundLines.isEmpty()) {
            return List.of();
        }

        Map<InboundAggregationKey, BigDecimal> remainingByBatch = new LinkedHashMap<>();

        for (InboundLineRow line : inboundLines) {
            String partnerSku = trimToNull(line.getPartnerSku());
            if (partnerSku == null) {
                continue;
            }

            BigDecimal remainingQuantity = positiveQuantity(line.getRemainingQuantity());
            if (remainingQuantity.signum() <= 0) {
                continue;
            }

            InboundAggregationKey key = new InboundAggregationKey(
                    partnerSku,
                    trimToNull(line.getDestinationCode()),
                    "MATCHED".equals(line.getScopeStatus()),
                    line.getBatchId(),
                    line.getBatchReferenceNo(),
                    line.getTransportMode(),
                    line.getBatchStatus(),
                    line.getEtaDate()
            );
            remainingByBatch.merge(key, remainingQuantity, BigDecimal::add);
        }

        List<InboundRow> rows = new ArrayList<>(remainingByBatch.size());
        for (Map.Entry<InboundAggregationKey, BigDecimal> entry : remainingByBatch.entrySet()) {
            InboundAggregationKey key = entry.getKey();
            rows.add(new InboundRow(
                    key.partnerSku,
                    key.destinationCode,
                    key.scopeResolved,
                    key.batchId,
                    key.batchReferenceNo,
                    key.transportMode,
                    key.batchStatus,
                    key.etaDate,
                    entry.getValue()
            ));
        }
        return rows;
    }

    private static BigDecimal positiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return quantity;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class InboundAggregationKey {
        private final String partnerSku;
        private final String destinationCode;
        private final boolean scopeResolved;
        private final Long batchId;
        private final String batchReferenceNo;
        private final String transportMode;
        private final String batchStatus;
        private final LocalDate etaDate;

        private InboundAggregationKey(
                String partnerSku,
                String destinationCode,
                boolean scopeResolved,
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate
        ) {
            this.partnerSku = partnerSku;
            this.destinationCode = destinationCode;
            this.scopeResolved = scopeResolved;
            this.batchId = batchId;
            this.batchReferenceNo = batchReferenceNo;
            this.transportMode = transportMode;
            this.batchStatus = batchStatus;
            this.etaDate = etaDate;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof InboundAggregationKey)) {
                return false;
            }
            InboundAggregationKey that = (InboundAggregationKey) other;
            return Objects.equals(partnerSku, that.partnerSku)
                    && Objects.equals(destinationCode, that.destinationCode)
                    && scopeResolved == that.scopeResolved
                    && Objects.equals(batchId, that.batchId)
                    && Objects.equals(batchReferenceNo, that.batchReferenceNo)
                    && Objects.equals(transportMode, that.transportMode)
                    && Objects.equals(batchStatus, that.batchStatus)
                    && Objects.equals(etaDate, that.etaDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    partnerSku,
                    destinationCode,
                    scopeResolved,
                    batchId,
                    batchReferenceNo,
                    transportMode,
                    batchStatus,
                    etaDate
            );
        }
    }
}
