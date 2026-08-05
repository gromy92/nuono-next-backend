package com.nuono.next.datapull.wiring;

import com.nuono.next.datapull.advertising.AdvertisingFactWriter;
import com.nuono.next.datapull.advertising.AdvertisingProvider;
import com.nuono.next.datapull.advertising.AdvertisingStagedFact;
import com.nuono.next.datapull.advertising.AdvertisingStagedFactCodec;
import com.nuono.next.datapull.advertising.Dp06AdvertisingJob;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.NoonDataPullScopeSource;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.snapshot.MyBatisSnapshotStageStore;
import com.nuono.next.datapull.snapshot.SnapshotStageStore;
import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Composition root for the bounded 2+C DP-06 Module. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class Dp06RuntimeConfiguration {
    static final String CHANNEL = "NOON_ADMANAGER";

    @Bean
    AdvertisingStagedFactCodec dp06AdvertisingStagedFactCodec() {
        return new AdvertisingStagedFactCodec();
    }

    @Bean("dp06SnapshotStageStore")
    SnapshotStageStore<AdvertisingStagedFact> dp06SnapshotStageStore(
            CompleteSnapshotStageMapper mapper,
            AdvertisingStagedFactCodec codec
    ) {
        return new MyBatisSnapshotStageStore<>(mapper, codec, codec);
    }

    @Bean("dp06AdvertisingJob")
    DataPullJob dp06AdvertisingJob(
            NoonDataPullScopeMapper scopeMapper,
            AdvertisingProvider provider,
            @Qualifier("dp06SnapshotStageStore")
            SnapshotStageStore<AdvertisingStagedFact> stageStore,
            AdvertisingFactWriter factWriter,
            ProviderWaitTransition providerWaitTransition
    ) {
        return new Dp06AdvertisingJob(
                CHANNEL,
                new NoonDataPullScopeSource(scopeMapper),
                provider,
                stageStore,
                factWriter,
                providerWaitTransition,
                Duration.ofMinutes(1)
        );
    }
}
