package com.nuono.next.replenishmentplan;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.InboundBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.MissingEtaBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanInput;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanItemView;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanQuery;
import com.nuono.next.salesforecast.SalesForecastDetailView;
import com.nuono.next.salesforecast.SalesForecastDailyForecastView;
import com.nuono.next.salesforecast.SalesForecastFactorBreakdownView;
import com.nuono.next.salesforecast.SalesForecastFeatureValuesView;
import com.nuono.next.salesforecast.SalesForecastOverviewRow;
import com.nuono.next.salesforecast.SalesForecastOverviewView;
import com.nuono.next.salesforecast.SalesForecastQuery;
import com.nuono.next.salesforecast.SalesForecastResultRecord;
import com.nuono.next.salesforecast.SalesForecastRunRepository;
import com.nuono.next.salesforecast.SalesForecastService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultReplenishmentPlanService implements ReplenishmentPlanService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final SalesForecastService forecastService;
    private final SalesForecastRunRepository forecastRunRepository;
    private final ReplenishmentPlanRepository repository;
    private final ReplenishmentPlanConfigResolver configResolver;
    private final ReplenishmentPlanCalculator calculator;
    private final Clock clock;

    @Autowired
    public DefaultReplenishmentPlanService(
            SalesForecastService forecastService,
            SalesForecastRunRepository forecastRunRepository,
            ReplenishmentPlanRepository repository,
            ReplenishmentPlanConfigResolver configResolver
    ) {
        this(
                forecastService,
                forecastRunRepository,
                repository,
                configResolver,
                new ReplenishmentPlanCalculator(),
                Clock.system(BUSINESS_ZONE)
        );
    }

    DefaultReplenishmentPlanService(
            SalesForecastService forecastService,
            SalesForecastRunRepository forecastRunRepository,
            ReplenishmentPlanRepository repository,
            ReplenishmentPlanConfigResolver configResolver,
            ReplenishmentPlanCalculator calculator,
            Clock clock
    ) {
        this.forecastService = forecastService;
        this.forecastRunRepository = forecastRunRepository;
        this.repository = repository;
        this.configResolver = configResolver;
        this.calculator = calculator;
        this.clock = clock == null ? Clock.system(BUSINESS_ZONE) : clock.withZone(BUSINESS_ZONE);
    }

    @Override
    public ReplenishmentPlanOverviewView getOverview(PlanQuery query) {
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
        Map<String, ReplenishmentProductStockRow> stockByPartnerSku =
                ReplenishmentProductCoverageAssembler.index(listStockRows(query));
        SalesForecastOverviewView forecast = forecastService.getOverview(forecastQuery);
        LocalDate anchorDate = anchorDate(forecast);
        if (forecast == null || !"ready".equals(forecast.getState()) || forecast.getRunId() == null) {
            return coverageOnlyOverview(query, config, anchorDate, stockByPartnerSku);
        }
        LocalDate planDate = LocalDate.now(clock);
        List<SalesForecastResultRecord> forecastResults = forecastRunRepository.listResults(forecast.getRunId());
        if (forecastResults == null || forecastResults.isEmpty()) {
            return coverageOnlyOverview(query, config, anchorDate, stockByPartnerSku);
        }
        List<SalesForecastOverviewRow> forecastRows = forecast.getRows().stream()
                .filter(row -> row != null && hasText(row.getPartnerSku()))
                .collect(Collectors.toList());
        if (forecastRows.isEmpty()) {
            return coverageOnlyOverview(query, config, anchorDate, stockByPartnerSku);
        }
        Map<String, SalesForecastResultRecord> forecastResultByPartnerSku = forecastResults.stream()
                .filter(record -> record != null && hasText(record.getPartnerSku()))
                .collect(Collectors.toMap(
                        record -> skuKey(record.getPartnerSku()),
                        record -> record,
                        (left, right) -> left
                ));

        Map<String, List<ReplenishmentPlanRepository.InboundRow>> inboundByPartnerSku = listInboundRows(query).stream()
                .filter(row -> row != null && hasText(row.getPartnerSku()))
                .collect(Collectors.groupingBy(row -> skuKey(row.getPartnerSku())));

        List<PlanItemView> rows = new ArrayList<>();
        Set<String> forecastedProductKeys = new HashSet<>();
        for (SalesForecastOverviewRow forecastRow : forecastRows) {
            String partnerSkuKey = skuKey(forecastRow.getPartnerSku());
            forecastedProductKeys.add(partnerSkuKey);
            ReplenishmentProductStockRow stockRow = stockByPartnerSku.get(partnerSkuKey);
            List<ReplenishmentPlanRepository.InboundRow> inboundRows =
                    inboundByPartnerSku.getOrDefault(partnerSkuKey, List.of());
            rows.add(calculator.calculate(new PlanInput(
                    forecastRow.getPartnerSku(),
                    forecastRow.getSku(),
                    forecastRow.getProductTitle(),
                    ReplenishmentProductCoverageAssembler.imageUrl(stockRow),
                    ReplenishmentProductCoverageAssembler.listingAt(stockRow),
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
                    ReplenishmentProductCoverageAssembler.stockSnapshot(stockRow),
                    dailyDemandByDay(forecastResultByPartnerSku.get(partnerSkuKey), config, anchorDate, planDate),
                    knownInboundBatches(inboundRows),
                    missingEtaBatches(inboundRows),
                    hasUnresolvedInboundSite(inboundRows)
            ), config).withActiveState(
                    stockRow == null ? null : stockRow.getIsActive(),
                    stockRow == null ? null : stockRow.getActiveStateSource(),
                    stockRow == null ? null : stockRow.getActiveStateSyncedAt()
            ));
        }

        ReplenishmentProductCoverageAssembler.appendBlocked(
                rows, config, stockByPartnerSku, forecastedProductKeys);

        return new ReplenishmentPlanOverviewView(
                "ready",
                query.getStoreCode(),
                query.getSiteCode(),
                ReplenishmentPlanConfig.CALCULATION_VERSION,
                config,
                anchorDate,
                ReplenishmentProductCoverageAssembler.summarize(stockByPartnerSku, forecastedProductKeys),
                rows
        );
    }

    private ReplenishmentPlanOverviewView emptyOverview(PlanQuery query, ReplenishmentPlanConfig config, LocalDate anchorDate) {
        return new ReplenishmentPlanOverviewView(
                "empty",
                query.getStoreCode(),
                query.getSiteCode(),
                ReplenishmentPlanConfig.CALCULATION_VERSION,
                config,
                anchorDate,
                ReplenishmentProductCoverageView.empty(),
                List.of()
        );
    }

    private ReplenishmentPlanOverviewView coverageOnlyOverview(
            PlanQuery query,
            ReplenishmentPlanConfig config,
            LocalDate anchorDate,
            Map<String, ReplenishmentProductStockRow> stockByPartnerSku
    ) {
        if (stockByPartnerSku == null || stockByPartnerSku.isEmpty()) {
            return emptyOverview(query, config, anchorDate);
        }
        return ReplenishmentProductCoverageAssembler.coverageOnly(
                query, config, anchorDate, stockByPartnerSku);
    }

    private LocalDate anchorDate(SalesForecastOverviewView forecast) {
        if (forecast != null && forecast.getSourceDataDate() != null) {
            return forecast.getSourceDataDate();
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

    private List<ReplenishmentProductStockRow> listStockRows(PlanQuery query) {
        List<ReplenishmentProductStockRow> rows = repository.listFbnSupermallStock(
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
