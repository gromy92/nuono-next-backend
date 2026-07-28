package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class CompetitorBrowserSearchEvidenceWriter {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration RUN_MAX_AGE = Duration.ofMinutes(30);
    private static final Duration RUN_FUTURE_SKEW = Duration.ofMinutes(5);

    private final CompetitorAnalysisMapper mapper;

    CompetitorBrowserSearchEvidenceWriter(CompetitorAnalysisMapper mapper) {
        this.mapper = mapper;
    }

    void lockFreshRun(CompetitorKeywordRunRow run) {
        LocalDateTime capturedAt = run == null ? null : run.getCapturedAt();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (capturedAt == null
                || capturedAt.isBefore(now.minus(RUN_MAX_AGE))
                || capturedAt.isAfter(now.plus(RUN_FUTURE_SKEW))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "COMPETITOR_BROWSER_OBSERVATION_RUN_STALE");
        }
        Long lockedRunId = mapper.lockKeywordRunForBrowserObservation(run.getId());
        if (!run.getId().equals(lockedRunId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "COMPETITOR_BROWSER_OBSERVATION_RUN_STALE");
        }
    }

    Result persist(
            CompetitorKeywordRunRow latestRun,
            CompetitorBrowserObservationItem item,
            String noonCode,
            Long actorUserId
    ) {
        CompetitorSearchResultObservationRow existing =
                mapper.selectBrowserSponsoredSearchResultByCode(latestRun.getId(), noonCode);
        if (existing != null) {
            CompetitorSearchResultInsertCommand update =
                    buildEvidence(latestRun, item, noonCode, actorUserId, null);
            update.setId(existing.getId());
            int updated = mapper.updateSponsoredSearchResultFromBrowser(update);
            return new Result(existing.getId(), 0, updated);
        }
        Integer nextPosition = mapper.selectNextSearchResultPosition(latestRun.getId());
        CompetitorSearchResultInsertCommand insert = buildEvidence(
                latestRun,
                item,
                noonCode,
                actorUserId,
                nextPosition == null || nextPosition < 1 ? 1 : nextPosition
        );
        insert.setId(mapper.nextSearchResultId());
        mapper.insertSearchResult(insert);
        return new Result(insert.getId(), 1, 0);
    }

    private CompetitorSearchResultInsertCommand buildEvidence(
            CompetitorKeywordRunRow latestRun,
            CompetitorBrowserObservationItem item,
            String noonCode,
            Long actorUserId,
            Integer position
    ) {
        CompetitorSearchResultInsertCommand insert = new CompetitorSearchResultInsertCommand();
        insert.setKeywordRunId(latestRun.getId());
        insert.setResultPosition(position);
        insert.setNoonProductCode(noonCode);
        insert.setCodeType(noonCode.startsWith("Z") ? "Z_CODE" : "N_CODE");
        insert.setCanonicalUrl(normalizeText(item.getCanonicalUrl()));
        insert.setTitleSnapshot(normalizeText(item.getTitle()));
        insert.setBrandSnapshot(normalizeText(item.getBrand()));
        insert.setImageUrlSnapshot(normalizeText(item.getImageUrl()));
        insert.setPriceAmount(item.getPriceAmount());
        insert.setCurrencyCode(normalizeText(item.getCurrencyCode()));
        insert.setRating(item.getRating());
        insert.setReviewCount(item.getReviewCount());
        insert.setSponsored(true);
        insert.setRawResultJson("{\"source\":\"browser-observation\",\"observedPosition\":"
                + normalizeSourcePosition(item.getPosition())
                + ",\"noonProductCode\":\""
                + escapeJson(noonCode)
                + "\"}");
        insert.setCapturedAt(latestRun.getCapturedAt() == null
                ? LocalDateTime.now(BUSINESS_ZONE)
                : latestRun.getCapturedAt());
        insert.setActorUserId(actorUserId);
        return insert;
    }

    private int normalizeSourcePosition(Integer position) {
        return position == null || position < 1 ? 999 : Math.min(position, 999);
    }

    private String normalizeText(String value) {
        return org.springframework.util.StringUtils.hasText(value) ? value.trim() : null;
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static final class Result {
        private final Long sourceResultId;
        private final int insertedCount;
        private final int updatedCount;

        private Result(Long sourceResultId, int insertedCount, int updatedCount) {
            this.sourceResultId = sourceResultId;
            this.insertedCount = insertedCount;
            this.updatedCount = updatedCount;
        }

        Long sourceResultId() { return sourceResultId; }
        int insertedCount() { return insertedCount; }
        int updatedCount() { return updatedCount; }
    }
}
