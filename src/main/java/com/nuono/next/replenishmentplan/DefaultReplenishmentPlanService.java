package com.nuono.next.replenishmentplan;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.InboundBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.MissingEtaBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanInput;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanItemView;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanOverviewView;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanQuery;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.StockSnapshot;
import com.nuono.next.salesforecast.SalesForecastDetailView;
import com.nuono.next.salesforecast.SalesForecastDailyForecastView;
import com.nuono.next.salesforecast.SalesForecastFactorBreakdownView;
import com.nuono.next.salesforecast.SalesForecastFeatureValuesView;
import com.nuono.next.salesforecast.SalesForecastOverviewRow;
import com.nuono.next.salesforecast.SalesForecastOverviewView;
import com.nuono.next.salesforecast.SalesForecastQuery;
import com.nuono.next.salesforecast.SalesForecastResultRecord;
import com.nuono.next.salesforecast.SalesForecastRunRecord;
import com.nuono.next.salesforecast.SalesForecastRunRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultReplenishmentPlanService implements ReplenishmentPlanService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final SalesForecastRunRepository forecastRunRepository;
    private final ReplenishmentPlanRepository repository;
    private final ReplenishmentPlanConfigResolver configResolver;
    private final ReplenishmentPlanCalculator calculator;
    private final Clock clock;

    @Autowired
    public DefaultReplenishmentPlanService(
            SalesForecastRunRepository forecastRunRepository,
            ReplenishmentPlanRepository repository,
            ReplenishmentPlanConfigResolver configResolver
    ) {
        this(
                forecastRunRepository,
                repository,
                configResolver,
                new ReplenishmentPlanCalculator(),
                Clock.system(BUSINESS_ZONE)
        );
    }

    DefaultReplenishmentPlanService(
            SalesForecastRunRepository forecastRunRepository,
            ReplenishmentPlanRepository repository,
            ReplenishmentPlanConfigResolver configResolver,
            ReplenishmentPlanCalculator calculator,
            Clock clock
    ) {
        this.forecastRunRepository = forecastRunRepository;
        this.repository = repository;
        this.configResolver = configResolver;
        this.calculator = calculator;
        this.clock = clock == null ? Clock.system(BUSINESS_ZONE) : clock.withZone(BUSINESS_ZONE);
    }

    @Override
    public PlanOverviewView getOverview(PlanQuery query) {
        ReplenishmentPlanConfig config = configResolver.resolve(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        );
        SalesForecastQuery forecastQuery = new SalesForecastQuery(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        );
        SalesForecastRunRecord run = forecastRunRepository.findLatestCompleted(forecastQuery);
        if (run == null) {
            return emptyOverview(query, config, LocalDate.now(clock));
        }

        LocalDate anchorDate = anchorDate(run);
        LocalDate planDate = LocalDate.now(clock);
        List<SalesForecastResultRecord> forecastResults = forecastRunRepository.listResults(run.getId());
        if (forecastResults == null || forecastResults.isEmpty()) {
            return emptyOverview(query, config, anchorDate);
        }
        SalesForecastOverviewView forecast = SalesForecastOverviewView.ready(
                query.getStoreCode(),
                query.getSiteCode(),
                run,
                forecastResults
        );
        List<SalesForecastOverviewRow> forecastRows = forecast.getRows().stream()
                .filter(row -> row != null && hasText(row.getPartnerSku()))
                .collect(Collectors.toList());
        if (forecastRows.isEmpty()) {
            return emptyOverview(query, config, anchorDate);
        }
        Map<String, SalesForecastResultRecord> forecastResultByPartnerSku = forecastResults.stream()
                .filter(record -> record != null && hasText(record.getPartnerSku()))
                .collect(Collectors.toMap(
                        record -> skuKey(record.getPartnerSku()),
                        record -> record,
                        (left, right) -> left
                ));

        Map<String, ReplenishmentPlanRepository.StockRow> stockByPartnerSku = listStockRows(query).stream()
                .filter(row -> row != null && hasText(row.getPartnerSku()))
                .collect(Collectors.toMap(
                        row -> skuKey(row.getPartnerSku()),
                        row -> row,
                        (left, right) -> left
                ));
        Map<String, List<ReplenishmentPlanRepository.InboundRow>> inboundByPartnerSku = listInboundRows(query).stream()
                .filter(row -> row != null && hasText(row.getPartnerSku()))
                .collect(Collectors.groupingBy(row -> skuKey(row.getPartnerSku())));

        List<PlanItemView> rows = new ArrayList<>();
        for (SalesForecastOverviewRow forecastRow : forecastRows) {
            String partnerSkuKey = skuKey(forecastRow.getPartnerSku());
            List<ReplenishmentPlanRepository.InboundRow> inboundRows =
                    inboundByPartnerSku.getOrDefault(partnerSkuKey, List.of());
            rows.add(calculator.calculate(new PlanInput(
                    forecastRow.getPartnerSku(),
                    forecastRow.getSku(),
                    forecastRow.getProductTitle(),
                    stockImageUrl(stockByPartnerSku.get(partnerSkuKey)),
                    stockListingAt(stockByPartnerSku.get(partnerSkuKey)),
                    forecastRow.getLatestFactDate(),
                    observedDays(forecastRow),
                    forecastRow.getHistoryUnits7(),
                    forecastRow.getHistoryUnits30(),
                    forecastRow.getHistoryUnits60(),
                    forecastRow.getHistoryUnits90(),
                    adjustedHistoryUnits(forecastRow, 7),
                    adjustedHistoryUnits(forecastRow, 30),
                    adjustedHistoryUnits(forecastRow, 60),
                    adjustedHistoryUnits(forecastRow, 90),
                    forecastRow.getForecastUnits30(),
                    forecastRow.getForecastUnits60(),
                    forecastRow.getForecastUnits90(),
                    forecastRow.getConfidenceLabel(),
                    forecastRow.getShortReason(),
                    anchorDate,
                    planDate,
                    stockSnapshot(stockByPartnerSku.get(partnerSkuKey)),
                    dailyDemandByDay(forecastResultByPartnerSku.get(partnerSkuKey), config, anchorDate, planDate),
                    knownInboundBatches(inboundRows),
                    missingEtaBatches(inboundRows),
                    hasUnresolvedInboundSite(inboundRows)
            ), config));
        }

        return new PlanOverviewView(
                "ready",
                query.getStoreCode(),
                query.getSiteCode(),
                ReplenishmentPlanConfig.CALCULATION_VERSION,
                config,
                anchorDate,
                rows
        );
    }

    private PlanOverviewView emptyOverview(PlanQuery query, ReplenishmentPlanConfig config, LocalDate anchorDate) {
        return new PlanOverviewView(
                "empty",
                query.getStoreCode(),
                query.getSiteCode(),
                ReplenishmentPlanConfig.CALCULATION_VERSION,
                config,
                anchorDate,
                List.of()
        );
    }

    private LocalDate anchorDate(SalesForecastRunRecord run) {
        if (run != null && run.getSourceDataDate() != null) {
            return run.getSourceDataDate();
        }
        return LocalDate.now(clock);
    }

    private static int observedDays(SalesForecastOverviewRow row) {
        SalesForecastDetailView detail = row == null ? null : row.getDetail();
        if (detail == null || detail.getFeatureValues() == null) {
            return 0;
        }
        return detail.getFeatureValues().getObservedDays();
    }

    private static BigDecimal adjustedHistoryUnits(SalesForecastOverviewRow row, int windowDays) {
        SalesForecastDetailView detail = row == null ? null : row.getDetail();
        SalesForecastFeatureValuesView featureValues = detail == null ? null : detail.getFeatureValues();
        if (featureValues == null) {
            return BigDecimal.valueOf(rawHistoryUnits(row, windowDays));
        }
        if (windowDays == 7) {
            return featureValues.getAdjustedHistoryUnits7();
        }
        if (windowDays == 30) {
            return featureValues.getAdjustedHistoryUnits30();
        }
        if (windowDays == 60) {
            return featureValues.getAdjustedHistoryUnits60();
        }
        if (windowDays == 90) {
            return featureValues.getAdjustedHistoryUnits90();
        }
        return BigDecimal.valueOf(rawHistoryUnits(row, windowDays));
    }

    private static int rawHistoryUnits(SalesForecastOverviewRow row, int windowDays) {
        if (row == null) {
            return 0;
        }
        if (windowDays == 7) {
            return row.getHistoryUnits7();
        }
        if (windowDays == 30) {
            return row.getHistoryUnits30();
        }
        if (windowDays == 60) {
            return row.getHistoryUnits60();
        }
        if (windowDays == 90) {
            return row.getHistoryUnits90();
        }
        return 0;
    }

    private List<ReplenishmentPlanRepository.StockRow> listStockRows(PlanQuery query) {
        List<ReplenishmentPlanRepository.StockRow> rows = repository.listFbnSupermallStock(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        );
        return rows == null ? List.of() : rows;
    }

    private List<ReplenishmentPlanRepository.InboundRow> listInboundRows(PlanQuery query) {
        List<ReplenishmentPlanRepository.InboundRow> rows = repository.listActiveInbound(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        );
        return rows == null ? List.of() : rows;
    }

    private static StockSnapshot stockSnapshot(ReplenishmentPlanRepository.StockRow row) {
        if (row == null) {
            return new StockSnapshot(null, null, null);
        }
        BigDecimal fbnStockUnits = row.getFbnStockUnits();
        BigDecimal supermallStockUnits = row.getSupermallStockUnits();
        BigDecimal currentStockUnits = fbnStockUnits;
        boolean currentStockFactMissing = fbnStockUnits == null;
        return new StockSnapshot(
                currentStockUnits,
                fbnStockUnits,
                supermallStockUnits,
                currentStockFactMissing
        );
    }

    private static String stockImageUrl(ReplenishmentPlanRepository.StockRow row) {
        return row == null ? null : row.getImageUrl();
    }

    private static LocalDate stockListingAt(ReplenishmentPlanRepository.StockRow row) {
        return row == null ? null : row.getListingAt();
    }

    private static List<InboundBatch> knownInboundBatches(List<ReplenishmentPlanRepository.InboundRow> rows) {
        List<InboundBatch> batches = new ArrayList<>();
        for (ReplenishmentPlanRepository.InboundRow row : rows) {
            if (row == null || !row.isScopeResolved() || row.getEtaDate() == null) {
                continue;
            }
            batches.add(new InboundBatch(
                    row.getBatchId(),
                    row.getBatchReferenceNo(),
                    row.getTransportMode(),
                    row.getBatchStatus(),
                    row.getEtaDate(),
                    row.getRemainingQuantity(),
                    row.getDestinationCode()
            ));
        }
        return batches;
    }

    private static List<MissingEtaBatch> missingEtaBatches(List<ReplenishmentPlanRepository.InboundRow> rows) {
        List<MissingEtaBatch> batches = new ArrayList<>();
        for (ReplenishmentPlanRepository.InboundRow row : rows) {
            if (row == null || !row.isScopeResolved() || row.getEtaDate() != null) {
                continue;
            }
            batches.add(new MissingEtaBatch(
                    row.getBatchId(),
                    row.getBatchReferenceNo(),
                    row.getTransportMode(),
                    row.getBatchStatus(),
                    row.getRemainingQuantity(),
                    row.getDestinationCode()
            ));
        }
        return batches;
    }

    private static boolean hasUnresolvedInboundSite(List<ReplenishmentPlanRepository.InboundRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (ReplenishmentPlanRepository.InboundRow row : rows) {
            if (row != null && !row.isScopeResolved()) {
                return true;
            }
        }
        return false;
    }

    private static Map<Integer, BigDecimal> dailyDemandByDay(
            SalesForecastResultRecord result,
            ReplenishmentPlanConfig config,
            LocalDate anchorDate,
            LocalDate planDate
    ) {
        int horizonDays = Math.max(config.getForecastHorizonDays(), config.getSeaLeadDays() + config.getSeaCoverDays());
        return dailyForecastDemandByDay(result, horizonDays, anchorDate, planDate);
    }

    private static Map<Integer, BigDecimal> dailyForecastDemandByDay(
            SalesForecastResultRecord result,
            int horizonDays,
            LocalDate anchorDate,
            LocalDate planDate
    ) {
        SalesForecastDetailView detail = result == null ? null : SalesForecastDetailView.fromResult(result, true);
        SalesForecastFactorBreakdownView factors = detail == null ? null : detail.getFactorBreakdown();
        if (factors == null || factors.getDailyForecasts() == null || factors.getDailyForecasts().isEmpty()) {
            return Map.of();
        }
        Map<Integer, BigDecimal> demandByDay = new HashMap<>();
        for (SalesForecastDailyForecastView forecast : factors.getDailyForecasts()) {
            Integer day = planDayIndex(forecast, anchorDate, planDate);
            if (day == null || day < 1 || day > horizonDays) {
                continue;
            }
            demandByDay.merge(day, zeroIfNull(forecast.getForecastUnits()), BigDecimal::add);
        }
        return demandByDay;
    }

    private static Integer planDayIndex(SalesForecastDailyForecastView forecast, LocalDate anchorDate, LocalDate planDate) {
        if (forecast == null) {
            return null;
        }
        if (forecast.getForecastDate() != null && planDate != null) {
            return toDayIndex(ChronoUnit.DAYS.between(planDate, forecast.getForecastDate()));
        }
        int sourceDayIndex = forecast.getDayIndex();
        if (sourceDayIndex < 1) {
            return null;
        }
        if (anchorDate != null && planDate != null) {
            return toDayIndex(ChronoUnit.DAYS.between(planDate, anchorDate.plusDays(sourceDayIndex)));
        }
        return sourceDayIndex;
    }

    private static Integer toDayIndex(long dayIndex) {
        if (dayIndex < Integer.MIN_VALUE || dayIndex > Integer.MAX_VALUE) {
            return null;
        }
        return (int) dayIndex;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String skuKey(String value) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
