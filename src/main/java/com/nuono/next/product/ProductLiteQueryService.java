package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductLiteMapper;
import com.nuono.next.permission.access.BusinessStoreAccess;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductLiteQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final ProductLiteMapper mapper;

    public ProductLiteQueryService(ProductLiteMapper mapper) {
        this.mapper = mapper;
    }

    public List<ProductLiteView> search(BusinessStoreAccess storeAccess, ProductLiteQuery query) {
        if (storeAccess == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PRODUCT_LITE_STORE_SCOPE_REQUIRED");
        }
        ProductLiteQuery safeQuery = query == null ? new ProductLiteQuery() : query;
        String siteCode = normalizeUpper(safeQuery.getSiteCode());
        String titleKeyword = normalizeText(safeQuery.getTitleKeyword());
        int limit = normalizeLimit(safeQuery.getLimit());
        return mapper.search(
                        storeAccess.getOwnerUserId(),
                        storeAccess.getStoreCode(),
                        siteCode,
                        titleKeyword,
                        limit
                )
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    private ProductLiteView toView(ProductLiteRecord record) {
        ProductLiteView view = new ProductLiteView();
        if (record == null) {
            return view;
        }
        view.setProductMasterId(record.getProductMasterId());
        view.setStoreCode(record.getStoreCode());
        view.setSiteCode(record.getSiteCode());
        view.setTitle(record.getTitle());
        view.setTitleCn(record.getTitleCn());
        view.setTitleEn(record.getTitleEn());
        view.setBrand(record.getBrand());
        view.setImageUrl(record.getImageUrl());
        view.setProductFulltype(record.getProductFulltype());
        view.setSourceType(record.getSourceType());
        return view;
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
