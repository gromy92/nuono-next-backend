package com.nuono.next.competitoranalysis;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.competitoranalysis.dp08.Dp08TrackedProduct;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.Dp08RankMemberMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/** Writes only DP-08-A source facts from the immutable task-bound product cohort. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
class Dp08ImmutableRankingPageWriter {
    private static final int RANK_SCAN_DEPTH = 200;
    private static final int MAX_TITLE_LENGTH = 500;
    private final CompetitorAnalysisMapper mapper;
    private final CompetitorRankFactWriter rankFactWriter;
    private final CompetitorProductSnapshotService snapshotService;
    private final Dp08RankMemberMapper rankMembers;

    @Autowired
    Dp08ImmutableRankingPageWriter(
            CompetitorAnalysisMapper mapper,
            CompetitorProductSnapshotService snapshotService,
            Dp08RankMemberMapper rankMembers
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.rankFactWriter = new CompetitorRankFactWriter(mapper);
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService");
        this.rankMembers=Objects.requireNonNull(rankMembers,"rankMembers");
    }

    Dp08ImmutableRankingPageWriter(
            CompetitorAnalysisMapper mapper,CompetitorProductSnapshotService snapshotService
    ) {
        this.mapper=Objects.requireNonNull(mapper,"mapper");
        this.rankFactWriter=new CompetitorRankFactWriter(mapper);
        this.snapshotService=Objects.requireNonNull(snapshotService,"snapshotService");
        this.rankMembers=null;
    }

    CompetitorKeywordRefreshOutcome apply(
            CompetitorKeywordRefreshContext context,
            NoonSearchPage page,
            List<Dp08TrackedProduct> trackedProducts
    ) {
        context.validateLease();
        List<Dp08TrackedProduct> products = List.copyOf(
                Objects.requireNonNull(trackedProducts, "trackedProducts")
        );
        CompetitorSearchResultIndex index = CompetitorSearchResultIndex.from(
                page.getResults(), RANK_SCAN_DEPTH
        );
        Map<String, Long> resultIds = insertResults(context, page, index);
        return applyMembers(context,page,products,index,resultIds);
    }

    void initialize(CompetitorKeywordRefreshContext context,NoonSearchPage page) {
        insertResults(context,page,CompetitorSearchResultIndex.from(page.getResults(),RANK_SCAN_DEPTH));
    }

    CompetitorKeywordRefreshOutcome applyMembers(
            CompetitorKeywordRefreshContext context,
            NoonSearchPage page,
            List<Dp08TrackedProduct> trackedProducts
    ) {
        CompetitorSearchResultIndex index=CompetitorSearchResultIndex.from(page.getResults(),RANK_SCAN_DEPTH);
        Map<String,Long> resultIds=new LinkedHashMap<>();
        if(rankMembers==null)throw new IllegalStateException("DP08 rank-member mapper is unavailable");
        List<Dp08SearchResultIdentityRow> stored=rankMembers.listSearchResultIdentities(context.getKeywordRunId());
        if(stored.size()>RANK_SCAN_DEPTH)throw new IllegalStateException("DP08 search-result bound exceeded");
        for(Dp08SearchResultIdentityRow row:stored){String channel=Boolean.TRUE.equals(row.getSponsored())
                ?CompetitorSearchResultIndex.SPONSORED:CompetitorSearchResultIndex.ORGANIC;
            resultIds.putIfAbsent(CompetitorSearchResultIndex.rankKey(row.getNoonProductCode(),channel),row.getId());}
        if(stored.size()!=index.orderedResults().size())
            throw new IllegalStateException("DP08 stored search-result cohort drift");
        return applyMembers(context,page,List.copyOf(trackedProducts),index,resultIds);
    }

    private CompetitorKeywordRefreshOutcome applyMembers(
            CompetitorKeywordRefreshContext context,NoonSearchPage page,List<Dp08TrackedProduct> products,
            CompetitorSearchResultIndex index,Map<String,Long> resultIds
    ) {
        Map<String, NoonSearchResult> resultsByCode = index.firstResultsByCode(RANK_SCAN_DEPTH);
        recordSnapshots(context, page, products, resultsByCode);
        int rankCount = rankFactWriter.writeBound(
                context, products, index, resultIds, page, RANK_SCAN_DEPTH
        );
        CompetitorKeywordRefreshOutcome outcome = CompetitorKeywordRefreshOutcome.success(
                index.orderedResults().size()
        );
        outcome.setCandidateUpsertedCount(0);
        outcome.setRankFactWrittenCount(rankCount);
        outcome.setRequestedResultLimit(RANK_SCAN_DEPTH);
        outcome.setSourceUrl(page.getSourceUrl());
        outcome.setParserVersion(page.getParserVersion());
        outcome.setProviderHttpStatus(page.getProviderHttpStatus());
        outcome.setResponseHash(page.getResponseHash());
        outcome.setCapturedAt(page.getCapturedAt());
        return outcome;
    }

    private Map<String, Long> insertResults(
            CompetitorKeywordRefreshContext context,
            NoonSearchPage page,
            CompetitorSearchResultIndex index
    ) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (NoonSearchResult item : index.orderedResults()) {
            long id = mapper.nextSearchResultId();
            mapper.insertSearchResult(searchResult(context, page, item, id));
            result.putIfAbsent(CompetitorSearchResultIndex.rankKey(item), id);
        }
        return result;
    }

    private void recordSnapshots(
            CompetitorKeywordRefreshContext context,
            NoonSearchPage page,
            List<Dp08TrackedProduct> products,
            Map<String, NoonSearchResult> resultsByCode
    ) {
        Map<String, NoonSearchResult> trackedResults = new LinkedHashMap<>();
        Map<String, Long> productIds = new LinkedHashMap<>();
        for (Dp08TrackedProduct product : products) {
            String code = normalizeCode(product.getNoonProductCode());
            NoonSearchResult result = resultsByCode.get(code);
            if (result == null) {
                continue;
            }
            trackedResults.put(code, result);
            if (product.getCompetitorProductId() != null) {
                productIds.put(code, product.getCompetitorProductId());
            }
        }
        snapshotService.recordSearchSnapshots(
                context, page, trackedResults, productIds
        );
    }

    private CompetitorSearchResultInsertCommand searchResult(
            CompetitorKeywordRefreshContext context,
            NoonSearchPage page,
            NoonSearchResult result,
            long id
    ) {
        CompetitorSearchResultInsertCommand command = new CompetitorSearchResultInsertCommand();
        command.setId(id);
        command.setKeywordRunId(context.getKeywordRunId());
        command.setResultPosition(result.getPosition());
        command.setNoonProductCode(result.getNoonProductCode());
        command.setCodeType(result.getCodeType());
        command.setCanonicalUrl(text(result.getCanonicalUrl()));
        command.setTitleSnapshot(title(first(result.getTitle(), result.getTitleEn(), result.getTitleAr())));
        command.setTitleEnSnapshot(title(result.getTitleEn()));
        command.setTitleArSnapshot(title(result.getTitleAr()));
        command.setImageUrlSnapshot(text(result.getImageUrl()));
        command.setPriceAmount(result.getPriceAmount());
        command.setCurrencyCode(text(result.getCurrencyCode()));
        command.setSponsored(result.isSponsored());
        command.setTagsJson(text(result.getTagsJson()));
        command.setCapturedAt(page.getCapturedAt());
        command.setActorUserId(context.getActorUserId());
        return command;
    }

    private static String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String title(String value) {
        String normalized = text(value);
        return normalized == null || normalized.length() <= MAX_TITLE_LENGTH
                ? normalized : normalized.substring(0, MAX_TITLE_LENGTH);
    }

    private static String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
