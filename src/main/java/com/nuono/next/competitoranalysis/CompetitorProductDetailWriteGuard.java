package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
class CompetitorProductDetailWriteGuard {
    private final CompetitorAnalysisMapper mapper;
    private final CompetitorProductSnapshotService snapshotService;
    private final CompetitorRefreshLeaseGuard leaseGuard;

    CompetitorProductDetailWriteGuard(
            CompetitorAnalysisMapper mapper,
            CompetitorProductSnapshotService snapshotService,
            CompetitorRefreshLeaseGuard leaseGuard
    ) {
        this.mapper = mapper;
        this.snapshotService = snapshotService;
        this.leaseGuard = leaseGuard;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(
            Long taskId,
            Long searchRunId,
            CompetitorWatchProductRow watchProduct,
            CompetitorProductRow product,
            CompetitorProductInsertCommand productUpdate,
            NoonProductDetail detail,
            Long actorUserId
    ) {
        Long watchProductId = watchProduct == null ? null : watchProduct.getId();
        leaseGuard.acquire(taskId, searchRunId, watchProductId);
        String expectedCode = normalize(product == null
                ? watchProduct == null ? null : watchProduct.getSelfNoonProductCode()
                : product.getNoonProductCode());
        if (!StringUtils.hasText(expectedCode)
                || !expectedCode.equals(normalize(
                        detail == null ? null : detail.getNoonProductCode()
                ))) {
            throw new CompetitorDetailTargetStaleException();
        }
        CompetitorWatchProductRow currentWatch =
                mapper.lockWatchProductForDetailWrite(watchProductId);
        if (!sameWatchScope(watchProduct, currentWatch)) {
            throw new CompetitorDetailTargetStaleException();
        }
        if (product == null) {
            if (!expectedCode.equals(normalize(currentWatch.getSelfNoonProductCode()))) {
                throw new CompetitorDetailTargetStaleException();
            }
            snapshotService.recordProductDetailSnapshot(
                    currentWatch, null, detail, searchRunId, actorUserId
            );
            return;
        }
        if (!Objects.equals(watchProductId, product.getWatchProductId())
                || product.getId() == null) {
            throw new CompetitorDetailTargetStaleException();
        }
        CompetitorProductRow currentProduct =
                mapper.lockConfirmedCompetitorProductForDetailWrite(
                        watchProductId, product.getId()
                );
        if (currentProduct == null
                || !Objects.equals(product.getId(), currentProduct.getId())
                || !Objects.equals(watchProductId, currentProduct.getWatchProductId())
                || !expectedCode.equals(normalize(currentProduct.getNoonProductCode()))) {
            throw new CompetitorDetailTargetStaleException();
        }
        if (productUpdate != null
                && (!Objects.equals(currentProduct.getId(), productUpdate.getId())
                || !Objects.equals(watchProductId, productUpdate.getWatchProductId())
                || !expectedCode.equals(normalize(productUpdate.getNoonProductCode()))
                || mapper.updateCompetitorProductFromDetail(productUpdate) != 1)) {
            throw new CompetitorDetailTargetStaleException();
        }
        snapshotService.recordProductDetailSnapshot(
                currentWatch, currentProduct, detail, searchRunId, actorUserId
        );
    }

    private static boolean sameWatchScope(
            CompetitorWatchProductRow expected,
            CompetitorWatchProductRow current
    ) {
        return expected != null
                && current != null
                && Objects.equals(expected.getId(), current.getId())
                && Objects.equals(expected.getOwnerUserId(), current.getOwnerUserId())
                && equalText(expected.getStoreCode(), current.getStoreCode())
                && equalText(expected.getSiteCode(), current.getSiteCode())
                && Objects.equals(
                        normalize(expected.getSelfNoonProductCode()),
                        normalize(current.getSelfNoonProductCode())
                );
    }

    private static String normalize(String value) {
        return NoonProductCodeSupport.normalize(value);
    }

    private static boolean equalText(String left, String right) {
        String normalizedLeft = StringUtils.hasText(left) ? left.trim() : "";
        String normalizedRight = StringUtils.hasText(right) ? right.trim() : "";
        return normalizedLeft.equalsIgnoreCase(normalizedRight);
    }
}
