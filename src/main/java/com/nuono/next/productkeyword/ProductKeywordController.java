package com.nuono.next.productkeyword;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-keywords")
public class ProductKeywordController {
    private final ProductKeywordService service;
    private final BusinessAccessResolver accessResolver;

    public ProductKeywordController(
            ProductKeywordService service,
            BusinessAccessResolver accessResolver
    ) {
        this.service = service;
        this.accessResolver = accessResolver;
    }

    @GetMapping
    public ProductKeywordViews.KeywordListView list(
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            @RequestParam(value = "partnerSku", required = false) String partnerSku,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request
    ) {
        ProductKeywordListQuery query = new ProductKeywordListQuery();
        query.setStoreCode(requireStoreCode(storeCode));
        query.setSiteCode(requireSiteCode(siteCode));
        query.setPartnerSku(trimToNull(partnerSku));
        query.setKeywordNorm(normalizeSearch(keyword));
        query.setStatus(normalizeOptionalUpper(status));
        query.setLimit(limit);
        BusinessAccessContext context = requireStoreAccess(request, query.getStoreCode());
        try {
            return service.listKeywords(context, query);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/products/{partnerSku}")
    public ProductKeywordViews.ProductKeywordPanelView productKeywords(
            @PathVariable String partnerSku,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode,
            HttpServletRequest request
    ) {
        String normalizedStoreCode = requireStoreCode(storeCode);
        String normalizedSiteCode = requireSiteCode(siteCode);
        String normalizedPartnerSku = requirePartnerSku(partnerSku);
        BusinessAccessContext context = requireStoreAccess(request, normalizedStoreCode);
        try {
            return service.productKeywords(context, normalizedStoreCode, normalizedSiteCode, normalizedPartnerSku);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping
    public ProductKeywordViews.KeywordItemView create(
            @RequestBody(required = false) ProductKeywordCommand body,
            HttpServletRequest request
    ) {
        ProductKeywordCommand command = requireCommand(body);
        command.setStoreCode(requireStoreCode(command.getStoreCode()));
        command.setSiteCode(requireSiteCode(command.getSiteCode()));
        command.setPartnerSku(requirePartnerSku(command.getPartnerSku()));
        BusinessAccessContext context = requireStoreAccess(request, command.getStoreCode());
        try {
            return ProductKeywordViews.keyword(service.addManualKeyword(context, command));
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PatchMapping("/{keywordId}")
    public ProductKeywordViews.KeywordItemView update(
            @PathVariable Long keywordId,
            @RequestBody(required = false) ProductKeywordCommand body,
            HttpServletRequest request
    ) {
        ProductKeywordCommand command = requireCommand(body);
        command.setStoreCode(requireStoreCode(command.getStoreCode()));
        command.setSiteCode(requireSiteCode(command.getSiteCode()));
        command.setPartnerSku(requirePartnerSku(command.getPartnerSku()));
        BusinessAccessContext context = requireStoreAccess(request, command.getStoreCode());
        try {
            return ProductKeywordViews.keyword(service.updateKeyword(context, keywordId, command));
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/rebuild-index")
    public ProductKeywordViews.RebuildIndexResultView rebuildIndex(
            @RequestBody(required = false) ProductKeywordListQuery body,
            HttpServletRequest request
    ) {
        ProductKeywordListQuery query = body == null ? new ProductKeywordListQuery() : body;
        query.setStoreCode(requireStoreCode(query.getStoreCode()));
        query.setSiteCode(requireSiteCode(query.getSiteCode()));
        query.setPartnerSku(trimToNull(query.getPartnerSku()));
        BusinessAccessContext context = requireStoreAccess(request, query.getStoreCode());
        try {
            return service.rebuildIndex(context, query);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private BusinessAccessContext requireStoreAccess(HttpServletRequest request, String storeCode) {
        return accessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_KEYWORD_MANAGEMENT,
                storeCode
        );
    }

    private ProductKeywordCommand requireCommand(ProductKeywordCommand command) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空。");
        }
        return command;
    }

    private String requireStoreCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "店铺编码不能为空。");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String requireSiteCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点不能为空。");
        }
        String siteCode = raw.trim();
        return "*".equals(siteCode) ? "*" : siteCode.toUpperCase(Locale.ROOT);
    }

    private String requirePartnerSku(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PSKU 不能为空。");
        }
        return raw.trim();
    }

    private String normalizeSearch(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalUpper(String raw) {
        return StringUtils.hasText(raw) ? raw.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String trimToNull(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private ResponseStatusException badRequest(Exception exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
}
