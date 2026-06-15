package com.nuono.next.sales;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSalesFactRepository implements SalesFactRepository {

    private final SalesDataMapper mapper;
    private final ProductManagementMapper productManagementMapper;

    public MyBatisSalesFactRepository(SalesDataMapper mapper) {
        this(mapper, (ProductManagementMapper) null);
    }

    @Autowired
    public MyBatisSalesFactRepository(
            SalesDataMapper mapper,
            ObjectProvider<ProductManagementMapper> productManagementMapperProvider
    ) {
        this(mapper, productManagementMapperProvider.getIfAvailable());
    }

    public MyBatisSalesFactRepository(SalesDataMapper mapper, ProductManagementMapper productManagementMapper) {
        this.mapper = mapper;
        this.productManagementMapper = productManagementMapper;
    }

    @Override
    public long saveBatch(SalesImportBatch batch) {
        Long id = mapper.nextSalesImportBatchId();
        mapper.insertImportBatch(id, batch);
        return id;
    }

    @Override
    public void upsert(DailySalesFact fact) {
        Long id = mapper.nextDailySalesFactId();
        mapper.upsertDailySalesFact(id, fact);
        refreshListingStartedAt(fact);
    }

    @Override
    public void saveExceptions(long sourceBatchId, List<SalesImportExceptionRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (SalesImportExceptionRecord record : records) {
            Long id = mapper.nextSalesImportExceptionId();
            mapper.insertSalesImportException(id, record);
        }
    }

    @Override
    public List<DailySalesFact> list(SalesFactQuery query) {
        return mapper.selectDailySalesFacts(query);
    }

    @Override
    public LocalDate findLatestFactDate(Long ownerUserId, String storeCode, String siteCode) {
        return mapper.selectLatestDailySalesFactDate(ownerUserId, storeCode, siteCode);
    }

    @Override
    public List<SalesImportBatchRecord> listImportBatches(SalesImportBatchQuery query) {
        return mapper.selectSalesImportBatches(query);
    }

    @Override
    public SalesImportBatchRecord findImportBatch(Long batchId) {
        return mapper.selectSalesImportBatchById(batchId);
    }

    @Override
    public List<SalesImportExceptionRecord> listImportExceptions(Long batchId) {
        return mapper.selectSalesImportExceptions(batchId);
    }

    @Override
    public void markSiteOffersNotListedForEmptyReport(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long updatedBy
    ) {
        if (productManagementMapper == null) {
            return;
        }
        productManagementMapper.markSiteProductOffersNotListedForEmptySalesReport(
                ownerUserId,
                storeCode,
                siteCode,
                updatedBy
        );
    }

    private void refreshListingStartedAt(DailySalesFact fact) {
        if (productManagementMapper == null || fact == null) {
            return;
        }
        if (fact.getOwnerUserId() == null || isBlank(fact.getStoreCode()) || isBlank(fact.getSiteCode())) {
            return;
        }
        if (isBlank(fact.getPartnerSku()) && isBlank(fact.getSku())) {
            return;
        }
        productManagementMapper.refreshProductSiteOfferListingStartedAtBySalesFact(
                fact.getOwnerUserId(),
                fact.getStoreCode(),
                fact.getSiteCode(),
                fact.getPartnerSku(),
                fact.getSku(),
                LocalDateTime.now(),
                fact.getOwnerUserId()
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
