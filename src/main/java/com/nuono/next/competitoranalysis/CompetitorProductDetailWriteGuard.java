package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CompetitorProductDetailWriteGuard {
    private final CompetitorAnalysisMapper mapper;
    private final CompetitorProductSnapshotService snapshotService;
    private final CompetitorRefreshLeaseGuard leaseGuard;

    @Autowired
    public CompetitorProductDetailWriteGuard(
            CompetitorAnalysisMapper mapper,
            CompetitorProductSnapshotService snapshotService,
            CompetitorRefreshLeaseGuard leaseGuard
    ) {
        this.mapper = mapper;
        this.snapshotService = snapshotService;
        this.leaseGuard = leaseGuard;
    }

    CompetitorProductDetailWriteGuard(
            CompetitorAnalysisMapper mapper,
            CompetitorProductSnapshotService snapshotService
    ) {
        this(
                mapper,
                snapshotService,
                CompetitorRefreshLeaseGuard.disabled(mapper)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean writeIfCurrent(
            Long taskId,
            CompetitorWatchProductRow watchProduct,
            CompetitorProductRow product,
            CompetitorProductDetailTarget target,
            NoonProductDetail detail,
            Long sourceRunId,
            Long actorUserId
    ) {
        leaseGuard.acquire(
                taskId,
                sourceRunId,
                watchProduct == null ? null : watchProduct.getId()
        );
        String expectedCode = normalize(target == null ? null : target.getNoonProductCode());
        if (watchProduct == null
                || watchProduct.getId() == null
                || target == null
                || !StringUtils.hasText(expectedCode)
                || !expectedCode.equals(normalize(detail == null ? null : detail.getNoonProductCode()))) {
            return false;
        }
        CompetitorWatchProductRow currentWatch =
                mapper.lockWatchProductForDetailWrite(watchProduct.getId());
        if (!sameWatchScope(watchProduct, currentWatch)) {
            return false;
        }
        if (product == null) {
            if (!target.isSelf()
                    || !expectedCode.equals(normalize(currentWatch.getSelfNoonProductCode()))) {
                return false;
            }
            snapshotService.recordProductDetailSnapshot(
                    currentWatch,
                    null,
                    detail,
                    sourceRunId,
                    actorUserId
            );
            return true;
        }
        if (!CompetitorProductDetailTarget.COMPETITOR.equals(target.getSubjectType())
                || target.getCompetitorProductId() == null
                || !Objects.equals(target.getCompetitorProductId(), product.getId())
                || !Objects.equals(watchProduct.getId(), product.getWatchProductId())) {
            return false;
        }
        CompetitorProductRow currentProduct =
                mapper.lockConfirmedCompetitorProductForDetailWrite(
                        watchProduct.getId(),
                        product.getId()
                );
        if (currentProduct == null
                || !expectedCode.equals(normalize(currentProduct.getNoonProductCode()))
                || mapper.updateCompetitorProductFromDetail(
                        update(currentProduct, detail, expectedCode, actorUserId)
                ) != 1) {
            return false;
        }
        snapshotService.recordProductDetailSnapshot(
                currentWatch,
                currentProduct,
                detail,
                sourceRunId,
                actorUserId
        );
        return true;
    }

    private static CompetitorProductInsertCommand update(
            CompetitorProductRow product,
            NoonProductDetail detail,
            String expectedCode,
            Long actorUserId
    ) {
        CompetitorProductInsertCommand command = new CompetitorProductInsertCommand();
        command.setId(product.getId());
        command.setWatchProductId(product.getWatchProductId());
        command.setNoonProductCode(expectedCode);
        command.setCodeType(detail.getCodeType());
        command.setCanonicalUrl(detail.getDetailUrl());
        command.setTitleSnapshot(firstNonBlank(detail.getTitleEn(), detail.getTitleAr()));
        command.setTitleEnSnapshot(detail.getTitleEn());
        command.setTitleArSnapshot(detail.getTitleAr());
        command.setBrandSnapshot(detail.getBrand());
        command.setImageUrlSnapshot(firstNonBlank(
                detail.getMainImageUrlNormalized(),
                detail.getMainImageUrlRaw()
        ));
        command.setPriceAmountSnapshot(detail.getPriceAmount());
        command.setCurrencyCodeSnapshot(detail.getCurrencyCode());
        command.setRatingSnapshot(detail.getRating());
        command.setReviewCountSnapshot(detail.getReviewCount());
        command.setTagsSnapshotJson(firstNonBlank(
                detail.getBadgesJson(),
                detail.getLogisticsTagsJson()
        ));
        command.setSourceType("PRODUCT_DETAIL");
        command.setActorUserId(actorUserId);
        return command;
    }

    private static String normalize(String value) {
        String normalized = NoonProductCodeSupport.normalize(value);
        return normalized == null ? "" : normalized;
    }

    private static boolean sameWatchScope(
            CompetitorWatchProductRow expected,
            CompetitorWatchProductRow current
    ) {
        return current != null
                && Objects.equals(expected.getId(), current.getId())
                && Objects.equals(expected.getOwnerUserId(), current.getOwnerUserId())
                && equalText(expected.getStoreCode(), current.getStoreCode())
                && equalText(expected.getSiteCode(), current.getSiteCode())
                && normalize(expected.getSelfNoonProductCode())
                        .equals(normalize(current.getSelfNoonProductCode()));
    }

    private static boolean equalText(String left, String right) {
        String normalizedLeft = StringUtils.hasText(left) ? left.trim() : "";
        String normalizedRight = StringUtils.hasText(right) ? right.trim() : "";
        return normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
