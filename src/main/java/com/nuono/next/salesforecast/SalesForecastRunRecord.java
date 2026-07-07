package com.nuono.next.salesforecast;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class SalesForecastRunRecord {

    private final Long id;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate sourceDataDate;
    private final String calculationVersion;
    private final String configVersion;
    private final String calendarVersionNo;
    private final String calendarVersionName;
    private final String calendarVersionSourceLabel;
    private final String lifecycleVersionNo;
    private final String lifecycleVersionName;
    private final String lifecycleVersionSourceLabel;
    private final String status;
    private final int resultCount;
    private final LocalDateTime calculatedAt;

    public SalesForecastRunRecord(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate sourceDataDate,
            String calculationVersion,
            String configVersion,
            String status,
            int resultCount,
            LocalDateTime calculatedAt
    ) {
        this(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                sourceDataDate,
                calculationVersion,
                configVersion,
                null,
                null,
                null,
                null,
                null,
                null,
                status,
                resultCount,
                calculatedAt
        );
    }

    public SalesForecastRunRecord(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate sourceDataDate,
            String calculationVersion,
            String configVersion,
            String calendarVersionNo,
            String calendarVersionName,
            String calendarVersionSourceLabel,
            String lifecycleVersionNo,
            String lifecycleVersionName,
            String lifecycleVersionSourceLabel,
            String status,
            int resultCount,
            LocalDateTime calculatedAt
    ) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.sourceDataDate = sourceDataDate;
        this.calculationVersion = calculationVersion;
        this.configVersion = configVersion;
        this.calendarVersionNo = calendarVersionNo;
        this.calendarVersionName = calendarVersionName;
        this.calendarVersionSourceLabel = calendarVersionSourceLabel;
        this.lifecycleVersionNo = lifecycleVersionNo;
        this.lifecycleVersionName = lifecycleVersionName;
        this.lifecycleVersionSourceLabel = lifecycleVersionSourceLabel;
        this.status = status;
        this.resultCount = resultCount;
        this.calculatedAt = calculatedAt;
    }

    public static SalesForecastRunRecord succeeded(
            SalesForecastQuery query,
            LocalDate sourceDataDate,
            String calculationVersion,
            String configVersion,
            int resultCount
    ) {
        return succeeded(
                query,
                sourceDataDate,
                calculationVersion,
                configVersion,
                null,
                null,
                null,
                null,
                null,
                null,
                resultCount
        );
    }

    public static SalesForecastRunRecord succeeded(
            SalesForecastQuery query,
            LocalDate sourceDataDate,
            String calculationVersion,
            String configVersion,
            String calendarVersionNo,
            String calendarVersionName,
            String calendarVersionSourceLabel,
            int resultCount
    ) {
        return succeeded(
                query,
                sourceDataDate,
                calculationVersion,
                configVersion,
                calendarVersionNo,
                calendarVersionName,
                calendarVersionSourceLabel,
                null,
                null,
                null,
                resultCount
        );
    }

    public static SalesForecastRunRecord succeeded(
            SalesForecastQuery query,
            LocalDate sourceDataDate,
            String calculationVersion,
            String configVersion,
            String calendarVersionNo,
            String calendarVersionName,
            String calendarVersionSourceLabel,
            String lifecycleVersionNo,
            String lifecycleVersionName,
            String lifecycleVersionSourceLabel,
            int resultCount
    ) {
        return new SalesForecastRunRecord(
                null,
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                sourceDataDate,
                calculationVersion,
                configVersion,
                calendarVersionNo,
                calendarVersionName,
                calendarVersionSourceLabel,
                lifecycleVersionNo,
                lifecycleVersionName,
                lifecycleVersionSourceLabel,
                "succeeded",
                resultCount,
                null
        );
    }

    public SalesForecastRunRecord withIdAndCalculatedAt(Long id, LocalDateTime calculatedAt) {
        return new SalesForecastRunRecord(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                sourceDataDate,
                calculationVersion,
                configVersion,
                calendarVersionNo,
                calendarVersionName,
                calendarVersionSourceLabel,
                lifecycleVersionNo,
                lifecycleVersionName,
                lifecycleVersionSourceLabel,
                status,
                resultCount,
                calculatedAt
        );
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public LocalDate getSourceDataDate() {
        return sourceDataDate;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public String getCalendarVersionNo() {
        return calendarVersionNo;
    }

    public String getCalendarVersionName() {
        return calendarVersionName;
    }

    public String getCalendarVersionSourceLabel() {
        return calendarVersionSourceLabel;
    }

    public String getLifecycleVersionNo() {
        return lifecycleVersionNo;
    }

    public String getLifecycleVersionName() {
        return lifecycleVersionName;
    }

    public String getLifecycleVersionSourceLabel() {
        return lifecycleVersionSourceLabel;
    }

    public String getStatus() {
        return status;
    }

    public int getResultCount() {
        return resultCount;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }
}
