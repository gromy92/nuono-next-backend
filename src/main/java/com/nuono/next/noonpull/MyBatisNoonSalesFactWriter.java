package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.NoonSalesFactMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class MyBatisNoonSalesFactWriter implements NoonSalesFactWriter {
    private final NoonSalesFactMapper mapper;
    private final ProductManagementMapper productManagementMapper;
    private final AtomicBoolean schemaEnsured = new AtomicBoolean(false);

    public MyBatisNoonSalesFactWriter(NoonSalesFactMapper mapper) {
        this(mapper, (ProductManagementMapper) null);
    }

    @Autowired
    public MyBatisNoonSalesFactWriter(
            NoonSalesFactMapper mapper,
            ObjectProvider<ProductManagementMapper> productManagementMapperProvider
    ) {
        this(mapper, productManagementMapperProvider.getIfAvailable());
    }

    public MyBatisNoonSalesFactWriter(NoonSalesFactMapper mapper, ProductManagementMapper productManagementMapper) {
        this.mapper = mapper;
        this.productManagementMapper = productManagementMapper;
    }

    @Override
    public void upsert(NoonSalesDailyFact fact) {
        ensureSchema();
        Long id = mapper.nextDailySalesFactId();
        mapper.upsertDailySalesFact(id, fact);
        refreshListingStartedAt(fact);
    }

    private void ensureSchema() {
        if (!schemaEnsured.compareAndSet(false, true)) {
            return;
        }
        mapper.ensureSalesDataIdSequence();
        mapper.ensureDailySalesFactSequence();
        mapper.ensureDailySalesFactTable();
    }

    private void refreshListingStartedAt(NoonSalesDailyFact fact) {
        if (productManagementMapper == null || fact == null) {
            return;
        }
        if (fact.getOwnerUserId() == null || isBlank(fact.getStoreCode()) || isBlank(fact.getSiteCode())) {
            return;
        }
        if (isBlank(fact.getSkuParent()) && isBlank(fact.getSku())) {
            return;
        }
        productManagementMapper.refreshProductSiteOfferListingStartedAtBySalesFact(
                fact.getOwnerUserId(),
                fact.getStoreCode(),
                fact.getSiteCode(),
                fact.getSkuParent(),
                fact.getSku(),
                LocalDateTime.now(),
                fact.getOwnerUserId()
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
