package com.nuono.next.productpublicdetail;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailRequest;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductPublicDetailSingleFetchService {
    private final ProductPublicDetailSyncService syncSupport;

    public ProductPublicDetailSingleFetchService(ProductPublicDetailSyncService syncSupport) {
        this.syncSupport = syncSupport;
    }

    public ProductPublicDetailFetchResult fetch(
            ProductPublicDetailCandidate candidate,
            Long actorUserId,
            Long taskId
    ) {
        if (candidate == null) {
            return failed("未找到可用于 Noon 前台详情拉取的商品身份。");
        }
        String storeCode = syncSupport.normalizeStore(candidate.getStoreCode());
        String siteCode = syncSupport.normalizeSite(candidate.getSiteCode());
        Optional<NoonRiskBackoffHold> activeHold =
                syncSupport.currentRiskBackoffHold(candidate.getOwnerUserId(), storeCode, siteCode);
        if (activeHold.isPresent()) {
            return failed("Noon 前台详情正在风险退避，冷却至 " + activeHold.get().getBlockedUntil() + "。");
        }
        NoonPublicProductDetailAdapter adapter = syncSupport.adapter();
        if (adapter == null) {
            return failed("Noon 前台公开详情 adapter 不可用。");
        }
        String code = NoonProductCodeSupport.normalize(candidate.getNoonProductCode());
        if (!StringUtils.hasText(code) || NoonProductCodeSupport.codeType(code).isEmpty()) {
            return failed("商品缺少可在 Noon 前台搜索的 Z 编码。");
        }
        try {
            NoonPublicProductDetailResult result = adapter.fetch(NoonPublicProductDetailRequest.builder()
                    .siteCode(siteCode)
                    .locale(syncSupport.defaultLocale(siteCode))
                    .noonProductCode(code)
                    .build());
            if (result == null) {
                result = syncSupport.failureResult(
                        code,
                        "PROVIDER_EMPTY_RESPONSE",
                        "Noon 前台公开详情 adapter 返回空结果。",
                        null,
                        null,
                        null,
                        null
                );
            }
            return persist(candidate, actorUserId, taskId, storeCode, siteCode, result);
        } catch (Exception exception) {
            return persist(
                    candidate,
                    actorUserId,
                    taskId,
                    storeCode,
                    siteCode,
                    syncSupport.failureResult(
                            code,
                            "PROVIDER_EXCEPTION",
                            syncSupport.shrink(exception.getMessage(), 300),
                            null,
                            null,
                            null,
                            null
                    )
            );
        }
    }

    private ProductPublicDetailFetchResult persist(
            ProductPublicDetailCandidate candidate,
            Long actorUserId,
            Long taskId,
            String storeCode,
            String siteCode,
            NoonPublicProductDetailResult result
    ) {
        ProductPublicDetailSnapshot snapshot = syncSupport.toSnapshot(candidate, result, actorUserId);
        syncSupport.upsertSnapshot(snapshot);
        syncSupport.recordRiskBackoffIfNeeded(
                taskId,
                candidate.getOwnerUserId(),
                storeCode,
                siteCode,
                snapshot
        );
        return ProductPublicDetailFetchResult.of(
                snapshot.getSyncStatus(),
                syncSupport.firstText(snapshot.getFailureMessage(), "Noon 前台详情拉取完成。")
        );
    }

    private ProductPublicDetailFetchResult failed(String message) {
        return ProductPublicDetailFetchResult.of(ProductPublicDetailSyncStatus.FAILED, message);
    }
}
