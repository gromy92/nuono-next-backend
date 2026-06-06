package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchAdapter;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchRequest;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Primary
@Profile("local-db")
public class CompetitorSearchRefreshRunner implements CompetitorKeywordRefreshRunner {
    private static final int SEARCH_LIMIT = 30;

    private final CompetitorAnalysisMapper mapper;
    private final NoonFrontendSearchAdapter adapter;

    public CompetitorSearchRefreshRunner(
            CompetitorAnalysisMapper mapper,
            NoonFrontendSearchAdapter adapter
    ) {
        this.mapper = mapper;
        this.adapter = adapter;
    }

    @Override
    public CompetitorKeywordRefreshOutcome refresh(CompetitorKeywordRefreshContext context) {
        CompetitorWatchProductRow watchProduct = context.getWatchProduct();
        CompetitorKeywordRow keyword = context.getKeyword();
        NoonSearchPage page = adapter.search(NoonSearchRequest.builder()
                .siteCode(watchProduct.getSiteCode())
                .locale(keyword.getLocale())
                .keyword(keyword.getKeyword())
                .limit(SEARCH_LIMIT)
                .build());

        Map<String, NoonSearchResult> resultsByCode = firstResultsByCode(page.getResults());
        Map<String, Long> searchResultIdsByCode = new LinkedHashMap<>();
        int resultCount = 0;
        for (NoonSearchResult result : resultsByCode.values()) {
            Long searchResultId = mapper.nextSearchResultId();
            mapper.insertSearchResult(buildSearchResult(context, result, page, searchResultId));
            searchResultIdsByCode.put(result.getNoonProductCode(), searchResultId);
            resultCount++;
        }

        int candidateCount = upsertCandidates(context, resultsByCode);
        int rankFactCount = writeRankFacts(context, resultsByCode, searchResultIdsByCode, page);
        CompetitorKeywordRefreshOutcome outcome = CompetitorKeywordRefreshOutcome.success(resultCount);
        outcome.setCandidateUpsertedCount(candidateCount);
        outcome.setRankFactWrittenCount(rankFactCount);
        outcome.setSourceUrl(page.getSourceUrl());
        outcome.setParserVersion(page.getParserVersion());
        outcome.setProviderHttpStatus(page.getProviderHttpStatus());
        outcome.setResponseHash(page.getResponseHash());
        outcome.setCapturedAt(page.getCapturedAt());
        return outcome;
    }

    private int upsertCandidates(
            CompetitorKeywordRefreshContext context,
            Map<String, NoonSearchResult> resultsByCode
    ) {
        int count = 0;
        String selfCode = normalizeCode(context.getWatchProduct().getSelfNoonProductCode());
        for (NoonSearchResult result : resultsByCode.values()) {
            if (result.getNoonProductCode().equals(selfCode)) {
                continue;
            }
            CompetitorProductRow existing = mapper.selectCompetitorProductByCode(
                    context.getWatchProduct().getId(),
                    result.getNoonProductCode()
            );
            Long productId;
            if (existing == null) {
                productId = mapper.nextCompetitorProductId();
                mapper.insertCompetitorProduct(buildProductInsert(context, result, productId));
            } else {
                productId = existing.getId();
                mapper.updateCompetitorProductFromSearch(buildProductInsert(context, result, productId));
            }
            mapper.upsertKeywordProductRelationFromSearch(buildRelationCommand(context, result, productId));
            count++;
        }
        return count;
    }

    private int writeRankFacts(
            CompetitorKeywordRefreshContext context,
            Map<String, NoonSearchResult> resultsByCode,
            Map<String, Long> searchResultIdsByCode,
            NoonSearchPage page
    ) {
        int count = 0;
        String selfCode = normalizeCode(context.getWatchProduct().getSelfNoonProductCode());
        if (StringUtils.hasText(selfCode)) {
            mapper.insertRankFact(buildRankFact(
                    context,
                    "SELF",
                    selfCode,
                    resultsByCode.get(selfCode),
                    searchResultIdsByCode.get(selfCode),
                    page
            ));
            count++;
        }

        List<CompetitorProductRow> confirmedProducts = mapper.listConfirmedCompetitorProductsByWatchProductId(
                context.getWatchProduct().getId()
        );
        for (CompetitorProductRow product : confirmedProducts) {
            String code = normalizeCode(product.getNoonProductCode());
            if (!StringUtils.hasText(code) || code.equals(selfCode)) {
                continue;
            }
            mapper.insertRankFact(buildRankFact(
                    context,
                    "COMPETITOR",
                    code,
                    resultsByCode.get(code),
                    searchResultIdsByCode.get(code),
                    page
            ));
            count++;
        }
        return count;
    }

    private Map<String, NoonSearchResult> firstResultsByCode(List<NoonSearchResult> results) {
        Map<String, NoonSearchResult> byCode = new LinkedHashMap<>();
        if (results == null) {
            return byCode;
        }
        for (NoonSearchResult result : results) {
            if (byCode.size() >= SEARCH_LIMIT) {
                break;
            }
            String code = normalizeCode(result.getNoonProductCode());
            if (!StringUtils.hasText(code)) {
                continue;
            }
            result.setNoonProductCode(code);
            result.setCodeType(NoonProductCodeSupport.codeType(code).orElse(result.getCodeType()));
            byCode.putIfAbsent(code, result);
        }
        return byCode;
    }

    private CompetitorProductInsertCommand buildProductInsert(
            CompetitorKeywordRefreshContext context,
            NoonSearchResult result,
            Long productId
    ) {
        CompetitorProductInsertCommand command = new CompetitorProductInsertCommand();
        command.setId(productId);
        command.setWatchProductId(context.getWatchProduct().getId());
        command.setNoonProductCode(result.getNoonProductCode());
        command.setCodeType(result.getCodeType());
        command.setCanonicalUrl(normalizeText(result.getCanonicalUrl()));
        command.setTitleSnapshot(normalizeText(result.getTitle()));
        command.setBrandSnapshot(normalizeText(result.getBrand()));
        command.setImageUrlSnapshot(normalizeText(result.getImageUrl()));
        command.setPriceAmountSnapshot(result.getPriceAmount());
        command.setCurrencyCodeSnapshot(normalizeText(result.getCurrencyCode()));
        command.setRatingSnapshot(result.getRating());
        command.setReviewCountSnapshot(result.getReviewCount());
        command.setSourceType("SEARCH_DISCOVERY");
        command.setReviewStatus("PENDING");
        command.setActorUserId(context.getActorUserId());
        return command;
    }

    private CompetitorKeywordProductSearchCommand buildRelationCommand(
            CompetitorKeywordRefreshContext context,
            NoonSearchResult result,
            Long productId
    ) {
        CompetitorKeywordProductSearchCommand command = new CompetitorKeywordProductSearchCommand();
        command.setId(mapper.nextKeywordProductId());
        command.setKeywordId(context.getKeyword().getId());
        command.setCompetitorProductId(productId);
        command.setSearchRunId(context.getSearchRunId());
        command.setRankNo(result.getPosition());
        command.setSponsored(result.isSponsored());
        command.setActorUserId(context.getActorUserId());
        return command;
    }

    private CompetitorSearchResultInsertCommand buildSearchResult(
            CompetitorKeywordRefreshContext context,
            NoonSearchResult result,
            NoonSearchPage page,
            Long searchResultId
    ) {
        CompetitorSearchResultInsertCommand command = new CompetitorSearchResultInsertCommand();
        command.setId(searchResultId);
        command.setKeywordRunId(context.getKeywordRunId());
        command.setResultPosition(result.getPosition());
        command.setNoonProductCode(result.getNoonProductCode());
        command.setCodeType(result.getCodeType());
        command.setCanonicalUrl(normalizeText(result.getCanonicalUrl()));
        command.setTitleSnapshot(normalizeText(result.getTitle()));
        command.setBrandSnapshot(normalizeText(result.getBrand()));
        command.setImageUrlSnapshot(normalizeText(result.getImageUrl()));
        command.setPriceAmount(result.getPriceAmount());
        command.setCurrencyCode(normalizeText(result.getCurrencyCode()));
        command.setRating(result.getRating());
        command.setReviewCount(result.getReviewCount());
        command.setSponsored(result.isSponsored());
        command.setRawResultJson(normalizeText(result.getRawResultJson()));
        command.setCapturedAt(page.getCapturedAt());
        command.setActorUserId(context.getActorUserId());
        return command;
    }

    private CompetitorRankFactInsertCommand buildRankFact(
            CompetitorKeywordRefreshContext context,
            String trackedProductType,
            String noonProductCode,
            NoonSearchResult result,
            Long sourceResultId,
            NoonSearchPage page
    ) {
        LocalDateTime factTime = page.getCapturedAt() == null ? LocalDateTime.now() : page.getCapturedAt();
        CompetitorRankFactInsertCommand command = new CompetitorRankFactInsertCommand();
        command.setId(mapper.nextRankFactId());
        command.setWatchProductId(context.getWatchProduct().getId());
        command.setKeywordId(context.getKeyword().getId());
        command.setKeywordRunId(context.getKeywordRunId());
        command.setSearchRunId(context.getSearchRunId());
        command.setFactTime(factTime);
        command.setFactDate(factTime.toLocalDate());
        command.setTrackedProductType(trackedProductType);
        command.setNoonProductCode(noonProductCode);
        command.setActorUserId(context.getActorUserId());
        if (result == null) {
            command.setRankStatus("NOT_IN_TOP_30");
            command.setSponsored(false);
            return command;
        }
        command.setRankStatus("RANKED");
        command.setRankNo(result.getPosition());
        command.setSponsored(result.isSponsored());
        command.setPriceAmount(result.getPriceAmount());
        command.setCurrencyCode(normalizeText(result.getCurrencyCode()));
        command.setRating(result.getRating());
        command.setReviewCount(result.getReviewCount());
        command.setSourceResultId(sourceResultId);
        return command;
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
