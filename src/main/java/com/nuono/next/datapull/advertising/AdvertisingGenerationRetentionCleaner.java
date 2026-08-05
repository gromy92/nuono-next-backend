package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeMaintenance;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingRetentionMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Bounded retention for sealed DP-06 generations superseded by a current head. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class AdvertisingGenerationRetentionCleaner implements DataPullRuntimeMaintenance {
    static final int FACT_BATCH_SIZE = 100;
    static final int GENERATION_BATCH_SIZE = 1;
    static final Duration TERMINAL_GRACE = Duration.ofDays(7);
    static final Duration RUN_INTERVAL = Duration.ofMinutes(1);

    private final Dp06AdvertisingRetentionMapper mapper;
    private Instant nextRunUtc = Instant.MIN;

    public AdvertisingGenerationRetentionCleaner(Dp06AdvertisingRetentionMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public synchronized void run(Instant nowUtc) {
        Instant now = Objects.requireNonNull(nowUtc, "nowUtc");
        if (now.isBefore(nextRunUtc)) return;
        LocalDateTime cutoffUtc = LocalDateTime.ofInstant(
                now.minus(TERMINAL_GRACE), ZoneOffset.UTC
        );
        requireBounded(mapper.deleteQueriesBatch(cutoffUtc, FACT_BATCH_SIZE), FACT_BATCH_SIZE);
        requireBounded(
                mapper.deleteAbandonedQueriesBatch(cutoffUtc, FACT_BATCH_SIZE),
                FACT_BATCH_SIZE
        );
        requireBounded(mapper.deleteCampaignsBatch(cutoffUtc, FACT_BATCH_SIZE), FACT_BATCH_SIZE);
        requireBounded(
                mapper.deleteAbandonedCampaignsBatch(cutoffUtc, FACT_BATCH_SIZE),
                FACT_BATCH_SIZE
        );
        requireBounded(
                mapper.deleteGenerationsBatch(cutoffUtc, GENERATION_BATCH_SIZE),
                GENERATION_BATCH_SIZE
        );
        requireBounded(
                mapper.deleteAbandonedGenerationsBatch(cutoffUtc, GENERATION_BATCH_SIZE),
                GENERATION_BATCH_SIZE
        );
        nextRunUtc = now.plus(RUN_INTERVAL);
    }

    private void requireBounded(int deleted, int bound) {
        if (deleted < 0 || deleted > bound) {
            throw new IllegalStateException("DP06_RETENTION_DELETE_COUNT_INVALID");
        }
    }
}
