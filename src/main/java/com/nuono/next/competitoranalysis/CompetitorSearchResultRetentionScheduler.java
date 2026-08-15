package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorSearchResultRetentionMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bounded retention for full Top-200 search evidence. Rank facts and product
 * snapshots deliberately remain outside this cleaner.
 */
@Component
public class CompetitorSearchResultRetentionScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompetitorSearchResultRetentionScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_BATCH_SIZE = 1_000;
    private static final int MAX_BATCHES_PER_RUN = 100;

    private final CompetitorSearchResultRetentionMapper mapper;
    private final boolean enabled;
    private final int retentionDays;
    private final int batchSize;
    private final int maximumBatchesPerRun;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public CompetitorSearchResultRetentionScheduler(
            CompetitorSearchResultRetentionMapper mapper,
            @Value("${nuono.competitor-analysis.search-result-retention.enabled:false}") boolean enabled,
            @Value("${nuono.competitor-analysis.search-result-retention.retention-days:15}") int retentionDays,
            @Value("${nuono.competitor-analysis.search-result-retention.batch-size:500}") int batchSize,
            @Value("${nuono.competitor-analysis.search-result-retention.maximum-batches-per-run:5}") int maximumBatchesPerRun
    ) {
        this(
                mapper,
                enabled,
                retentionDays,
                batchSize,
                maximumBatchesPerRun,
                Clock.system(BUSINESS_ZONE)
        );
    }

    CompetitorSearchResultRetentionScheduler(
            CompetitorSearchResultRetentionMapper mapper,
            boolean enabled,
            int retentionDays,
            int batchSize,
            int maximumBatchesPerRun,
            Clock clock
    ) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("competitor search-result retention-days must be at least one day");
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("competitor search-result retention batch-size must be 1.." + MAX_BATCH_SIZE);
        }
        if (maximumBatchesPerRun < 1 || maximumBatchesPerRun > MAX_BATCHES_PER_RUN) {
            throw new IllegalArgumentException(
                    "competitor search-result retention maximum-batches-per-run must be 1.." + MAX_BATCHES_PER_RUN
            );
        }
        this.mapper = mapper;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.maximumBatchesPerRun = maximumBatchesPerRun;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${nuono.competitor-analysis.search-result-retention.fixed-delay-ms:60000}",
            initialDelayString = "${nuono.competitor-analysis.search-result-retention.initial-delay-ms:120000}"
    )
    public void runScheduledCleanup() {
        int deleted = runOnce();
        if (deleted > 0) {
            LOGGER.info(
                    "competitor search result retention deleted={} retentionDays={} batchSize={} maximumBatchesPerRun={}",
                    deleted,
                    retentionDays,
                    batchSize,
                    maximumBatchesPerRun
            );
        }
    }

    int runOnce() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
            int deleted = 0;
            for (int batch = 0; batch < maximumBatchesPerRun; batch++) {
                int affectedRows = mapper.deleteExpiredTerminalSearchResults(cutoff, batchSize);
                if (affectedRows < 0 || affectedRows > batchSize) {
                    throw new IllegalStateException("competitor search-result retention returned invalid delete count: " + affectedRows);
                }
                deleted += affectedRows;
                if (affectedRows < batchSize) {
                    break;
                }
            }
            return deleted;
        } finally {
            running.set(false);
        }
    }
}
