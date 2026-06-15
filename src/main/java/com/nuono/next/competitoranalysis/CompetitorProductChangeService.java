package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorProductChangeMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompetitorProductChangeService {

    private final CompetitorProductChangeMapper changeMapper;
    private final CompetitorAnalysisMapper analysisMapper;

    public CompetitorProductChangeService(
            CompetitorProductChangeMapper changeMapper,
            CompetitorAnalysisMapper analysisMapper
    ) {
        this.changeMapper = changeMapper;
        this.analysisMapper = analysisMapper;
    }

    public CompetitorProductChangeListView productChanges(
            BusinessAccessContext context,
            Long watchProductId,
            Integer limit
    ) {
        CompetitorWatchProductRow watchProduct = requireWatchProduct(context, watchProductId);
        return CompetitorProductChangeListView.fromRows(changeMapper.listProductChangeEvents(
                watchProduct.getOwnerUserId(),
                watchProduct.getId(),
                normalizeLimit(limit)
        ));
    }

    private CompetitorWatchProductRow requireWatchProduct(BusinessAccessContext context, Long watchProductId) {
        if (watchProductId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPETITOR_WATCH_PRODUCT_REQUIRED");
        }
        Long ownerUserId = context == null ? null : context.getBusinessOwnerUserId();
        CompetitorWatchProductRow watchProduct = analysisMapper.selectWatchProductById(ownerUserId, watchProductId);
        if (watchProduct == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "COMPETITOR_WATCH_PRODUCT_NOT_FOUND");
        }
        if (context == null || !context.canAccessStore(watchProduct.getStoreCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "COMPETITOR_STORE_SCOPE_REQUIRED");
        }
        return watchProduct;
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit < 1 ? 100 : Math.min(limit, 300);
    }
}
