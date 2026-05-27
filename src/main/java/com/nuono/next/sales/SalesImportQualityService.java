package com.nuono.next.sales;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesImportQualityService {

    private final SalesFactRepository salesFactRepository;

    public SalesImportQualityService(SalesFactRepository salesFactRepository) {
        this.salesFactRepository = salesFactRepository;
    }

    public SalesImportBatchListView listBatches(SalesImportBatchQuery query) {
        return new SalesImportBatchListView(salesFactRepository.listImportBatches(query));
    }

    public SalesImportBatchDetail getBatchDetail(Long batchId) {
        SalesImportBatchRecord batch = salesFactRepository.findImportBatch(batchId);
        if (batch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "销量导入批次不存在。");
        }
        return new SalesImportBatchDetail(batch, salesFactRepository.listImportExceptions(batchId));
    }
}
