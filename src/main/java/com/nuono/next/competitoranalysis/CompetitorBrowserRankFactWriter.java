package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class CompetitorBrowserRankFactWriter {
    private static final int RANK_SCAN_DEPTH = 100;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final CompetitorAnalysisMapper mapper;

    CompetitorBrowserRankFactWriter(CompetitorAnalysisMapper mapper) {
        this.mapper = mapper;
    }

    int reconcile(
            CompetitorKeywordRunRow latestRun,
            CompetitorKeywordScopeRow keyword,
            CompetitorWatchProductRow watchProduct,
            CompetitorBrowserObservationItem item,
            CompetitorProductRow product,
            Long sourceResultId,
            int sponsoredRankPosition,
            Long actorUserId
    ) {
        String code = normalizeCode(item.getNoonProductCode());
        String selfCode = normalizeCode(watchProduct.getSelfNoonProductCode());
        if (!StringUtils.hasText(code)) {
            return 0;
        }
        String trackedProductType;
        if (code.equals(selfCode)) {
            trackedProductType = "SELF";
        } else if (product != null && "CONFIRMED".equalsIgnoreCase(product.getReviewStatus())) {
            trackedProductType = "COMPETITOR";
        } else {
            return 0;
        }

        CompetitorRankFactInsertCommand command = buildCommand(
                latestRun,
                keyword,
                watchProduct,
                item,
                trackedProductType,
                code,
                sourceResultId,
                sponsoredRankPosition,
                actorUserId
        );
        Long existingId = mapper.selectRankFactId(
                latestRun.getId(), trackedProductType, code, "SPONSORED");
        if (existingId != null) {
            command.setId(existingId);
            return mapper.updateSponsoredRankFact(command);
        }
        command.setId(mapper.nextRankFactId());
        return mapper.insertRankFact(command);
    }

    private CompetitorRankFactInsertCommand buildCommand(
            CompetitorKeywordRunRow latestRun,
            CompetitorKeywordScopeRow keyword,
            CompetitorWatchProductRow watchProduct,
            CompetitorBrowserObservationItem item,
            String trackedProductType,
            String code,
            Long sourceResultId,
            int sponsoredRankPosition,
            Long actorUserId
    ) {
        LocalDateTime factTime = latestRun.getCapturedAt() == null
                ? LocalDateTime.now(BUSINESS_ZONE)
                : latestRun.getCapturedAt();
        CompetitorRankFactInsertCommand command = new CompetitorRankFactInsertCommand();
        command.setWatchProductId(watchProduct.getId());
        command.setKeywordId(keyword.getKeywordId());
        command.setKeywordRunId(latestRun.getId());
        command.setSearchRunId(latestRun.getSearchRunId());
        command.setFactTime(factTime);
        command.setFactDate(factTime.toLocalDate());
        command.setTrackedProductType(trackedProductType);
        command.setRankChannel("SPONSORED");
        command.setNoonProductCode(code);
        command.setRankStatus("RANKED");
        command.setRankNo(sponsoredRankPosition);
        command.setScanDepth(RANK_SCAN_DEPTH);
        command.setSponsored(true);
        command.setPriceAmount(item.getPriceAmount());
        command.setCurrencyCode(normalizeText(item.getCurrencyCode()));
        command.setRating(item.getRating());
        command.setReviewCount(item.getReviewCount());
        command.setSourceResultId(sourceResultId);
        command.setActorUserId(actorUserId);
        return command;
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
