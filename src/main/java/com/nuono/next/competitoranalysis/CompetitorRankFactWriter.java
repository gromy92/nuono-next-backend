package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.dp08.Dp08TrackedProduct;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class CompetitorRankFactWriter {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final CompetitorAnalysisMapper mapper;

    CompetitorRankFactWriter(CompetitorAnalysisMapper mapper) {
        this.mapper = mapper;
    }

    int write(
            CompetitorKeywordRefreshContext context,
            CompetitorSearchResultIndex resultIndex,
            Map<String, Long> searchResultIdsByRankKey,
            NoonSearchPage page,
            int scanDepth
    ) {
        int count = 0;
        String selfCode = normalizeCode(context.getWatchProduct().getSelfNoonProductCode());
        if (StringUtils.hasText(selfCode)) {
            count += writeProduct(context, "SELF", selfCode, resultIndex, searchResultIdsByRankKey, page, scanDepth);
        }
        List<CompetitorProductRow> confirmedProducts =
                mapper.listConfirmedCompetitorProductsByKeywordId(context.getKeyword().getId());
        for (CompetitorProductRow product : confirmedProducts) {
            String code = normalizeCode(product.getNoonProductCode());
            if (StringUtils.hasText(code) && !code.equals(selfCode)) {
                count += writeProduct(
                        context, "COMPETITOR", code, resultIndex, searchResultIdsByRankKey, page, scanDepth);
            }
        }
        return count;
    }

    int writeBound(
            CompetitorKeywordRefreshContext context,
            List<Dp08TrackedProduct> trackedProducts,
            CompetitorSearchResultIndex resultIndex,
            Map<String, Long> searchResultIdsByRankKey,
            NoonSearchPage page,
            int scanDepth
    ) {
        int count = 0;
        for (Dp08TrackedProduct product : trackedProducts) {
            count += writeProduct(
                    context, product.getSubjectType().name(), product.getNoonProductCode(),
                    resultIndex, searchResultIdsByRankKey, page, scanDepth
            );
        }
        return count;
    }

    private int writeProduct(
            CompetitorKeywordRefreshContext context,
            String trackedProductType,
            String code,
            CompetitorSearchResultIndex resultIndex,
            Map<String, Long> searchResultIdsByRankKey,
            NoonSearchPage page,
            int scanDepth
    ) {
        NoonSearchResult organic = resultIndex.firstResult(code, CompetitorSearchResultIndex.ORGANIC);
        insert(context, trackedProductType, code, organic, searchResultIdsByRankKey, page, scanDepth);
        NoonSearchResult sponsored = resultIndex.firstResult(code, CompetitorSearchResultIndex.SPONSORED);
        if (sponsored == null) {
            return 1;
        }
        insert(context, trackedProductType, code, sponsored, searchResultIdsByRankKey, page, scanDepth);
        return 2;
    }

    private void insert(
            CompetitorKeywordRefreshContext context,
            String trackedProductType,
            String code,
            NoonSearchResult result,
            Map<String, Long> searchResultIdsByRankKey,
            NoonSearchPage page,
            int scanDepth
    ) {
        LocalDateTime factTime = page.getCapturedAt() == null
                ? LocalDateTime.now(BUSINESS_ZONE)
                : page.getCapturedAt();
        String rankChannel = CompetitorSearchResultIndex.rankChannel(result);
        CompetitorRankFactInsertCommand command = new CompetitorRankFactInsertCommand();
        command.setId(mapper.nextRankFactId());
        command.setWatchProductId(context.getWatchProduct().getId());
        command.setKeywordId(context.getKeyword().getId());
        command.setKeywordRunId(context.getKeywordRunId());
        command.setSearchRunId(context.getSearchRunId());
        command.setFactTime(factTime);
        command.setFactDate(factTime.toLocalDate());
        command.setTrackedProductType(trackedProductType);
        command.setRankChannel(rankChannel);
        command.setNoonProductCode(code);
        command.setScanDepth(scanDepth);
        command.setActorUserId(context.getActorUserId());
        if (result == null) {
            command.setRankStatus("NOT_IN_SCAN_DEPTH");
            command.setSponsored(false);
        } else {
            command.setRankStatus("RANKED");
            command.setRankNo(CompetitorSearchResultIndex.rankPosition(result));
            command.setSponsored(result.isSponsored());
            command.setPriceAmount(result.getPriceAmount());
            command.setCurrencyCode(normalizeText(result.getCurrencyCode()));
            command.setSourceResultId(searchResultIdsByRankKey.get(CompetitorSearchResultIndex.rankKey(result)));
        }
        mapper.insertRankFact(command);
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
