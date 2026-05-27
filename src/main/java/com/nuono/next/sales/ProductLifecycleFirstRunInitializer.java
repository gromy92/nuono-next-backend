package com.nuono.next.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class ProductLifecycleFirstRunInitializer {

    public ProductLifecycleCurrentState initialize(
            ProductLifecycleStateQuery query,
            ProductLifecycleListingDateResolution listingResolution,
            LocalDate analysisDate,
            Long jobId
    ) {
        boolean newCandidate = listingResolution.isEligibleForNewInitialization();
        String lifecycleCode = newCandidate ? "new" : "data_insufficient";
        String lifecycleLabel = newCandidate ? "新品" : "数据不足";
        String qualityState = qualityState(listingResolution);
        String explanation = explanation(listingResolution);
        return new ProductLifecycleCurrentState(
                null,
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                query.getPartnerSku(),
                query.getSku(),
                lifecycleCode,
                lifecycleLabel,
                "DEFAULT_V1",
                analysisDate,
                listingResolution.getListingDate(),
                listingResolution.getSource(),
                qualityState,
                explanation,
                listingResolution.getEvidenceJson(),
                jobId,
                LocalDateTime.now()
        );
    }

    private String qualityState(ProductLifecycleListingDateResolution listingResolution) {
        if (listingResolution.isHistoricalOldProduct()) {
            return "historical_old_product";
        }
        if (listingResolution.isEligibleForNewInitialization() && "low".equals(listingResolution.getConfidence())) {
            return "low_confidence";
        }
        if (listingResolution.isEligibleForNewInitialization()) {
            return "ready";
        }
        return "data_insufficient";
    }

    private String explanation(ProductLifecycleListingDateResolution listingResolution) {
        if (listingResolution.isHistoricalOldProduct()) {
            return "商品已有 60 天历史销量/PV/库存信号，首次计算不按新品初始化。";
        }
        if (listingResolution.isEligibleForNewInitialization()) {
            return "商品上架时间位于新品窗口内，可作为首次生命周期新品候选。";
        }
        return "商品首次计算需要等待 DEFAULT_V1 完整分类规则判定。";
    }
}
