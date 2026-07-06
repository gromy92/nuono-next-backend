package com.nuono.next.replenishmentplan;

import com.nuono.next.infrastructure.mapper.ReplenishmentPlanMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

        IdentityMaps identityMaps = IdentityMaps.from(mapper.selectProductIdentities(ownerUserId, storeCode, siteCode));
        Map<InboundAggregationKey, BigDecimal> remainingByBatch = new LinkedHashMap<>();

        for (InboundLineRow line : inboundLines) {
            String partnerSku = identityMaps.resolve(line);
            if (partnerSku == null) {
                continue;
            }

            BigDecimal remainingQuantity = positiveQuantity(line.getRemainingQuantity());
            if (remainingQuantity.signum() <= 0) {
                continue;
            }

            InboundAggregationKey key = new InboundAggregationKey(
                    partnerSku,
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

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class IdentityMaps {
        private final CodeMap canonicalPartnerSku = new CodeMap();
        private final CodeMap psoPartnerSku = new CodeMap();
        private final CodeMap psoPskuCode = new CodeMap();
        private final CodeMap psoOfferCode = new CodeMap();
        private final CodeMap childSku = new CodeMap();
        private final CodeMap skuParent = new CodeMap();
        private final CodeMap barcode = new CodeMap();

        private static IdentityMaps from(List<ProductIdentityRow> identities) {
            IdentityMaps maps = new IdentityMaps();
            for (ProductIdentityRow identity : identities) {
                String partnerSku = trimToNull(identity.getPartnerSku());
                if (partnerSku == null) {
                    continue;
                }
                maps.canonicalPartnerSku.add(identity.getPartnerSku(), partnerSku);
                maps.psoPartnerSku.add(identity.getPsoPartnerSku(), partnerSku);
                maps.psoPskuCode.add(identity.getPsoPskuCode(), partnerSku);
                maps.psoOfferCode.add(identity.getPsoOfferCode(), partnerSku);
                maps.childSku.add(identity.getChildSku(), partnerSku);
                maps.skuParent.add(identity.getSkuParent(), partnerSku);
                maps.barcode.add(identity.getBarcode(), partnerSku);
            }
            return maps;
        }

        private String resolve(InboundLineRow line) {
            String partnerSku = resolvePsku(line.getPsku());
            if (partnerSku != null) {
                return partnerSku;
            }
            partnerSku = resolveSku(line.getSku());
            if (partnerSku != null) {
                return partnerSku;
            }
            return resolveMsku(line.getMsku());
        }

        private String resolvePsku(String code) {
            return firstMatch(
                    code,
                    canonicalPartnerSku,
                    psoPartnerSku,
                    psoPskuCode,
                    psoOfferCode,
                    childSku,
                    barcode,
                    skuParent
            );
        }

        private String resolveSku(String code) {
            return firstMatch(
                    code,
                    barcode,
                    psoPskuCode,
                    psoOfferCode,
                    canonicalPartnerSku,
                    psoPartnerSku,
                    childSku,
                    skuParent
            );
        }

        private String resolveMsku(String code) {
            return firstMatch(
                    code,
                    psoPskuCode,
                    psoOfferCode,
                    canonicalPartnerSku,
                    psoPartnerSku,
                    childSku,
                    barcode,
                    skuParent
            );
        }

        private String firstMatch(String code, CodeMap... maps) {
            String normalizedCode = normalize(code);
            if (normalizedCode == null) {
                return null;
            }
            for (CodeMap map : maps) {
                String partnerSku = map.lookup(normalizedCode);
                if (partnerSku != null) {
                    return partnerSku;
                }
            }
            return null;
        }
    }

    private static final class CodeMap {
        private final Map<String, String> partnerSkuByCode = new HashMap<>();
        private final Set<String> ambiguousCodes = new HashSet<>();

        private void add(String code, String partnerSku) {
            String normalizedCode = normalize(code);
            String normalizedPartnerSku = normalize(partnerSku);
            String canonicalPartnerSku = trimToNull(partnerSku);
            if (normalizedCode == null || normalizedPartnerSku == null || canonicalPartnerSku == null) {
                return;
            }
            if (ambiguousCodes.contains(normalizedCode)) {
                return;
            }

            String existingPartnerSku = partnerSkuByCode.get(normalizedCode);
            if (existingPartnerSku == null) {
                partnerSkuByCode.put(normalizedCode, canonicalPartnerSku);
                return;
            }
            if (!Objects.equals(normalize(existingPartnerSku), normalizedPartnerSku)) {
                partnerSkuByCode.remove(normalizedCode);
                ambiguousCodes.add(normalizedCode);
            }
        }

        private String lookup(String normalizedCode) {
            if (ambiguousCodes.contains(normalizedCode)) {
                return null;
            }
            return partnerSkuByCode.get(normalizedCode);
        }
    }

    private static final class InboundAggregationKey {
        private final String partnerSku;
        private final Long batchId;
        private final String batchReferenceNo;
        private final String transportMode;
        private final String batchStatus;
        private final LocalDate etaDate;

        private InboundAggregationKey(
                String partnerSku,
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate
        ) {
            this.partnerSku = partnerSku;
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
                    && Objects.equals(batchId, that.batchId)
                    && Objects.equals(batchReferenceNo, that.batchReferenceNo)
                    && Objects.equals(transportMode, that.transportMode)
                    && Objects.equals(batchStatus, that.batchStatus)
                    && Objects.equals(etaDate, that.etaDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(partnerSku, batchId, batchReferenceNo, transportMode, batchStatus, etaDate);
        }
    }
}
