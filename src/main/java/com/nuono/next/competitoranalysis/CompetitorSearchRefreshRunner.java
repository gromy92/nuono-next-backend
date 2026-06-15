package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchAdapter;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchRequest;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private static final int CANDIDATE_DISCOVERY_LIMIT = 20;
    private static final int RANK_SCAN_DEPTH = 100;
    private static final int MAX_TITLE_SNAPSHOT_LENGTH = 500;

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
                .limit(RANK_SCAN_DEPTH)
                .build());

        Map<String, NoonSearchResult> resultsByCode = firstResultsByCode(page.getResults(), RANK_SCAN_DEPTH);
        Map<String, NoonSearchResult> candidateResultsByCode = firstResultsByCode(
                page.getResults(),
                CANDIDATE_DISCOVERY_LIMIT
        );
        Map<String, Long> searchResultIdsByCode = new LinkedHashMap<>();
        int resultCount = 0;
        for (NoonSearchResult result : resultsByCode.values()) {
            Long searchResultId = mapper.nextSearchResultId();
            mapper.insertSearchResult(buildSearchResult(context, result, page, searchResultId));
            searchResultIdsByCode.put(result.getNoonProductCode(), searchResultId);
            resultCount++;
        }

        int candidateCount = upsertCandidates(context, candidateResultsByCode);
        int rankFactCount = writeRankFacts(context, resultsByCode, searchResultIdsByCode, page);
        CompetitorKeywordRefreshOutcome outcome = CompetitorKeywordRefreshOutcome.success(resultCount);
        outcome.setCandidateUpsertedCount(candidateCount);
        outcome.setRankFactWrittenCount(rankFactCount);
        outcome.setRequestedResultLimit(RANK_SCAN_DEPTH);
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
        List<Long> currentProductIds = new ArrayList<>();
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
            mapper.upsertKeywordProductRelationFromSearch(buildRelationCommand(context, result, productId, existing));
            currentProductIds.add(productId);
            count++;
        }
        mapper.softDeleteDiscoveredKeywordProductRelationsOutsideSet(
                context.getKeyword().getId(),
                currentProductIds,
                context.getActorUserId()
        );
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

        List<CompetitorProductRow> confirmedProducts = mapper.listConfirmedCompetitorProductsByKeywordId(
                context.getKeyword().getId()
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

    private Map<String, NoonSearchResult> firstResultsByCode(List<NoonSearchResult> results, int limit) {
        Map<String, NoonSearchResult> byCode = new LinkedHashMap<>();
        if (results == null) {
            return byCode;
        }
        for (NoonSearchResult result : results) {
            if (byCode.size() >= limit) {
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
        command.setTitleSnapshot(normalizeTitleSnapshot(result.getTitle()));
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
            Long productId,
            CompetitorProductRow existingProduct
    ) {
        CompetitorKeywordProductSearchCommand command = new CompetitorKeywordProductSearchCommand();
        command.setId(mapper.nextKeywordProductId());
        command.setKeywordId(context.getKeyword().getId());
        command.setCompetitorProductId(productId);
        command.setRelationStatus(isConfirmedProduct(existingProduct) ? "CONFIRMED" : "DISCOVERED");
        command.setSearchRunId(context.getSearchRunId());
        command.setRankNo(result.getPosition());
        command.setSponsored(result.isSponsored());
        command.setActorUserId(context.getActorUserId());
        return command;
    }

    private boolean isConfirmedProduct(CompetitorProductRow product) {
        return product != null && "CONFIRMED".equalsIgnoreCase(product.getReviewStatus());
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
        command.setTitleSnapshot(normalizeTitleSnapshot(result.getTitle()));
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
        command.setScanDepth(RANK_SCAN_DEPTH);
        if (result == null) {
            command.setRankStatus("NOT_IN_SCAN_DEPTH");
            command.setSponsored(false);
            command.setRankChannel("ORGANIC");
            return command;
        }
        command.setRankStatus("RANKED");
        command.setRankNo(result.getPosition());
        command.setSponsored(result.isSponsored());
        command.setRankChannel(result.isSponsored() ? "SPONSORED" : "ORGANIC");
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

    private String normalizeTitleSnapshot(String value) {
        String normalized = normalizeText(value);
        if (normalized == null || normalized.length() <= MAX_TITLE_SNAPSHOT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TITLE_SNAPSHOT_LENGTH);
    }
}
