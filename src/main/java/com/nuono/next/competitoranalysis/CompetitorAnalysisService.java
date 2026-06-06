package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompetitorAnalysisService {

    private final CompetitorAnalysisMapper mapper;

    public CompetitorAnalysisService(CompetitorAnalysisMapper mapper) {
        this.mapper = mapper;
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

    private CompetitorProductOptionView toProductOptionView(CompetitorProductOptionRow row) {
        String noonCode = normalizeNoonCode(row == null ? null : row.getPskuCode());
        String codeType = resolveNoonCodeType(noonCode);
        if (codeType == null) {
            return null;
        }
        return CompetitorProductOptionView.fromRow(row, noonCode, codeType);
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
}
