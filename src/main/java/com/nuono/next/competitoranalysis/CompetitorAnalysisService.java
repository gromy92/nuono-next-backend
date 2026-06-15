package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompetitorAnalysisService {

    private static final Pattern COLLAPSED_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NOON_CODE_PATTERN = Pattern.compile("(?i)(^|/)([ZN][A-Z0-9]{4,79})(/|\\?|$)");

    private final CompetitorAnalysisMapper mapper;
    private final CompetitorProductChangeService productChangeService;

    @Autowired
    public CompetitorAnalysisService(
            CompetitorAnalysisMapper mapper,
            CompetitorProductChangeService productChangeService
    ) {
        this.mapper = mapper;
        this.productChangeService = productChangeService;
    }

    public CompetitorAnalysisService(CompetitorAnalysisMapper mapper) {
        this(mapper, null);
    }

    public CompetitorWatchProductListView listWatchProducts(
            BusinessAccessContext context,
            CompetitorWatchProductQuery query
    ) {
        CompetitorWatchProductQuery resolvedQuery = query == null
                ? CompetitorWatchProductQuery.fromRequest(null, null, null, null, null, null, null, null)
                : query;
        Long ownerUserId = ownerUserIdForQuery(context, resolvedQuery.getStoreCode());
        List<String> scopedStoreCodes = scopedStoreCodes(context, resolvedQuery.getStoreCode());
        if (scopedStoreCodes.isEmpty()) {
            return CompetitorWatchProductListView.fromRows(List.of(), resolvedQuery, 0);
        }
        long total = mapper.countWatchProducts(ownerUserId, scopedStoreCodes, resolvedQuery);
        List<CompetitorWatchProductListRow> rows = total <= 0
                ? List.of()
                : mapper.listWatchProducts(ownerUserId, scopedStoreCodes, resolvedQuery);
        return CompetitorWatchProductListView.fromRows(rows, resolvedQuery, total);
    }

    public CompetitorWatchProductListView listProductBaselines(
            BusinessAccessContext context,
            CompetitorWatchProductQuery query
    ) {
        CompetitorWatchProductQuery resolvedQuery = query == null
                ? CompetitorWatchProductQuery.fromRequest(null, null, null, null, null, null, null, null)
                : query;
        String storeCode = requireText(resolvedQuery.getStoreCode(), "COMPETITOR_STORE_REQUIRED")
                .toUpperCase(Locale.ROOT);
        String siteCode = requireText(resolvedQuery.getSiteCode(), "COMPETITOR_SITE_REQUIRED")
                .toUpperCase(Locale.ROOT);
        Long ownerUserId = ownerUserId(context, storeCode);
        long total = mapper.countProductBaselines(ownerUserId, storeCode, siteCode, resolvedQuery);
        List<CompetitorWatchProductListRow> rows = total <= 0
                ? List.of()
                : mapper.listProductBaselines(ownerUserId, storeCode, siteCode, resolvedQuery);
        return CompetitorWatchProductListView.fromRows(rows, resolvedQuery, total);
    }

    public List<CompetitorProductOptionView> productOptions(
            BusinessAccessContext context,
            String storeCode,
            String siteCode,
            String keyword,
            Integer limit
    ) {
        String normalizedStoreCode = requireText(storeCode, "COMPETITOR_STORE_REQUIRED").toUpperCase(Locale.ROOT);
        String normalizedSiteCode = requireText(siteCode, "COMPETITOR_SITE_REQUIRED").toUpperCase(Locale.ROOT);
        Long ownerUserId = ownerUserId(context, normalizedStoreCode);
        int normalizedLimit = normalizeOptionLimit(limit);
        return mapper.listProductOptions(
                        ownerUserId,
                        normalizedStoreCode,
                        normalizedSiteCode,
                        normalizeText(keyword),
                        normalizedLimit
                )
                .stream()
                .map(this::toProductOptionView)
                .filter(item -> item != null)
                .collect(Collectors.toList());
    }

    public CompetitorWatchProductDetailView addKeyword(
            BusinessAccessContext context,
            Long watchProductId,
            CompetitorKeywordCommand command
    ) {
        CompetitorWatchProductRow watchProduct = requireWatchProduct(context, watchProductId);
        String keyword = normalizeKeywordDisplay(command == null ? null : command.getKeyword());
        String keywordNorm = normalizeKeywordNorm(keyword);
        CompetitorKeywordRow existing = mapper.selectKeywordByNorm(watchProduct.getId(), keywordNorm);
        if (existing == null) {
            CompetitorKeywordInsertCommand insert = new CompetitorKeywordInsertCommand();
            insert.setId(mapper.nextKeywordId());
            insert.setWatchProductId(watchProduct.getId());
            insert.setKeyword(keyword);
            insert.setKeywordNorm(keywordNorm);
            insert.setLocale(normalizeText(command == null ? null : command.getLocale()));
            insert.setStatus("ACTIVE");
            insert.setDisplayOrder(command == null || command.getDisplayOrder() == null ? 0 : command.getDisplayOrder());
            insert.setActorUserId(actorUserId(context));
            mapper.insertKeyword(insert);
        }
        return detail(context, watchProduct.getId());
    }

    public CompetitorWatchProductDetailView updateKeyword(
            BusinessAccessContext context,
            Long keywordId,
            CompetitorKeywordCommand command
    ) {
        CompetitorKeywordScopeRow scope = requireKeywordScope(keywordId);
        requireStoreInContext(context, scope.getStoreCode());
        CompetitorKeywordUpdateCommand update = new CompetitorKeywordUpdateCommand();
        update.setId(keywordId);
        if (command != null && StringUtils.hasText(command.getKeyword())) {
            String keyword = normalizeKeywordDisplay(command.getKeyword());
            update.setKeyword(keyword);
            update.setKeywordNorm(normalizeKeywordNorm(keyword));
        }
        if (command != null) {
            update.setLocale(normalizeNullableText(command.getLocale()));
            update.setStatus(normalizeStatus(command.getStatus()));
            update.setDisplayOrder(command.getDisplayOrder());
        }
        update.setActorUserId(actorUserId(context));
        mapper.updateKeyword(update);
        return detail(context, scope.getWatchProductId());
    }

    public CompetitorWatchProductDetailView deleteKeyword(BusinessAccessContext context, Long keywordId) {
        CompetitorKeywordScopeRow scope = requireKeywordScope(keywordId);
        requireStoreInContext(context, scope.getStoreCode());
        mapper.softDeleteKeyword(keywordId, actorUserId(context));
        return detail(context, scope.getWatchProductId());
    }

    public CompetitorWatchProductDetailView addManualCompetitor(
            BusinessAccessContext context,
            Long watchProductId,
            CompetitorManualCompetitorCommand command
    ) {
        CompetitorWatchProductRow watchProduct = requireWatchProduct(context, watchProductId);
        Long keywordId = command == null ? null : command.getKeywordId();
        CompetitorKeywordScopeRow keyword = requireKeywordScope(keywordId);
        if (!watchProduct.getId().equals(keyword.getWatchProductId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPETITOR_KEYWORD_SCOPE_MISMATCH");
        }
        if (!"ACTIVE".equalsIgnoreCase(nullToEmpty(keyword.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPETITOR_KEYWORD_INACTIVE");
        }
        ParsedNoonCode parsed = parseNoonCode(command == null ? null : command.getInput());
        if (parsed == null) {
            throw badRequest("COMPETITOR_NOON_CODE_REQUIRED");
        }
        if (parsed.noonCode.equals(normalizeNoonCode(watchProduct.getSelfNoonProductCode()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPETITOR_SELF_CODE_FORBIDDEN");
        }
        Long actorUserId = actorUserId(context);
        CompetitorProductRow product = mapper.selectCompetitorProductByCode(watchProduct.getId(), parsed.noonCode);
        Long competitorProductId;
        if (product == null) {
            competitorProductId = mapper.nextCompetitorProductId();
            mapper.insertCompetitorProduct(buildManualProductInsert(
                    competitorProductId,
                    watchProduct.getId(),
                    parsed,
                    command,
                    actorUserId
            ));
        } else {
            competitorProductId = product.getId();
            if (!"CONFIRMED".equalsIgnoreCase(nullToEmpty(product.getReviewStatus()))) {
                mapper.markCompetitorProductConfirmed(competitorProductId, actorUserId);
            }
        }
        mapper.upsertKeywordProductRelation(keyword.getKeywordId(), competitorProductId, "CONFIRMED", actorUserId);
        return detail(context, watchProduct.getId());
    }

    public CompetitorWatchProductScopeRow requireKeywordCandidateScope(
            Long keywordId,
            Long competitorProductId
    ) {
        KeywordCandidateScope scope = requireKeywordCandidateScopeInternal(keywordId, competitorProductId);
        return scope.toWatchProductScope();
    }

    public CompetitorWatchProductScopeRow requireKeywordWatchProductScope(Long keywordId) {
        return toWatchProductScope(requireKeywordScope(keywordId));
    }

    public CompetitorWatchProductDetailView confirmCandidate(
            BusinessAccessContext context,
            Long keywordId,
            Long competitorProductId
    ) {
        KeywordCandidateScope scope = requireKeywordCandidateScopeInternal(keywordId, competitorProductId);
        requireStoreInContext(context, scope.keyword.getStoreCode());
        CompetitorWatchProductRow watchProduct = mapper.selectWatchProductById(scope.keyword.getOwnerUserId(), scope.keyword.getWatchProductId());
        String selfCode = watchProduct == null ? null : normalizeNoonCode(watchProduct.getSelfNoonProductCode());
        if (selfCode != null && selfCode.equals(normalizeNoonCode(scope.product.getNoonProductCode()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPETITOR_SELF_CODE_FORBIDDEN");
        }
        Long actorUserId = actorUserId(context);
        mapper.markCompetitorProductConfirmed(competitorProductId, actorUserId);
        applyCompetitorToAllActiveKeywords(scope.keyword.getWatchProductId(), competitorProductId, "CONFIRMED", actorUserId);
        return detail(context, scope.keyword.getWatchProductId());
    }

    public CompetitorWatchProductDetailView ignoreCandidate(
            BusinessAccessContext context,
            Long keywordId,
            Long competitorProductId
    ) {
        KeywordCandidateScope scope = requireKeywordCandidateScopeInternal(keywordId, competitorProductId);
        requireStoreInContext(context, scope.keyword.getStoreCode());
        if ("CONFIRMED".equalsIgnoreCase(nullToEmpty(scope.product.getReviewStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPETITOR_CONFIRMED_CANNOT_IGNORE");
        }
        mapper.softDeleteKeywordProductRelation(keywordId, competitorProductId, actorUserId(context));
        return detail(context, scope.keyword.getWatchProductId());
    }

    public CompetitorWatchProductDetailView removeCandidateFromKeyword(
            BusinessAccessContext context,
            Long keywordId,
            Long competitorProductId
    ) {
        KeywordCandidateScope scope = requireKeywordCandidateScopeInternal(keywordId, competitorProductId);
        requireStoreInContext(context, scope.keyword.getStoreCode());
        mapper.softDeleteKeywordProductRelation(keywordId, competitorProductId, actorUserId(context));
        return detail(context, scope.keyword.getWatchProductId());
    }

    public CompetitorWatchProductDetailView createWatchProduct(
            BusinessAccessContext context,
            CompetitorWatchProductCreateCommand command
    ) {
        if (command == null) {
            throw badRequest("COMPETITOR_REQUEST_REQUIRED");
        }
        String storeCode = requireText(command.getStoreCode(), "COMPETITOR_STORE_REQUIRED").toUpperCase(Locale.ROOT);
        String siteCode = requireText(command.getSiteCode(), "COMPETITOR_SITE_REQUIRED").toUpperCase(Locale.ROOT);
        if (command.getProductSiteOfferId() == null) {
            throw badRequest("COMPETITOR_PRODUCT_SITE_OFFER_REQUIRED");
        }
        Long ownerUserId = ownerUserId(context, storeCode);
        CompetitorProductOptionRow option = mapper.selectProductOptionByOfferId(
                ownerUserId,
                storeCode,
                siteCode,
                command.getProductSiteOfferId()
        );
        if (option == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_PRODUCT_OPTION_NOT_FOUND");
        }

        String selfCode = normalizeNoonCode(option.getPskuCode());
        String selfCodeType = resolveNoonCodeType(selfCode);
        if (selfCodeType == null) {
            throw badRequest("COMPETITOR_SELF_CODE_REQUIRED");
        }
        String frontendSelfCode = normalizeNoonCode(command.getSelfNoonProductCode());
        if (StringUtils.hasText(frontendSelfCode) && !selfCode.equals(frontendSelfCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPETITOR_SELF_CODE_MISMATCH");
        }

        CompetitorWatchProductRow existing = mapper.selectWatchProductByProductSiteOfferId(
                ownerUserId,
                storeCode,
                siteCode,
                option.getProductSiteOfferId()
        );
        if (existing != null) {
            return detail(context, existing.getId());
        }

        Long watchProductId = mapper.nextWatchProductId();
        mapper.insertWatchProduct(buildInsertCommand(watchProductId, ownerUserId, context, option, selfCode, selfCodeType));
        return detail(context, watchProductId);
    }

    public CompetitorWatchProductScopeRow requireWatchProductScope(Long watchProductId) {
        if (watchProductId == null) {
            throw badRequest("COMPETITOR_WATCH_PRODUCT_REQUIRED");
        }
        CompetitorWatchProductScopeRow scope = mapper.selectWatchProductScopeById(watchProductId);
        if (scope == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_WATCH_PRODUCT_NOT_FOUND");
        }
        return scope;
    }

    public CompetitorWatchProductDetailView detail(BusinessAccessContext context, Long watchProductId) {
        if (watchProductId == null) {
            throw badRequest("COMPETITOR_WATCH_PRODUCT_REQUIRED");
        }
        Long ownerUserId = context == null ? null : context.getBusinessOwnerUserId();
        CompetitorWatchProductRow watchProduct = mapper.selectWatchProductById(ownerUserId, watchProductId);
        if (watchProduct == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_WATCH_PRODUCT_NOT_FOUND");
        }
        return CompetitorWatchProductDetailView.fromRows(
                watchProduct,
                mapper.listKeywordsByWatchProductId(watchProductId),
                mapper.listProductsByWatchProductId(watchProductId),
                mapper.listKeywordProductRelationsByWatchProductId(watchProductId),
                mapper.listLatestRankPointsByWatchProductId(watchProductId)
        );
    }

    public CompetitorRankHistoryView rankHistory(
            BusinessAccessContext context,
            Long watchProductId,
            Long keywordId,
            Integer rangeDays
    ) {
        CompetitorWatchProductRow watchProduct = requireWatchProduct(context, watchProductId);
        CompetitorKeywordScopeRow keyword = requireKeywordScope(keywordId);
        if (!watchProduct.getId().equals(keyword.getWatchProductId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPETITOR_KEYWORD_SCOPE_MISMATCH");
        }
        int normalizedRangeDays = normalizeHistoryRangeDays(rangeDays);
        LocalDateTime fromTime = LocalDate.now().minusDays(normalizedRangeDays - 1L).atStartOfDay();
        return CompetitorRankHistoryView.fromRows(mapper.listRankHistoryByWatchProductIdAndKeywordId(
                watchProduct.getId(),
                keyword.getKeywordId(),
                fromTime,
                1000
        ));
    }

    public CompetitorProductChangeListView productChanges(
            BusinessAccessContext context,
            Long watchProductId,
            Integer limit
    ) {
        if (productChangeService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "COMPETITOR_PRODUCT_CHANGE_UNAVAILABLE");
        }
        return productChangeService.productChanges(context, watchProductId, limit);
    }

    public CompetitorBrowserObservationResultView applyBrowserObservations(
            BusinessAccessContext context,
            Long keywordId,
            CompetitorBrowserObservationCommand command
    ) {
        CompetitorKeywordScopeRow keyword = requireKeywordScope(keywordId);
        requireStoreInContext(context, keyword.getStoreCode());
        CompetitorKeywordRunRow latestRun = mapper.selectLatestSucceededKeywordRunByKeywordId(keywordId);
        if (latestRun == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPETITOR_KEYWORD_NO_LATEST_RUN");
        }
        CompetitorWatchProductRow watchProduct = mapper.selectWatchProductForRefresh(keyword.getWatchProductId());
        if (watchProduct == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_WATCH_PRODUCT_NOT_FOUND");
        }

        Long actorUserId = actorUserId(context);
        String selfCode = normalizeNoonCode(watchProduct.getSelfNoonProductCode());
        CompetitorBrowserObservationResultView result = new CompetitorBrowserObservationResultView();
        result.setObservedCount(command == null ? 0 : command.getItems().size());
        Map<String, CompetitorBrowserObservationItem> sponsoredItems = normalizeSponsoredObservationItems(command);
        result.setSponsoredObservedCount(sponsoredItems.size());

        for (CompetitorBrowserObservationItem item : sponsoredItems.values()) {
            String noonCode = normalizeNoonCode(item.getNoonProductCode());
            int updatedSearchResultCount = mapper.markSearchResultSponsored(latestRun.getId(), noonCode, actorUserId);
            if (updatedSearchResultCount > 0) {
                result.setSearchResultUpdatedCount(result.getSearchResultUpdatedCount() + updatedSearchResultCount);
            } else {
                mapper.insertSearchResult(buildBrowserObservedSearchResult(latestRun, item, noonCode, actorUserId));
                result.setSearchResultInsertedCount(result.getSearchResultInsertedCount() + 1);
            }

            result.setRankFactUpdatedCount(result.getRankFactUpdatedCount()
                    + mapper.markRankFactSponsored(latestRun.getId(), noonCode, actorUserId));
            if (noonCode.equals(selfCode)) {
                continue;
            }

            CompetitorProductRow product = mapper.selectCompetitorProductByCode(keyword.getWatchProductId(), noonCode);
            Long competitorProductId;
            String relationStatus;
            if (product == null) {
                competitorProductId = mapper.nextCompetitorProductId();
                mapper.insertCompetitorProduct(buildBrowserObservedProductInsert(
                        competitorProductId,
                        keyword.getWatchProductId(),
                        item,
                        noonCode,
                        actorUserId
                ));
                result.setCompetitorInsertedCount(result.getCompetitorInsertedCount() + 1);
                relationStatus = "DISCOVERED";
            } else {
                competitorProductId = product.getId();
                mapper.updateCompetitorProductFromSearch(buildBrowserObservedProductInsert(
                        competitorProductId,
                        keyword.getWatchProductId(),
                        item,
                        noonCode,
                        actorUserId
                ));
                relationStatus = "CONFIRMED".equalsIgnoreCase(nullToEmpty(product.getReviewStatus()))
                        ? "CONFIRMED"
                        : "DISCOVERED";
            }

            mapper.upsertKeywordProductRelationFromSearch(buildBrowserObservedRelation(
                    keywordId,
                    competitorProductId,
                    latestRun.getSearchRunId(),
                    item,
                    relationStatus,
                    actorUserId
            ));
            result.setRelationUpsertedCount(result.getRelationUpsertedCount() + 1);
        }

        return result;
    }

    public CompetitorBrowserObservationResultView applyBrowserObservationsByKeyword(
            BusinessAccessContext context,
            Long watchProductId,
            CompetitorBrowserObservationCommand command
    ) {
        CompetitorWatchProductRow watchProduct = requireWatchProduct(context, watchProductId);
        String keywordNorm = normalizeKeywordNorm(command == null ? null : command.getKeyword());
        CompetitorKeywordRow keyword = mapper.selectKeywordByNorm(watchProduct.getId(), keywordNorm);
        if (keyword == null || !"ACTIVE".equalsIgnoreCase(nullToEmpty(keyword.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPETITOR_KEYWORD_NOT_FOUND");
        }
        return applyBrowserObservations(context, keyword.getId(), command);
    }

    private CompetitorProductOptionView toProductOptionView(CompetitorProductOptionRow row) {
        String noonCode = normalizeNoonCode(row == null ? null : row.getPskuCode());
        String codeType = resolveNoonCodeType(noonCode);
        if (codeType == null) {
            return null;
        }
        return CompetitorProductOptionView.fromRow(row, noonCode, codeType);
    }

    private Map<String, CompetitorBrowserObservationItem> normalizeSponsoredObservationItems(
            CompetitorBrowserObservationCommand command
    ) {
        Map<String, CompetitorBrowserObservationItem> itemsByCode = new LinkedHashMap<>();
        if (command == null || command.getItems() == null) {
            return itemsByCode;
        }
        for (CompetitorBrowserObservationItem item : command.getItems()) {
            if (item == null || !Boolean.TRUE.equals(item.getSponsored())) {
                continue;
            }
            String noonCode = normalizeNoonCode(item.getNoonProductCode());
            if (resolveNoonCodeType(noonCode) == null) {
                continue;
            }
            item.setNoonProductCode(noonCode);
            itemsByCode.putIfAbsent(noonCode, item);
        }
        return itemsByCode;
    }

    private CompetitorSearchResultInsertCommand buildBrowserObservedSearchResult(
            CompetitorKeywordRunRow latestRun,
            CompetitorBrowserObservationItem item,
            String noonCode,
            Long actorUserId
    ) {
        CompetitorSearchResultInsertCommand insert = new CompetitorSearchResultInsertCommand();
        insert.setId(mapper.nextSearchResultId());
        insert.setKeywordRunId(latestRun.getId());
        insert.setResultPosition(normalizeObservedPosition(item.getPosition()));
        insert.setNoonProductCode(noonCode);
        insert.setCodeType(resolveNoonCodeType(noonCode));
        insert.setCanonicalUrl(normalizeText(item.getCanonicalUrl()));
        insert.setTitleSnapshot(normalizeText(item.getTitle()));
        insert.setBrandSnapshot(normalizeText(item.getBrand()));
        insert.setImageUrlSnapshot(normalizeText(item.getImageUrl()));
        insert.setPriceAmount(item.getPriceAmount());
        insert.setCurrencyCode(normalizeText(item.getCurrencyCode()));
        insert.setRating(item.getRating());
        insert.setReviewCount(item.getReviewCount());
        insert.setSponsored(true);
        insert.setRawResultJson(browserObservationRawJson(noonCode));
        insert.setCapturedAt(latestRun.getCapturedAt() == null ? LocalDateTime.now() : latestRun.getCapturedAt());
        insert.setActorUserId(actorUserId);
        return insert;
    }

    private CompetitorProductInsertCommand buildBrowserObservedProductInsert(
            Long productId,
            Long watchProductId,
            CompetitorBrowserObservationItem item,
            String noonCode,
            Long actorUserId
    ) {
        CompetitorProductInsertCommand insert = new CompetitorProductInsertCommand();
        insert.setId(productId);
        insert.setWatchProductId(watchProductId);
        insert.setNoonProductCode(noonCode);
        insert.setCodeType(resolveNoonCodeType(noonCode));
        insert.setCanonicalUrl(normalizeText(item.getCanonicalUrl()));
        insert.setTitleSnapshot(normalizeText(item.getTitle()));
        insert.setBrandSnapshot(normalizeText(item.getBrand()));
        insert.setImageUrlSnapshot(normalizeText(item.getImageUrl()));
        insert.setPriceAmountSnapshot(item.getPriceAmount());
        insert.setCurrencyCodeSnapshot(normalizeText(item.getCurrencyCode()));
        insert.setRatingSnapshot(item.getRating());
        insert.setReviewCountSnapshot(item.getReviewCount());
        insert.setSourceType("SEARCH_DISCOVERY");
        insert.setReviewStatus("PENDING");
        insert.setActorUserId(actorUserId);
        return insert;
    }

    private CompetitorKeywordProductSearchCommand buildBrowserObservedRelation(
            Long keywordId,
            Long competitorProductId,
            Long searchRunId,
            CompetitorBrowserObservationItem item,
            String relationStatus,
            Long actorUserId
    ) {
        CompetitorKeywordProductSearchCommand relation = new CompetitorKeywordProductSearchCommand();
        relation.setId(mapper.nextKeywordProductId());
        relation.setKeywordId(keywordId);
        relation.setCompetitorProductId(competitorProductId);
        relation.setRelationStatus(relationStatus);
        relation.setSearchRunId(searchRunId);
        relation.setRankNo(normalizeObservedPosition(item.getPosition()));
        relation.setSponsored(true);
        relation.setActorUserId(actorUserId);
        return relation;
    }

    private int normalizeObservedPosition(Integer position) {
        if (position == null || position < 1) {
            return 999;
        }
        return Math.min(position, 999);
    }

    private String browserObservationRawJson(String noonCode) {
        return "{\"source\":\"browser-observation\",\"noonProductCode\":\"" + escapeJson(noonCode) + "\"}";
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private CompetitorWatchProductRow requireWatchProduct(BusinessAccessContext context, Long watchProductId) {
        if (watchProductId == null) {
            throw badRequest("COMPETITOR_WATCH_PRODUCT_REQUIRED");
        }
        Long ownerUserId = context == null ? null : context.getBusinessOwnerUserId();
        CompetitorWatchProductRow watchProduct = mapper.selectWatchProductById(ownerUserId, watchProductId);
        if (watchProduct == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_WATCH_PRODUCT_NOT_FOUND");
        }
        requireStoreInContext(context, watchProduct.getStoreCode());
        return watchProduct;
    }

    private CompetitorKeywordScopeRow requireKeywordScope(Long keywordId) {
        if (keywordId == null) {
            throw badRequest("COMPETITOR_KEYWORD_REQUIRED");
        }
        CompetitorKeywordScopeRow scope = mapper.selectKeywordScopeById(keywordId);
        if (scope == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_KEYWORD_NOT_FOUND");
        }
        return scope;
    }

    private CompetitorWatchProductScopeRow toWatchProductScope(CompetitorKeywordScopeRow keyword) {
        CompetitorWatchProductScopeRow scope = new CompetitorWatchProductScopeRow();
        scope.setId(keyword.getWatchProductId());
        scope.setOwnerUserId(keyword.getOwnerUserId());
        scope.setStoreCode(keyword.getStoreCode());
        scope.setSiteCode(keyword.getSiteCode());
        return scope;
    }

    private KeywordCandidateScope requireKeywordCandidateScopeInternal(Long keywordId, Long competitorProductId) {
        CompetitorKeywordScopeRow keyword = requireKeywordScope(keywordId);
        if (competitorProductId == null) {
            throw badRequest("COMPETITOR_PRODUCT_REQUIRED");
        }
        CompetitorProductScopeRow product = mapper.selectCompetitorProductScopeById(competitorProductId);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_PRODUCT_NOT_FOUND");
        }
        if (!keyword.getWatchProductId().equals(product.getWatchProductId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPETITOR_SCOPE_MISMATCH");
        }
        return new KeywordCandidateScope(keyword, product);
    }

    private CompetitorProductInsertCommand buildManualProductInsert(
            Long competitorProductId,
            Long watchProductId,
            ParsedNoonCode parsed,
            CompetitorManualCompetitorCommand command,
            Long actorUserId
    ) {
        CompetitorProductInsertCommand insert = new CompetitorProductInsertCommand();
        insert.setId(competitorProductId);
        insert.setWatchProductId(watchProductId);
        insert.setNoonProductCode(parsed.noonCode);
        insert.setCodeType(parsed.codeType);
        insert.setCanonicalUrl(normalizeText(firstNonBlank(command == null ? null : command.getCanonicalUrl(), parsed.canonicalUrl)));
        insert.setTitleSnapshot(normalizeText(command == null ? null : command.getTitle()));
        insert.setBrandSnapshot(normalizeText(command == null ? null : command.getBrand()));
        insert.setImageUrlSnapshot(normalizeText(command == null ? null : command.getImageUrl()));
        insert.setSourceType("MANUAL_ADD");
        insert.setReviewStatus("CONFIRMED");
        insert.setActorUserId(actorUserId);
        return insert;
    }

    private void applyCompetitorToAllActiveKeywords(
            Long watchProductId,
            Long competitorProductId,
            String relationStatus,
            Long actorUserId
    ) {
        List<CompetitorKeywordRow> keywords = mapper.listActiveKeywordsByWatchProductId(watchProductId);
        for (CompetitorKeywordRow keyword : keywords) {
            mapper.upsertKeywordProductRelation(keyword.getId(), competitorProductId, relationStatus, actorUserId);
        }
    }

    private String normalizeKeywordDisplay(String keyword) {
        String normalized = normalizeText(keyword);
        if (normalized == null) {
            throw badRequest("COMPETITOR_KEYWORD_REQUIRED");
        }
        return COLLAPSED_WHITESPACE.matcher(normalized).replaceAll(" ");
    }

    private String normalizeKeywordNorm(String keyword) {
        return normalizeKeywordDisplay(keyword).toLowerCase(Locale.ROOT);
    }

    private ParsedNoonCode parseNoonCode(String input) {
        String normalized = normalizeText(input);
        if (normalized == null) {
            return null;
        }
        Matcher matcher = NOON_CODE_PATTERN.matcher(normalized);
        String code;
        if (matcher.find()) {
            code = matcher.group(2);
        } else {
            code = normalized;
        }
        String noonCode = normalizeNoonCode(code);
        String codeType = resolveNoonCodeType(noonCode);
        if (codeType == null) {
            return null;
        }
        String canonicalUrl = normalized.startsWith("http://") || normalized.startsWith("https://") ? normalized : null;
        return new ParsedNoonCode(noonCode, codeType, canonicalUrl);
    }

    private CompetitorWatchProductInsertCommand buildInsertCommand(
            Long watchProductId,
            Long ownerUserId,
            BusinessAccessContext context,
            CompetitorProductOptionRow option,
            String selfCode,
            String selfCodeType
    ) {
        CompetitorWatchProductInsertCommand command = new CompetitorWatchProductInsertCommand();
        command.setId(watchProductId);
        command.setOwnerUserId(ownerUserId);
        command.setStoreCode(normalizeText(option.getStoreCode()).toUpperCase(Locale.ROOT));
        command.setSiteCode(normalizeText(option.getSiteCode()).toUpperCase(Locale.ROOT));
        command.setLogicalStoreId(option.getLogicalStoreId());
        command.setProductMasterId(option.getProductMasterId());
        command.setProductVariantId(option.getProductVariantId());
        command.setProductSiteOfferId(option.getProductSiteOfferId());
        command.setSkuParent(normalizeText(option.getSkuParent()));
        command.setPartnerSku(requireText(option.getPartnerSku(), "COMPETITOR_PARTNER_SKU_REQUIRED"));
        command.setChildSku(normalizeText(option.getChildSku()));
        command.setSelfNoonProductCode(selfCode);
        command.setSelfCodeType(selfCodeType);
        command.setTitleSnapshot(normalizeText(option.getTitle()));
        command.setBrandSnapshot(normalizeText(option.getBrand()));
        command.setImageUrlSnapshot(normalizeText(option.getImageUrl()));
        command.setProductFulltypeSnapshot(normalizeText(option.getProductFulltype()));
        command.setStatus("ACTIVE");
        command.setActorUserId(context == null ? null : context.getSessionUserId());
        return command;
    }

    private Long ownerUserId(BusinessAccessContext context, String storeCode) {
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COMPETITOR_ACCESS_CONTEXT_REQUIRED");
        }
        if (StringUtils.hasText(storeCode) && !context.canAccessStore(storeCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COMPETITOR_STORE_SCOPE_REQUIRED");
        }
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId != null) {
            return ownerUserId;
        }
        if (context.getBusinessOwnerUserId() != null) {
            return context.getBusinessOwnerUserId();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COMPETITOR_STORE_SCOPE_REQUIRED");
    }

    private Long ownerUserIdForQuery(BusinessAccessContext context, String storeCode) {
        if (StringUtils.hasText(storeCode)) {
            return ownerUserId(context, storeCode);
        }
        if (context == null || context.getBusinessOwnerUserId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COMPETITOR_STORE_SCOPE_REQUIRED");
        }
        return context.getBusinessOwnerUserId();
    }

    private List<String> scopedStoreCodes(BusinessAccessContext context, String storeCode) {
        if (context == null) {
            return List.of();
        }
        if (StringUtils.hasText(storeCode)) {
            return List.of(storeCode);
        }
        return new ArrayList<>(context.getStoreCodes());
    }

    private int normalizeOptionLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 20;
        }
        return Math.min(limit, 50);
    }

    private int normalizeHistoryRangeDays(Integer rangeDays) {
        if (rangeDays == null || rangeDays < 1) {
            return 30;
        }
        return Math.min(rangeDays, 365);
    }

    private Long actorUserId(BusinessAccessContext context) {
        return context == null ? null : context.getSessionUserId();
    }

    private void requireStoreInContext(BusinessAccessContext context, String storeCode) {
        if (context == null || !context.canAccessStore(storeCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COMPETITOR_STORE_SCOPE_REQUIRED");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeText(status);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String resolveNoonCodeType(String noonCode) {
        if (!StringUtils.hasText(noonCode)) {
            return null;
        }
        char prefix = noonCode.charAt(0);
        if (prefix == 'Z') {
            return "Z_CODE";
        }
        if (prefix == 'N') {
            return "N_CODE";
        }
        return null;
    }

    private String normalizeNoonCode(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String errorCode) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw badRequest(errorCode);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static class ParsedNoonCode {
        private final String noonCode;
        private final String codeType;
        private final String canonicalUrl;

        private ParsedNoonCode(String noonCode, String codeType, String canonicalUrl) {
            this.noonCode = noonCode;
            this.codeType = codeType;
            this.canonicalUrl = canonicalUrl;
        }
    }

    private static class KeywordCandidateScope {
        private final CompetitorKeywordScopeRow keyword;
        private final CompetitorProductScopeRow product;

        private KeywordCandidateScope(CompetitorKeywordScopeRow keyword, CompetitorProductScopeRow product) {
            this.keyword = keyword;
            this.product = product;
        }

        private CompetitorWatchProductScopeRow toWatchProductScope() {
            CompetitorWatchProductScopeRow scope = new CompetitorWatchProductScopeRow();
            scope.setId(keyword.getWatchProductId());
            scope.setOwnerUserId(keyword.getOwnerUserId());
            scope.setStoreCode(keyword.getStoreCode());
            scope.setSiteCode(keyword.getSiteCode());
            return scope;
        }
    }
}
