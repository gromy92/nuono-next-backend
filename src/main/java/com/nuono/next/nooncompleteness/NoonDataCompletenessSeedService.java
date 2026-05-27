package com.nuono.next.nooncompleteness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NoonDataCompletenessSeedService {
    private static final List<NoonDataCategory> FIRST_VERSION_CATEGORIES = List.of(
            NoonDataCategory.PRODUCT_LIST,
            NoonDataCategory.PRODUCT_DETAIL,
            NoonDataCategory.SALES_ORDER,
            NoonDataCategory.SALES_PRODUCT_VIEWS
    );

    private final NoonDataCompletenessRepository repository;
    private final Clock clock;

    @Autowired
    public NoonDataCompletenessSeedService(NoonDataCompletenessRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public NoonDataCompletenessSeedService(NoonDataCompletenessRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public void seedNewScope(NoonDataCompletenessSeedCommand command) {
        SeedScope scope = scope(command);
        LocalDate seedDate = command.getSeedDate() == null ? LocalDate.now(clock) : command.getSeedDate();
        LocalDate latestDate = seedDate.minusDays(1);
        LocalDate historyFrom = seedDate.minusMonths(6);

        for (NoonDataCategory category : FIRST_VERSION_CATEGORIES) {
            CategorySeed seed = categorySeed(category, command.isProductProjectionPresent());
            NoonDataCompletenessRecord row = upsertCompleteness(scope, category, seed, seedDate);
            for (GapSeed gap : gapSeeds(category, seed, seedDate, latestDate, historyFrom)) {
                insertGapIfMissing(row, gap);
            }
        }
    }

    private NoonDataCompletenessRecord upsertCompleteness(
            SeedScope scope,
            NoonDataCategory category,
            CategorySeed seed,
            LocalDate seedDate
    ) {
        NoonDataCompletenessRecord existing = existingCompleteness(scope, category);
        LocalDateTime now = now();
        NoonDataCompletenessRecord row = existing == null ? new NoonDataCompletenessRecord() : existing.copy();
        if (row.getId() == null) {
            row.setId(repository.nextId("noon_data_completeness", 900000L));
        }
        row.setOwnerUserId(scope.ownerUserId);
        row.setStoreCode(scope.storeCode);
        row.setSiteCode(scope.siteCode);
        row.setCategory(category);
        row.setLatestStatus(seed.latestStatus);
        row.setHistoryStatus(seed.historyStatus);
        row.setPatrolEnabled(seed.activeGapCount > 0);
        row.setActiveGapCount(seed.activeGapCount);
        row.setDiagnosticSummary(seed.diagnosticSummary);
        row.setCreatedAt(row.getCreatedAt() == null ? now : row.getCreatedAt());
        row.setUpdatedAt(now);
        if (seed.latestStatus == NoonDataLatestStatus.READY) {
            row.setLatestDataDate(seedDate);
        }
        repository.insertCompleteness(row);
        return row;
    }

    private void insertGapIfMissing(NoonDataCompletenessRecord row, GapSeed seed) {
        boolean exists = repository.listGapWindows(gapQuery(row)).stream()
                .anyMatch((gap) -> sameGap(gap, seed));
        if (exists) {
            return;
        }

        LocalDateTime now = now();
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(repository.nextId("noon_data_gap_window", 910000L));
        gap.setCompletenessId(row.getId());
        gap.setOwnerUserId(row.getOwnerUserId());
        gap.setStoreCode(row.getStoreCode());
        gap.setSiteCode(row.getSiteCode());
        gap.setCategory(row.getCategory());
        gap.setWindowType(seed.windowType);
        gap.setDateFrom(seed.dateFrom);
        gap.setDateTo(seed.dateTo);
        gap.setStatus(NoonDataGapStatus.PENDING);
        gap.setAttempts(0);
        gap.setRetryable(Boolean.TRUE);
        gap.setRequiresManualAction(Boolean.FALSE);
        gap.setDiagnosticSummary(seed.diagnosticSummary);
        gap.setCreatedAt(now);
        gap.setUpdatedAt(now);
        repository.insertGapWindow(gap);
    }

    private CategorySeed categorySeed(NoonDataCategory category, boolean productProjectionPresent) {
        if (category == NoonDataCategory.SALES_ORDER || category == NoonDataCategory.SALES_PRODUCT_VIEWS) {
            return new CategorySeed(
                    NoonDataLatestStatus.INCOMPLETE,
                    NoonDataHistoryStatus.INCOMPLETE,
                    2,
                    "新接入店铺销售历史和最新数据未确认，等待巡检规划。"
            );
        }
        if (!productProjectionPresent) {
            return new CategorySeed(
                    NoonDataLatestStatus.INCOMPLETE,
                    NoonDataHistoryStatus.INCOMPLETE,
                    1,
                    "新接入店铺商品投影缺失，等待商品基线巡检。"
            );
        }
        return new CategorySeed(
                NoonDataLatestStatus.READY,
                NoonDataHistoryStatus.NOT_REQUIRED,
                0,
                "新接入店铺已有商品投影基线。"
        );
    }

    private List<GapSeed> gapSeeds(
            NoonDataCategory category,
            CategorySeed seed,
            LocalDate seedDate,
            LocalDate latestDate,
            LocalDate historyFrom
    ) {
        if (seed.activeGapCount == 0) {
            return List.of();
        }
        if (category == NoonDataCategory.PRODUCT_LIST) {
            return List.of(new GapSeed(
                    NoonDataGapWindowType.PRODUCT_BASELINE,
                    seedDate,
                    seedDate,
                    "商品列表基线缺失，等待拉取商品列表。"
            ));
        }
        if (category == NoonDataCategory.PRODUCT_DETAIL) {
            return List.of(new GapSeed(
                    NoonDataGapWindowType.PRODUCT_DETAIL_BASELINE,
                    seedDate,
                    seedDate,
                    "商品详情基线缺失，等待商品详情补齐。"
            ));
        }
        return List.of(
                new GapSeed(
                        NoonDataGapWindowType.LATEST_DAILY,
                        latestDate,
                        latestDate,
                        "最新日数据未确认，等待每日巡检。"
                ),
                new GapSeed(
                        NoonDataGapWindowType.HISTORY_BACKFILL,
                        historyFrom,
                        latestDate,
                        "历史数据未补齐，等待回填规划。"
                )
        );
    }

    private NoonDataCompletenessRecord existingCompleteness(SeedScope scope, NoonDataCategory category) {
        NoonDataCompletenessQuery query = new NoonDataCompletenessQuery();
        query.setOwnerUserId(scope.ownerUserId);
        query.setStoreCode(scope.storeCode);
        query.setSiteCode(scope.siteCode);
        query.setCategory(category);
        return repository.listCompleteness(query).stream().findFirst().orElse(null);
    }

    private NoonDataGapQuery gapQuery(NoonDataCompletenessRecord row) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setOwnerUserId(row.getOwnerUserId());
        query.setStoreCode(row.getStoreCode());
        query.setSiteCode(row.getSiteCode());
        query.setCategory(row.getCategory());
        return query;
    }

    private boolean sameGap(NoonDataGapWindowRecord gap, GapSeed seed) {
        return gap.getWindowType() == seed.windowType
                && Objects.equals(gap.getDateFrom(), seed.dateFrom)
                && Objects.equals(gap.getDateTo(), seed.dateTo);
    }

    private SeedScope scope(NoonDataCompletenessSeedCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少完整性种子命令。");
        }
        Long ownerUserId = command.getOwnerUserId();
        String storeCode = normalize(command.getStoreCode(), false);
        String siteCode = normalize(command.getSiteCode(), true);
        if (ownerUserId == null) {
            throw new IllegalArgumentException("缺少 ownerUserId，无法创建数据完整性种子。");
        }
        if (storeCode.isEmpty()) {
            throw new IllegalArgumentException("缺少 storeCode，无法创建数据完整性种子。");
        }
        if (siteCode.isEmpty()) {
            throw new IllegalArgumentException("缺少 siteCode，无法创建数据完整性种子。");
        }
        return new SeedScope(ownerUserId, storeCode, siteCode);
    }

    private String normalize(String value, boolean upperCase) {
        String normalized = value == null ? "" : value.trim();
        return upperCase ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static class SeedScope {
        private final Long ownerUserId;
        private final String storeCode;
        private final String siteCode;

        private SeedScope(Long ownerUserId, String storeCode, String siteCode) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }
    }

    private static class CategorySeed {
        private final NoonDataLatestStatus latestStatus;
        private final NoonDataHistoryStatus historyStatus;
        private final int activeGapCount;
        private final String diagnosticSummary;

        private CategorySeed(
                NoonDataLatestStatus latestStatus,
                NoonDataHistoryStatus historyStatus,
                int activeGapCount,
                String diagnosticSummary
        ) {
            this.latestStatus = latestStatus;
            this.historyStatus = historyStatus;
            this.activeGapCount = activeGapCount;
            this.diagnosticSummary = diagnosticSummary;
        }
    }

    private static class GapSeed {
        private final NoonDataGapWindowType windowType;
        private final LocalDate dateFrom;
        private final LocalDate dateTo;
        private final String diagnosticSummary;

        private GapSeed(
                NoonDataGapWindowType windowType,
                LocalDate dateFrom,
                LocalDate dateTo,
                String diagnosticSummary
        ) {
            this.windowType = windowType;
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
            this.diagnosticSummary = diagnosticSummary;
        }
    }
}
