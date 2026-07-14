package com.nuono.next.postsaleprofit;

import com.nuono.next.infrastructure.mapper.PostSaleProfitMapper;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.AttributionTotalsRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.AttributionWriteRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.BatchMoveRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.BatchStatusRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.TransferAttributionRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostSaleProfitAttributionAdjustmentService {
    private static final String MANUAL_LOCKED = "MANUAL_LOCKED";
    private static final String OK = "OK";
    private static final int MONEY_SCALE = 6;
    private static final int RATE_SCALE = 8;

    private final PostSaleProfitMapper mapper;

    public PostSaleProfitAttributionAdjustmentService(PostSaleProfitMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public PostSaleProfitAttributionAdjustmentView setBatchLock(PostSaleProfitBatchLockCommand command) {
        BatchStatusRow status = mapper.selectBatchStatus(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getBatchId()
        );
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post-sale profit batch does not exist.");
        }

        List<String> qualityStatuses = parseQualityStatusJson(status.qualityStatusJson);
        BigDecimal soldQuantity = value(status.soldQuantity);
        BigDecimal lockedQuantity;
        BigDecimal autoQuantity;
        String attributionMethod;
        String manualReason;
        if (command.isLocked()) {
            qualityStatuses.remove(OK);
            if (!qualityStatuses.contains(MANUAL_LOCKED)) {
                qualityStatuses.add(MANUAL_LOCKED);
            }
            lockedQuantity = soldQuantity;
            autoQuantity = BigDecimal.ZERO;
            attributionMethod = PostSaleProfitAttributionMethod.MANUAL.name();
            manualReason = command.getReason();
        } else {
            qualityStatuses.remove(MANUAL_LOCKED);
            if (qualityStatuses.isEmpty()) {
                qualityStatuses.add(OK);
            }
            lockedQuantity = BigDecimal.ZERO;
            autoQuantity = soldQuantity;
            attributionMethod = PostSaleProfitAttributionMethod.FIFO.name();
            manualReason = null;
        }

        mapper.updateBatchAttributionLockState(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getBatchId(),
                command.isLocked(),
                attributionMethod,
                manualReason
        );
        mapper.updateBatchLockSummary(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getBatchId(),
                lockedQuantity,
                autoQuantity,
                qualityStatusJson(qualityStatuses)
        );
        return new PostSaleProfitAttributionAdjustmentView(command.getBatchId(), qualityStatuses);
    }

    @Transactional
    public PostSaleProfitAttributionMoveView moveAttribution(PostSaleProfitAttributionMoveCommand command) {
        validateMoveCommand(command);
        BatchMoveRow source = requiredBatch(command, command.getSourceBatchId());
        BatchMoveRow target = requiredBatch(command, command.getTargetBatchId());
        if (!Objects.equals(source.runId, target.runId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能在同一次利润预览内移动归属。");
        }
        if (!Objects.equals(source.partnerSku, target.partnerSku)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能在同一个 SKU 的批次之间移动归属。");
        }

        BigDecimal remaining = command.getQuantity();
        for (TransferAttributionRow row : mapper.listTransferableAttributions(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getSourceBatchId()
        )) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = value(row.attributedQuantity);
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal movedQuantity = available.min(remaining);
            moveFromAttribution(command, target, row, movedQuantity);
            remaining = remaining.subtract(movedQuantity);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "来源批次可移动数量不足。");
        }

        refreshBatchSummary(command, source);
        refreshBatchSummary(command, target);
        return new PostSaleProfitAttributionMoveView(
                command.getSourceBatchId(),
                command.getTargetBatchId(),
                command.getQuantity()
        );
    }

    private void validateMoveCommand(PostSaleProfitAttributionMoveCommand command) {
        if (command.getSourceBatchId() == null || command.getTargetBatchId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceBatchId and targetBatchId are required.");
        }
        if (Objects.equals(command.getSourceBatchId(), command.getTargetBatchId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "来源批次和目标批次不能相同。");
        }
        if (command.getQuantity() == null || command.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "移动数量必须大于 0。");
        }
        if (command.getReason() == null || command.getReason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "移动原因必填。");
        }
    }

    private BatchMoveRow requiredBatch(PostSaleProfitAttributionMoveCommand command, Long batchId) {
        BatchMoveRow row = mapper.selectBatchForMove(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                batchId
        );
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "利润批次不存在。");
        }
        return row;
    }

    private void moveFromAttribution(
            PostSaleProfitAttributionMoveCommand command,
            BatchMoveRow target,
            TransferAttributionRow source,
            BigDecimal movedQuantity
    ) {
        BigDecimal originalQuantity = value(source.attributedQuantity);
        BigDecimal remainingQuantity = originalQuantity.subtract(movedQuantity);
        BigDecimal movedNet = prorate(source.netProceedsLcy, movedQuantity, originalQuantity);
        BigDecimal movedReferral = prorate(source.referralFeeLcy, movedQuantity, originalQuantity);
        BigDecimal movedFulfillment = prorate(source.fulfillmentFeeLcy, movedQuantity, originalQuantity);
        BigDecimal movedOther = prorate(source.otherFeeNetLcy, movedQuantity, originalQuantity);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            mapper.softDeleteAttributionSlice(
                    command.getOwnerUserId(),
                    command.getStoreCode(),
                    command.getSiteCode(),
                    source.id
            );
        } else {
            mapper.updateAttributionSlice(
                    command.getOwnerUserId(),
                    command.getStoreCode(),
                    command.getSiteCode(),
                    source.id,
                    scaleMoney(remainingQuantity),
                    subtractMoney(source.netProceedsLcy, movedNet),
                    subtractMoney(source.referralFeeLcy, movedReferral),
                    subtractMoney(source.fulfillmentFeeLcy, movedFulfillment),
                    subtractMoney(source.otherFeeNetLcy, movedOther)
            );
        }

        AttributionWriteRow moved = new AttributionWriteRow();
        moved.runId = target.runId;
        moved.batchId = target.id;
        moved.ownerUserId = command.getOwnerUserId();
        moved.storeCode = command.getStoreCode();
        moved.siteCode = command.getSiteCode();
        moved.financeItemNr = source.financeItemNr;
        moved.orderNr = source.orderNr;
        moved.itemNr = source.itemNr;
        moved.orderTime = source.orderTime;
        moved.partnerSku = source.partnerSku;
        moved.sku = source.sku;
        moved.attributedQuantity = scaleMoney(movedQuantity);
        moved.attributionMethod = PostSaleProfitAttributionMethod.MANUAL.name();
        moved.locked = true;
        moved.manualReason = command.getReason();
        moved.netProceedsLcy = movedNet;
        moved.referralFeeLcy = movedReferral;
        moved.fulfillmentFeeLcy = movedFulfillment;
        moved.otherFeeNetLcy = movedOther;
        moved.currency = source.currency;
        moved.qualityStatusJson = "[\"MANUAL_LOCKED\"]";
        mapper.insertOrderAttribution(moved);
    }

    private void refreshBatchSummary(PostSaleProfitAttributionMoveCommand command, BatchMoveRow batch) {
        AttributionTotalsRow totals = mapper.selectBatchAttributionTotals(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                batch.id
        );
        if (totals == null) {
            totals = new AttributionTotalsRow();
        }
        BigDecimal soldQuantity = value(totals.soldQuantity);
        BigDecimal purchaseCost = batch.purchaseUnitCostCny == null ? null : scaleMoney(batch.purchaseUnitCostCny.multiply(soldQuantity));
        BigDecimal headhaulCost = batch.headhaulUnitCostCny == null ? null : scaleMoney(batch.headhaulUnitCostCny.multiply(soldQuantity));
        BigDecimal profit = calculateProfit(value(totals.netProceedsLcy), batch.fxRateToCny, purchaseCost, headhaulCost);
        BigDecimal profitRate = calculateProfitRate(profit, value(totals.netProceedsLcy), batch.fxRateToCny);
        List<String> statuses = parseQualityStatusJson(batch.qualityStatusJson);
        boolean hasLocked = value(totals.lockedQuantity).compareTo(BigDecimal.ZERO) > 0;
        if (hasLocked && !statuses.contains(MANUAL_LOCKED)) {
            statuses.remove(OK);
            statuses.add(MANUAL_LOCKED);
        }
        if (!hasLocked) {
            statuses.remove(MANUAL_LOCKED);
            if (statuses.isEmpty()) {
                statuses.add(OK);
            }
        }
        mapper.updateBatchFinancialSummary(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                batch.id,
                totals.soldQuantity,
                totals.autoQuantity,
                totals.lockedQuantity,
                totals.netProceedsLcy,
                totals.referralFeeLcy,
                totals.fulfillmentFeeLcy,
                totals.otherFeeNetLcy,
                totals.currency,
                purchaseCost,
                headhaulCost,
                profit,
                profitRate,
                qualityStatusJson(statuses)
        );
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal prorate(BigDecimal amount, BigDecimal quantity, BigDecimal totalQuantity) {
        if (amount == null || totalQuantity == null || totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return scaleMoney(amount.multiply(quantity).divide(totalQuantity, MONEY_SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal subtractMoney(BigDecimal amount, BigDecimal moved) {
        if (amount == null) {
            return null;
        }
        return scaleMoney(amount.subtract(value(moved)));
    }

    private BigDecimal calculateProfit(BigDecimal netProceedsLcy, BigDecimal fxRate, BigDecimal purchaseCost, BigDecimal headhaulCost) {
        if (fxRate == null || purchaseCost == null || headhaulCost == null) {
            return null;
        }
        return scaleMoney(netProceedsLcy.multiply(fxRate).subtract(purchaseCost).subtract(headhaulCost));
    }

    private BigDecimal calculateProfitRate(BigDecimal profit, BigDecimal netProceedsLcy, BigDecimal fxRate) {
        if (profit == null || fxRate == null || netProceedsLcy == null || netProceedsLcy.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return profit.divide(netProceedsLcy.multiply(fxRate), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private List<String> parseQualityStatusJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        String normalized = raw.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        List<String> statuses = new ArrayList<>();
        if (normalized.isBlank()) {
            return statuses;
        }
        for (String token : normalized.split(",")) {
            String status = token.trim();
            if (status.startsWith("\"") && status.endsWith("\"") && status.length() >= 2) {
                status = status.substring(1, status.length() - 1);
            }
            if (!status.isBlank() && !statuses.contains(status)) {
                statuses.add(status);
            }
        }
        return statuses;
    }

    private String qualityStatusJson(List<String> statuses) {
        return statuses.stream()
                .map(status -> "\"" + status + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
