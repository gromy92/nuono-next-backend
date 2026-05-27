package com.nuono.next.sales;

import com.nuono.next.infrastructure.mapper.NoonOrderFactMapper;
import com.nuono.next.infrastructure.mapper.NoonOrderPriceTrendBucketRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSalesPriceTrendRepository implements SalesPriceTrendRepository {

    private final NoonOrderFactMapper mapper;

    public MyBatisSalesPriceTrendRepository(NoonOrderFactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SalesPriceTrendResult getPriceTrend(SalesFactQuery query, String granularity) {
        if (!queryHasProductScope(query)) {
            return SalesPriceTrendResult.empty();
        }
        LocalDateTime dateFromStart = query.getDateFrom().atStartOfDay();
        LocalDateTime dateToExclusive = query.getDateTo().plusDays(1).atStartOfDay();
        int candidateRows = mapper.countPriceTrendCandidateRows(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                query.getPartnerSku(),
                query.getSku(),
                dateFromStart,
                dateToExclusive,
                query.getDateFrom(),
                query.getDateTo()
        );
        List<NoonOrderPriceTrendBucketRow> rows = mapper.selectPriceTrendBuckets(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                query.getPartnerSku(),
                query.getSku(),
                dateFromStart,
                dateToExclusive
        );
        if (rows == null || rows.isEmpty()) {
            return candidateRows > 0 ? SalesPriceTrendResult.invalidOrderPriceFacts() : SalesPriceTrendResult.empty();
        }
        Set<String> currencies = rows.stream()
                .map(NoonOrderPriceTrendBucketRow::getCurrencyCode)
                .map(this::normalizeCurrency)
                .filter(this::hasText)
                .collect(Collectors.toSet());
        if (currencies.size() > 1) {
            return SalesPriceTrendResult.mixedCurrency();
        }
        if (currencies.isEmpty()) {
            return SalesPriceTrendResult.invalidOrderPriceFacts();
        }
        List<SalesPriceTrendBucket> buckets = rows.stream()
                .map(row -> new SalesPriceTrendBucket(
                        row.getBucketStart(),
                        bucketLabel(row.getBucketStart(), granularity),
                        row.getAvgOfferPrice(),
                        row.getMinOfferPrice(),
                        row.getMaxOfferPrice(),
                        row.getOrderLineCount(),
                        row.getCurrencyCode()
                ))
                .collect(Collectors.toList());
        return SalesPriceTrendResult.ready(buckets);
    }

    private boolean queryHasProductScope(SalesFactQuery query) {
        return query != null
                && query.getOwnerUserId() != null
                && hasText(query.getStoreCode())
                && hasText(query.getSiteCode())
                && query.getDateFrom() != null
                && query.getDateTo() != null
                && hasText(query.getPartnerSku())
                && hasText(query.getSku());
    }

    private String bucketLabel(LocalDate bucketStart, String granularity) {
        return bucketStart == null ? "" : bucketStart.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeCurrency(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
