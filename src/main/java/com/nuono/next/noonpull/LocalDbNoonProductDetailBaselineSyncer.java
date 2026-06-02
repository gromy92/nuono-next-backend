package com.nuono.next.noonpull;

import com.nuono.next.product.LocalDbProductMasterService;
import com.nuono.next.product.ProductListSummaryView;
import com.nuono.next.product.ProductMasterFetchCommand;
import com.nuono.next.product.ProductMasterSnapshotView;
import com.nuono.next.product.ProductProjectionPersistenceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbNoonProductDetailBaselineSyncer implements NoonProductDetailBaselineSyncer {
    private static final int DEFAULT_MAX_DETAIL_FETCHES = 10;

    private final ProductProjectionPersistenceService projectionPersistenceService;
    private final LocalDbProductMasterService productMasterService;

    public LocalDbNoonProductDetailBaselineSyncer(
            ProductProjectionPersistenceService projectionPersistenceService,
            LocalDbProductMasterService productMasterService
    ) {
        this.projectionPersistenceService = projectionPersistenceService;
        this.productMasterService = productMasterService;
    }

    @Override
    public NoonProductDetailBaselineSyncResult sync(NoonProductDetailBaselineSyncRequest request) {
        NoonProductDetailBaselineSyncResult result = new NoonProductDetailBaselineSyncResult();
        Long ownerUserId = request == null ? null : request.getOwnerUserId();
        String storeCode = request == null ? null : request.getStoreCode();
        String siteCode = request == null ? null : request.getSiteCode();
        List<String> warnings = new ArrayList<>();
        List<ProductListSummaryView> summaries =
                projectionPersistenceService.loadProductListSummaries(ownerUserId, storeCode, warnings);
        if (summaries.isEmpty()) {
            result.setFailureMessage("product list baseline missing: no products available for detail sync");
            result.setDiagnosticSummary("product detail baseline sync skipped; productCount=0");
            return result;
        }

        int batchLimit = resolveBatchLimit(request);
        List<ProductListSummaryView> missingSummaries = rotateByResumePosition(
                missingSummaries(summaries),
                request == null ? null : request.getResumePosition()
        );
        int skippedReady = summaries.size() - missingSummaries.size();
        int fetchLimit = Math.min(batchLimit, missingSummaries.size());
        List<String> succeededSkuParents = new ArrayList<>();
        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        String firstFailure = null;
        for (int index = 0; index < fetchLimit; index++) {
            ProductListSummaryView summary = missingSummaries.get(index);
            attempted++;
            try {
                ProductMasterSnapshotView snapshot = productMasterService.fetchSnapshot(command(ownerUserId, storeCode, summary));
                if (snapshot != null && snapshot.isReady()) {
                    succeeded++;
                    succeededSkuParents.add(summary.getSkuParent());
                } else {
                    failed++;
                    if (!StringUtils.hasText(firstFailure)) {
                        firstFailure = snapshot == null ? "empty product detail snapshot" : snapshot.getMessage();
                    }
                }
            } catch (RuntimeException exception) {
                failed++;
                if (!StringUtils.hasText(firstFailure)) {
                    firstFailure = StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : exception.getClass().getSimpleName();
                }
            }
        }

        int totalCount = summaries.size();
        int completedCount = skippedReady + succeeded;
        int remainingCount = Math.max(totalCount - completedCount, 0);
        String nextResumePosition = remainingCount <= 0
                ? null
                : nextResumePosition(missingSummaries, succeededSkuParents);
        result.setAttemptedCount(attempted);
        result.setSucceededCount(succeeded);
        result.setFailedCount(failed);
        result.setSkippedReadyCount(skippedReady);
        result.setTotalProductCount(totalCount);
        result.setCompletedCount(completedCount);
        result.setRemainingCount(remainingCount);
        result.setNextResumePosition(nextResumePosition);
        result.setFailureMessage(firstFailure);
        result.setDiagnosticSummary("product detail baseline sync attempted="
                + attempted
                + "; succeeded="
                + succeeded
                + "; failed="
                + failed
                + "; skippedReady="
                + skippedReady
                + "; total="
                + totalCount
                + "; completed="
                + completedCount
                + "; remaining="
                + remainingCount
                + "; batchLimit="
                + batchLimit
                + "; nextResumePosition="
                + (nextResumePosition == null ? "" : nextResumePosition)
                + "; site="
                + siteCode);
        return result;
    }

    private int resolveBatchLimit(NoonProductDetailBaselineSyncRequest request) {
        int requested = request == null ? 0 : request.getMaxDetailFetches();
        return requested > 0 ? requested : DEFAULT_MAX_DETAIL_FETCHES;
    }

    private List<ProductListSummaryView> missingSummaries(List<ProductListSummaryView> summaries) {
        List<ProductListSummaryView> missing = new ArrayList<>();
        for (ProductListSummaryView summary : summaries) {
            if (summary == null || !StringUtils.hasText(summary.getSkuParent())) {
                continue;
            }
            if ("ready".equalsIgnoreCase(summary.getDetailBaselineStatus())) {
                continue;
            }
            missing.add(summary);
        }
        return missing;
    }

    private List<ProductListSummaryView> rotateByResumePosition(
            List<ProductListSummaryView> summaries,
            String resumePosition
    ) {
        if (summaries.isEmpty() || !StringUtils.hasText(resumePosition)) {
            return summaries;
        }
        String normalizedResume = normalize(resumePosition);
        int startIndex = -1;
        for (int index = 0; index < summaries.size(); index++) {
            if (normalizedResume.equals(normalize(summaries.get(index).getSkuParent()))) {
                startIndex = index;
                break;
            }
        }
        if (startIndex <= 0) {
            return summaries;
        }
        List<ProductListSummaryView> rotated = new ArrayList<>();
        rotated.addAll(summaries.subList(startIndex, summaries.size()));
        rotated.addAll(summaries.subList(0, startIndex));
        return rotated;
    }

    private String nextResumePosition(List<ProductListSummaryView> missingSummaries, List<String> succeededSkuParents) {
        for (ProductListSummaryView summary : missingSummaries) {
            if (!containsSkuParent(succeededSkuParents, summary.getSkuParent())) {
                return summary.getSkuParent();
            }
        }
        return null;
    }

    private boolean containsSkuParent(List<String> skuParents, String skuParent) {
        String normalized = normalize(skuParent);
        for (String candidate : skuParents) {
            if (normalized.equals(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private ProductMasterFetchCommand command(Long ownerUserId, String storeCode, ProductListSummaryView summary) {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(ownerUserId);
        command.setStoreCode(storeCode);
        command.setSkuParent(summary.getSkuParent());
        command.setPartnerSku(summary.getPartnerSku());
        command.setPskuCode(summary.getPskuCode());
        return command;
    }
}
