package com.nuono.next.sales;

import com.nuono.next.infrastructure.mapper.ProductLifecycleCalculationMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class MyBatisProductLifecycleCalculationSource implements ProductLifecycleCalculationSource {

    private final ProductLifecycleCalculationMapper mapper;
    private final SalesFactRepository salesFactRepository;
    private final SalesActivityWindowRepository activityWindowRepository;

    public MyBatisProductLifecycleCalculationSource(
            ProductLifecycleCalculationMapper mapper,
            SalesFactRepository salesFactRepository,
            SalesActivityWindowRepository activityWindowRepository
    ) {
        this.mapper = mapper;
        this.salesFactRepository = salesFactRepository;
        this.activityWindowRepository = activityWindowRepository;
    }

    @Override
    public List<ProductLifecycleCalculationScope> listScheduledScopes(LocalDate anchorDate) {
        return mapper.selectScheduledScopes().stream()
                .map(row -> new ProductLifecycleCalculationScope(
                        row.getOwnerUserId(),
                        row.getStoreCode(),
                        row.getSiteCode(),
                        anchorDate,
                        ProductLifecycleResult.DEFAULT_RULE_VERSION,
                        false
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductLifecycleStateQuery> listProductScopes(ProductLifecycleCalculationScope scope) {
        return mapper.selectProductScopes(scope).stream()
                .map(row -> new ProductLifecycleStateQuery(
                        row.getOwnerUserId(),
                        row.getStoreCode(),
                        row.getSiteCode(),
                        row.getPartnerSku(),
                        row.getSku()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public ProductLifecycleListingSignals loadListingSignals(ProductLifecycleStateQuery query, LocalDate analysisDate) {
        ProductLifecycleListingSignalRow row = mapper.selectListingSignals(query);
        if (row == null) {
            return new ProductLifecycleListingSignals(query, null, null, null, null, analysisDate, 0, 0, 0, 0);
        }
        return new ProductLifecycleListingSignals(
                query,
                row.getOfficialListingDate(),
                row.getEarliestInventoryDate(),
                row.getEarliestPvDate(),
                row.getEarliestSalesDate(),
                row.getProductPulledDate(),
                row.getHistoricalSignalDays(),
                row.getSalesSignalDays(),
                row.getPvSignalDays(),
                row.getInventorySignalDays()
        );
    }

    @Override
    public List<DailySalesFact> loadFacts(ProductLifecycleStateQuery query, LocalDate from, LocalDate to) {
        return salesFactRepository.list(new SalesFactQuery(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                from,
                to,
                query.getPartnerSku(),
                query.getSku()
        ));
    }

    @Override
    public List<SalesActivityWindowRecord> loadActivityWindows(
            ProductLifecycleStateQuery query,
            LocalDate from,
            LocalDate to
    ) {
        return activityWindowRepository.listActive(new SalesActivityWindowScope(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                from,
                to
        ));
    }

    @Override
    public boolean isStockoutDistorted(ProductLifecycleStateQuery query, ProductLifecycleFeatureSnapshot features) {
        Integer stock = mapper.selectCurrentStock(query);
        if (stock == null || stock > 0 || features == null) {
            return false;
        }
        int recent7Sales = features.getRecent7() == null ? 0 : features.getRecent7().getSalesUnits();
        int recent30Sales = features.getRecent30() == null ? 0 : features.getRecent30().getSalesUnits();
        int previous30Sales = features.getPrevious30() == null ? 0 : features.getPrevious30().getSalesUnits();
        return recent7Sales <= 0 && (recent30Sales > 0 || previous30Sales > 0);
    }
}
