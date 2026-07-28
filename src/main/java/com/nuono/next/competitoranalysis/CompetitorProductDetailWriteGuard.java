package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import org.springframework.util.StringUtils;

final class CompetitorProductDetailWriteGuard {
    private CompetitorProductDetailWriteGuard() {
    }

    static boolean writeIfCurrent(
            CompetitorAnalysisMapper mapper,
            CompetitorWatchProductRow watchProduct,
            CompetitorProductRow product,
            CompetitorProductDetailTarget target,
            NoonProductDetail detail,
            Long actorUserId
    ) {
        String expectedCode = normalize(target == null ? null : target.getNoonProductCode());
        if (!expectedCode.equals(normalize(detail == null ? null : detail.getNoonProductCode()))) {
            return false;
        }
        if (product == null) {
            CompetitorWatchProductRow current =
                    mapper.selectWatchProductForRefresh(watchProduct.getId());
            return current != null
                    && expectedCode.equals(normalize(current.getSelfNoonProductCode()));
        }
        return mapper.updateCompetitorProductFromDetail(
                update(product, detail, expectedCode, actorUserId)
        ) == 1;
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
