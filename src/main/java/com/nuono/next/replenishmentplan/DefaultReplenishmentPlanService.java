package com.nuono.next.replenishmentplan;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.InboundBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.MissingEtaBatch;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanInput;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanItemView;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanOverviewView;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanQuery;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.StockSnapshot;
import com.nuono.next.salesforecast.SalesForecastDetailView;
import com.nuono.next.salesforecast.SalesForecastFactorBreakdownView;
import com.nuono.next.salesforecast.SalesForecastOverviewRow;
import com.nuono.next.salesforecast.SalesForecastOverviewView;
import com.nuono.next.salesforecast.SalesForecastQuery;
import com.nuono.next.salesforecast.SalesForecastService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DefaultReplenishmentPlanService implements ReplenishmentPlanService {

    private static final int DAILY_DEMAND_SCALE = 8;

    private final SalesForecastService salesForecastService;
    private final ReplenishmentPlanRepository repository;
    private final ReplenishmentPlanConfigResolver configResolver;
    private final ReplenishmentPlanCalculator calculator;
    private final Clock clock;

    public DefaultReplenishmentPlanService(
            SalesForecastService salesForecastService,
            ReplenishmentPlanRepository repository,
            ReplenishmentPlanConfigResolver configResolver
    ) {
        this(
                salesForecastService,
                repository,
                configResolver,
                new ReplenishmentPlanCalculator(),
                Clock.system(ZoneOffset.UTC)
        );
    }

    DefaultReplenishmentPlanService(
            SalesForecastService salesForecastService,
            ReplenishmentPlanRepository repository,
            ReplenishmentPlanConfigResolver configResolver,
            ReplenishmentPlanCalculator calculator,
            Clock clock
    ) {
        this.salesForecastService = salesForecastService;
        this.repository = repository;
        this.configResolver = configResolver;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Override
    public PlanOverviewView getOverview(PlanQuery query) {
        ReplenishmentPlanConfig config = configResolver.resolve(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        );
        SalesForecastOverviewView forecast = salesForecastService.getOverview(new SalesForecastQuery(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        ));
        LocalDate anchorDate = anchorDate(forecast);
        if (forecast == null || !"ready".equals(forecast.getState()) || forecast.getRows().isEmpty()) {
            return emptyOverview(query, config, anchorDate);
        }

        Map<String, ReplenishmentPlanRepository.StockRow> stockByPartnerSku = listStockRows(query).stream()
                .filter(row -> row != null && hasText(row.getPartnerSku()))
                .collect(Collectors.toMap(
                        ReplenishmentPlanRepository.StockRow::getPartnerSku,
                        row -> row,
                        (left, right) -> left
                ));
        Map<String, List<ReplenishmentPlanRepository.InboundRow>> inboundByPartnerSku = listInboundRows(query).stream()
                .filter(row -> row != null && hasText(row.getPartnerSku()))
                .collect(Collectors.groupingBy(ReplenishmentPlanRepository.InboundRow::getPartnerSku));

        List<PlanItemView> rows = new ArrayList<>();
        for (SalesForecastOverviewRow forecastRow : forecast.getRows()) {
            if (forecastRow == null || !hasText(forecastRow.getPartnerSku())) {
                continue;
            }
            List<ReplenishmentPlanRepository.InboundRow> inboundRows =
                    inboundByPartnerSku.getOrDefault(forecastRow.getPartnerSku(), List.of());
            rows.add(calculator.calculate(new PlanInput(
                    forecastRow.getPartnerSku(),
                    forecastRow.getSku(),
                    forecastRow.getProductTitle(),
                    anchorDate,
                    stockSnapshot(stockByPartnerSku.get(forecastRow.getPartnerSku())),
                    dailyDemandByDay(forecastRow, config),
                    knownInboundBatches(inboundRows),
                    missingEtaBatches(inboundRows)
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

    private LocalDate anchorDate(SalesForecastOverviewView forecast) {
        if (forecast != null && forecast.getSourceDataDate() != null) {
            return forecast.getSourceDataDate();
        }
        return LocalDate.now(clock);
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
        BigDecimal currentStockUnits = row.getCurrentStockUnits();
        boolean currentStockFactMissing = currentStockUnits == null;
        if (currentStockUnits == null && (fbnStockUnits != null || supermallStockUnits != null)) {
            currentStockUnits = zeroIfNull(fbnStockUnits).add(zeroIfNull(supermallStockUnits));
        }
        return new StockSnapshot(
                currentStockUnits,
                fbnStockUnits,
                supermallStockUnits,
                currentStockFactMissing
        );
    }

    private static List<InboundBatch> knownInboundBatches(List<ReplenishmentPlanRepository.InboundRow> rows) {
        List<InboundBatch> batches = new ArrayList<>();
        for (ReplenishmentPlanRepository.InboundRow row : rows) {
            if (row == null || row.getEtaDate() == null) {
                continue;
            }
            batches.add(new InboundBatch(
                    row.getBatchId(),
                    row.getBatchReferenceNo(),
                    row.getTransportMode(),
                    row.getBatchStatus(),
                    row.getEtaDate(),
                    row.getRemainingQuantity()
            ));
        }
        return batches;
    }

    private static List<MissingEtaBatch> missingEtaBatches(List<ReplenishmentPlanRepository.InboundRow> rows) {
        List<MissingEtaBatch> batches = new ArrayList<>();
        for (ReplenishmentPlanRepository.InboundRow row : rows) {
            if (row == null || row.getEtaDate() != null) {
                continue;
            }
            batches.add(new MissingEtaBatch(
                    row.getBatchId(),
                    row.getBatchReferenceNo(),
                    row.getTransportMode(),
                    row.getBatchStatus(),
                    row.getRemainingQuantity()
            ));
        }
        return batches;
    }

    private static Map<Integer, BigDecimal> dailyDemandByDay(
            SalesForecastOverviewRow row,
            ReplenishmentPlanConfig config
    ) {
        int horizonDays = Math.max(config.getForecastHorizonDays(), config.getSeaLeadDays() + config.getSeaCoverDays());
        BigDecimal dailyDemand = constantDailyDemand(row);
        Map<Integer, BigDecimal> demandByDay = new HashMap<>();
        for (int day = 1; day <= horizonDays; day++) {
            demandByDay.put(day, dailyDemand);
        }
        return demandByDay;
    }

    private static BigDecimal constantDailyDemand(SalesForecastOverviewRow row) {
        if (row.getForecastUnits90() > 0) {
            return BigDecimal.valueOf(row.getForecastUnits90())
                    .divide(BigDecimal.valueOf(90), DAILY_DEMAND_SCALE, RoundingMode.HALF_UP);
        }
        SalesForecastDetailView detail = row.getDetail();
        SalesForecastFactorBreakdownView factors = detail == null ? null : detail.getFactorBreakdown();
        if (factors == null
                || factors.getBaseDailySales() == null
                || factors.getTrendFactor() == null
                || factors.getLifecycleFactor() == null
                || factors.getFutureFactor() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal demand = factors.getBaseDailySales()
                .multiply(factors.getTrendFactor())
                .multiply(factors.getLifecycleFactor())
                .multiply(factors.getFutureFactor());
        return demand.signum() <= 0 ? BigDecimal.ZERO : demand.setScale(DAILY_DEMAND_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
