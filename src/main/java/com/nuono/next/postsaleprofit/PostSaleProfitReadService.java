package com.nuono.next.postsaleprofit;

import com.nuono.next.infrastructure.mapper.PostSaleProfitMapper;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.AttributionReadRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.BatchReadRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.RecalculationRunRow;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PostSaleProfitReadService {
    private final PostSaleProfitMapper mapper;

    public PostSaleProfitReadService(PostSaleProfitMapper mapper) {
        this.mapper = mapper;
    }

    public PostSaleProfitLatestRunView latestRun(Long ownerUserId, String storeCode, String siteCode) {
        RecalculationRunRow row = mapper.selectLatestRun(ownerUserId, storeCode, siteCode);
        return PostSaleProfitLatestRunView.from(row);
    }

    public PostSaleProfitBatchListView listBatches(PostSaleProfitBatchQuery query) {
        Long runId = mapper.selectLatestRunId(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                query.getDateFrom(),
                query.getDateTo()
        );
        if (runId == null) {
            return new PostSaleProfitBatchListView(List.of(), 0, true);
        }
        int total = mapper.countBatchRows(runId, query);
        int offset = Math.max(0, (query.getPage() - 1) * query.getPageSize());
        List<PostSaleProfitBatchRowView> rows = new ArrayList<>();
        for (BatchReadRow row : mapper.listBatchRows(runId, query, offset, query.getPageSize())) {
            rows.add(toView(row));
        }
        return new PostSaleProfitBatchListView(rows, total, false);
    }

    public PostSaleProfitBatchDetailView getBatchDetail(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long batchId
    ) {
        List<PostSaleProfitOrderLineView> orderLines = new ArrayList<>();
        for (AttributionReadRow row : mapper.listOrderAttributions(ownerUserId, storeCode, siteCode, batchId)) {
            orderLines.add(toOrderLineView(row));
        }
        return new PostSaleProfitBatchDetailView(batchId, orderLines);
    }

    private PostSaleProfitBatchRowView toView(BatchReadRow row) {
        PostSaleProfitBatchRowView view = new PostSaleProfitBatchRowView();
        view.setBatchId(row.id);
        view.setSourceId(row.sourceId);
        view.setSkuParent(row.skuParent);
        view.setPartnerSku(row.partnerSku);
        view.setProductTitle(row.productTitle);
        view.setProductImageUrl(row.productImageUrl);
        view.setPurchaseBatchTime(row.purchaseBatchTime);
        view.setPurchaseQuantity(row.purchaseQuantity);
        view.setPurchaseUnitCostCny(row.purchaseUnitCostCny);
        view.setPurchaseCostCny(row.purchaseCostCny);
        view.setShippingSourceType(row.shippingSourceType);
        view.setShippingSourceId(row.shippingSourceId);
        view.setShippingBatchNo(row.shippingBatchNo);
        view.setInTransitBatchId(row.inTransitBatchId);
        view.setInTransitReferenceNo(row.inTransitReferenceNo);
        view.setAvailableAt(row.availableAt);
        view.setAvailableAtSource(row.availableAtSource);
        view.setHeadhaulCostSourceType(row.headhaulCostSourceType);
        view.setHeadhaulUnitCostCny(row.headhaulUnitCostCny);
        view.setHeadhaulCostCny(row.headhaulCostCny);
        view.setSoldQuantity(row.soldQuantity);
        view.setAutoQuantity(row.autoQuantity);
        view.setLockedQuantity(row.lockedQuantity);
        view.setNetProceedsLcy(row.netProceedsLcy);
        view.setReferralFeeLcy(row.referralFeeLcy);
        view.setFulfillmentFeeLcy(row.fulfillmentFeeLcy);
        view.setOtherFeeNetLcy(row.otherFeeNetLcy);
        view.setAverageSalePriceLcy(row.averageSalePriceLcy);
        view.setGmvLcy(row.gmvLcy);
        view.setSalePriceFactCount(row.salePriceFactCount);
        view.setCurrency(row.currency);
        view.setFxRateToCny(row.fxRateToCny);
        view.setProfitCny(row.profitCny);
        view.setProfitRate(row.profitRate);
        view.setQualityStatuses(parseQualityStatusJson(row.qualityStatusJson));
        view.setEvidenceJson(row.evidenceJson);
        return view;
    }

    private PostSaleProfitOrderLineView toOrderLineView(AttributionReadRow row) {
        PostSaleProfitOrderLineView view = new PostSaleProfitOrderLineView();
        view.setId(row.id == null ? null : String.valueOf(row.id));
        view.setOrderNo(row.orderNr);
        view.setItemNr(row.itemNr);
        view.setOrderTime(row.orderTime == null ? null : row.orderTime.toString());
        view.setPartnerSku(row.partnerSku);
        view.setSku(row.sku);
        view.setAttributedQuantity(row.attributedQuantity);
        view.setAttributionMethod(row.attributionMethod);
        view.setLocked(row.locked);
        view.setManualReason(row.manualReason);
        view.setNetProceedsLcy(row.netProceedsLcy);
        view.setReferralFeeLcy(row.referralFeeLcy);
        view.setFulfillmentFeeLcy(row.fulfillmentFeeLcy);
        view.setOtherFeeNetLcy(row.otherFeeNetLcy);
        view.setCurrency(row.currency);
        return view;
    }

    private List<String> parseQualityStatusJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> statuses = new ArrayList<>();
        for (String token : normalized.split(",")) {
            String status = token.trim();
            if (status.startsWith("\"") && status.endsWith("\"") && status.length() >= 2) {
                status = status.substring(1, status.length() - 1);
            }
            if (!status.isBlank()) {
                statuses.add(status);
            }
        }
        return statuses;
    }
}
