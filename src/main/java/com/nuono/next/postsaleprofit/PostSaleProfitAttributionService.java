package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PostSaleProfitAttributionService {
    private static final int MONEY_SCALE = 6;
    private static final int RATE_SCALE = 8;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public PostSaleProfitAttributionResult attribute(
            List<PostSaleProfitSaleCandidate> sales,
            List<PostSaleProfitBatchCandidate> batches,
            List<PostSaleProfitLockedAttribution> lockedAttributions
    ) {
        List<PostSaleProfitBatchCandidate> orderedBatches = batches.stream()
                .sorted(Comparator
                        .comparing(PostSaleProfitBatchCandidate::partnerSku, Comparator.nullsLast(String::compareTo))
                        .thenComparing(PostSaleProfitBatchCandidate::purchaseBatchTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PostSaleProfitBatchCandidate::sourceId, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
        Map<String, BatchAccumulator> accumulators = new LinkedHashMap<>();
        for (PostSaleProfitBatchCandidate batch : orderedBatches) {
            accumulators.put(batch.sourceId(), new BatchAccumulator(batch));
        }

        List<PostSaleProfitOrderAttributionSlice> slices = new ArrayList<>();
        BigDecimal unassignedQuantity = ZERO;
        for (PostSaleProfitSaleCandidate sale : sales) {
            BigDecimal remainingSaleQuantity = value(sale.quantity());
            for (PostSaleProfitLockedAttribution locked : lockedAttributions) {
                if (!Objects.equals(sale.itemNr(), locked.itemNr())) {
                    continue;
                }
                BatchAccumulator target = accumulators.get(locked.sourceId());
                if (target == null || !Objects.equals(sale.partnerSku(), target.batch.partnerSku())) {
                    continue;
                }
                BigDecimal quantity = min(remainingSaleQuantity, target.remainingQuantity(), value(locked.quantity()));
                if (isPositive(quantity)) {
                    slices.add(target.allocate(sale, quantity, PostSaleProfitAttributionMethod.MANUAL, locked.manualReason()));
                    remainingSaleQuantity = remainingSaleQuantity.subtract(quantity);
                }
                if (!isPositive(remainingSaleQuantity)) {
                    break;
                }
            }

            for (BatchAccumulator target : accumulators.values()) {
                if (!Objects.equals(sale.partnerSku(), target.batch.partnerSku())) {
                    continue;
                }
                BigDecimal quantity = min(remainingSaleQuantity, target.remainingQuantity());
                if (isPositive(quantity)) {
                    PostSaleProfitAttributionMethod method = target.isUnassigned()
                            ? PostSaleProfitAttributionMethod.UNASSIGNED
                            : PostSaleProfitAttributionMethod.FIFO;
                    slices.add(target.allocate(sale, quantity, method, null));
                    remainingSaleQuantity = remainingSaleQuantity.subtract(quantity);
                }
                if (!isPositive(remainingSaleQuantity)) {
                    break;
                }
            }
            unassignedQuantity = unassignedQuantity.add(remainingSaleQuantity.max(ZERO));
        }

        List<PostSaleProfitBatchResult> batchResults = accumulators.values().stream()
                .filter(accumulator -> isPositive(accumulator.soldQuantity))
                .map(BatchAccumulator::toResult)
                .collect(Collectors.toList());
        return new PostSaleProfitAttributionResult(batchResults, List.copyOf(slices), unassignedQuantity);
    }

    private static BigDecimal allocatedMoney(BigDecimal total, BigDecimal allocatedQuantity, BigDecimal totalQuantity) {
        if (total == null || !isPositive(totalQuantity)) {
            return null;
        }
        return total.multiply(allocatedQuantity)
                .divide(totalQuantity, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(ZERO) > 0;
    }

    private static BigDecimal min(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static BigDecimal min(BigDecimal first, BigDecimal second, BigDecimal third) {
        return min(min(first, second), third);
    }

    private static final class BatchAccumulator {
        private final PostSaleProfitBatchCandidate batch;
        private BigDecimal soldQuantity = ZERO;
        private BigDecimal autoQuantity = ZERO;
        private BigDecimal lockedQuantity = ZERO;
        private BigDecimal netProceedsLcy = ZERO;
        private BigDecimal referralFeeLcy = ZERO;
        private BigDecimal fulfillmentFeeLcy = ZERO;
        private BigDecimal otherFeeNetLcy = ZERO;
        private String currency;

        private BatchAccumulator(PostSaleProfitBatchCandidate batch) {
            this.batch = batch;
        }

        private boolean isUnassigned() {
            return PostSaleProfitSourceIds.isUnassigned(batch.sourceId());
        }

        private BigDecimal remainingQuantity() {
            return value(batch.availableQuantity()).subtract(soldQuantity).max(ZERO);
        }

        private PostSaleProfitOrderAttributionSlice allocate(
                PostSaleProfitSaleCandidate sale,
                BigDecimal quantity,
                PostSaleProfitAttributionMethod method,
                String manualReason
        ) {
            BigDecimal net = allocatedMoney(sale.netProceedsLcy(), quantity, sale.quantity());
            BigDecimal referral = allocatedMoney(sale.referralFeeLcy(), quantity, sale.quantity());
            BigDecimal fulfillment = allocatedMoney(sale.fulfillmentFeeLcy(), quantity, sale.quantity());
            BigDecimal other = allocatedMoney(sale.otherFeeNetLcy(), quantity, sale.quantity());

            soldQuantity = soldQuantity.add(quantity);
            if (method == PostSaleProfitAttributionMethod.MANUAL) {
                lockedQuantity = lockedQuantity.add(quantity);
            } else if (method == PostSaleProfitAttributionMethod.FIFO) {
                autoQuantity = autoQuantity.add(quantity);
            }
            netProceedsLcy = netProceedsLcy.add(value(net));
            referralFeeLcy = referralFeeLcy.add(value(referral));
            fulfillmentFeeLcy = fulfillmentFeeLcy.add(value(fulfillment));
            otherFeeNetLcy = otherFeeNetLcy.add(value(other));
            currency = sale.currency();

            return new PostSaleProfitOrderAttributionSlice(
                    sale.itemNr(),
                    sale.orderNr(),
                    batch.sourceId(),
                    sale.partnerSku(),
                    quantity,
                    net,
                    referral,
                    fulfillment,
                    other,
                    sale.currency(),
                    sale.orderTime(),
                    method,
                    method == PostSaleProfitAttributionMethod.MANUAL ? manualReason : null
            );
        }

        private PostSaleProfitBatchResult toResult() {
            BigDecimal purchaseCost = batch.purchaseUnitCostCny() == null
                    ? null
                    : scaled(batch.purchaseUnitCostCny().multiply(soldQuantity));
            BigDecimal headhaulCost = batch.headhaulUnitCostCny() == null
                    ? null
                    : scaled(batch.headhaulUnitCostCny().multiply(soldQuantity));
            List<PostSaleProfitQualityStatus> statuses = qualityStatuses();
            BigDecimal profit = calculateProfit(purchaseCost, headhaulCost);
            BigDecimal profitRate = profit == null || !isPositive(netProceedsLcy) || batch.fxRateToCny() == null
                    ? null
                    : profit.divide(netProceedsLcy.multiply(batch.fxRateToCny()), RATE_SCALE, RoundingMode.HALF_UP);
            return new PostSaleProfitBatchResult(
                    batch.sourceId(),
                    batch.partnerSku(),
                    batch.skuParent(),
                    batch.productTitle(),
                    batch.productImageUrl(),
                    batch.purchaseBatchTime(),
                    isUnassigned() ? null : batch.availableQuantity(),
                    soldQuantity,
                    autoQuantity,
                    lockedQuantity,
                    batch.purchaseUnitCostCny(),
                    purchaseCost,
                    batch.headhaulUnitCostCny(),
                    headhaulCost,
                    scaled(netProceedsLcy),
                    scaled(referralFeeLcy),
                    scaled(fulfillmentFeeLcy),
                    scaled(otherFeeNetLcy),
                    currency,
                    batch.fxRateToCny(),
                    profit,
                    profitRate,
                    statuses,
                    batch.evidenceJson()
            );
        }

        private List<PostSaleProfitQualityStatus> qualityStatuses() {
            List<PostSaleProfitQualityStatus> statuses = new ArrayList<>();
            if (batch.purchaseUnitCostCny() == null) {
                statuses.add(PostSaleProfitQualityStatus.MISSING_PURCHASE_COST);
            }
            if (batch.headhaulUnitCostCny() == null) {
                statuses.add(PostSaleProfitQualityStatus.MISSING_HEADHAUL);
            } else if (batch.estimatedHeadhaul()) {
                statuses.add(PostSaleProfitQualityStatus.ESTIMATED_HEADHAUL);
            }
            if (batch.fxRateToCny() == null) {
                statuses.add(PostSaleProfitQualityStatus.MISSING_FX_RATE);
            }
            if (batch.purchaseSourceReview()) {
                statuses.add(PostSaleProfitQualityStatus.PURCHASE_SOURCE_REVIEW);
            }
            if (isPositive(lockedQuantity)) {
                statuses.add(PostSaleProfitQualityStatus.MANUAL_LOCKED);
            }
            if (isUnassigned()) {
                statuses.add(PostSaleProfitQualityStatus.UNASSIGNED_ORDER_QUANTITY);
            }
            if (statuses.isEmpty()) {
                statuses.add(PostSaleProfitQualityStatus.OK);
            }
            return List.copyOf(statuses);
        }

        private BigDecimal calculateProfit(BigDecimal purchaseCost, BigDecimal headhaulCost) {
            if (purchaseCost == null || headhaulCost == null || batch.fxRateToCny() == null) {
                return null;
            }
            BigDecimal revenueCny = netProceedsLcy.multiply(batch.fxRateToCny());
            return scaled(revenueCny.subtract(purchaseCost).subtract(headhaulCost));
        }
    }
}
