package com.nuono.next.datapull.wiring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.NoonDataPullScopeSource;
import com.nuono.next.datapull.orchestration.OperationHandlerDataPullJob;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.snapshot.CompleteSnapshotEngine;
import com.nuono.next.datapull.snapshot.MyBatisSnapshotStageStore;
import com.nuono.next.datapull.snapshot.SnapshotCheckpointCodec;
import com.nuono.next.datapull.snapshot.SnapshotCurrentFactStore;
import com.nuono.next.datapull.snapshot.SnapshotFactApplyGuard;
import com.nuono.next.datapull.snapshot.SnapshotEffectiveItemStore;
import com.nuono.next.datapull.snapshot.SnapshotStageStore;
import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import com.nuono.next.infrastructure.mapper.SnapshotCurrentFactMapper;
import com.nuono.next.infrastructure.mapper.SnapshotEffectiveItemMapper;
import com.nuono.next.infrastructure.mapper.SnapshotTwoPassMapper;
import com.nuono.next.noonpull.NoonProductInterfaceSmokeProvider;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.noonpull.datapull.Dp04ProductSnapshotCodec;
import com.nuono.next.noonpull.datapull.Dp04ProductSnapshotItem;
import com.nuono.next.noonpull.datapull.Dp04ProductSnapshotProvider;
import com.nuono.next.noonpull.datapull.Dp04ProductSnapshotWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Composition root for the DP-04 complete product-snapshot Module. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
@ConditionalOnBean(NoonProductInterfaceSmokeProvider.class)
public class Dp04SnapshotRuntimeConfiguration {
    static final String CHANNEL = "NOON_PARTNER_PRODUCT_LIST";
    static final String INITIAL_STEP = "SNAPSHOT_FETCH";

    @Bean
    Dp04ProductSnapshotCodec dp04ProductSnapshotCodec(ObjectMapper objectMapper) {
        return new Dp04ProductSnapshotCodec(objectMapper);
    }

    @Bean(name = "dp04SnapshotStageStore")
    SnapshotStageStore<Dp04ProductSnapshotItem> dp04SnapshotStageStore(
            CompleteSnapshotStageMapper mapper,
            SnapshotTwoPassMapper twoPassMapper,
            Dp04ProductSnapshotCodec codec
    ) {
        return new MyBatisSnapshotStageStore<>(mapper, twoPassMapper, codec, codec);
    }

    @Bean(name = "dp04CurrentProductSnapshotFactStore")
    SnapshotCurrentFactStore<Dp04ProductSnapshotItem> dp04CurrentProductSnapshotFactStore(
            SnapshotCurrentFactMapper mapper,
            Dp04ProductSnapshotCodec codec
    ) {
        return new SnapshotCurrentFactStore<>(mapper, codec, codec);
    }

    @Bean
    Dp04ProductSnapshotProvider dp04ProductSnapshotProvider(
            NoonProductInterfaceSmokeProvider provider,
            NoonPullStoreBindingResolver bindingResolver
    ) {
        return new Dp04ProductSnapshotProvider(provider, bindingResolver);
    }

    @Bean
    Dp04ProductSnapshotWriter dp04ProductSnapshotWriter(
            SnapshotFactApplyGuard applyGuard,
            Dp04ProductSnapshotCodec codec,
            SnapshotEffectiveItemStore<Dp04ProductSnapshotItem> effectiveItems
    ) {
        return new Dp04ProductSnapshotWriter(applyGuard, codec, effectiveItems);
    }

    @Bean
    SnapshotEffectiveItemStore<Dp04ProductSnapshotItem> dp04SnapshotEffectiveItemStore(
            SnapshotEffectiveItemMapper mapper,
            Dp04ProductSnapshotCodec codec
    ) {
        return new SnapshotEffectiveItemStore<>(mapper, codec);
    }

    @Bean
    DataPullJob dp04ProductSnapshotJob(
            NoonDataPullScopeMapper scopeMapper,
            Dp04ProductSnapshotProvider provider,
            @Qualifier("dp04SnapshotStageStore")
            SnapshotStageStore<Dp04ProductSnapshotItem> stageStore,
            Dp04ProductSnapshotWriter writer,
            ProviderWaitTransition providerWaitTransition
    ) {
        CompleteSnapshotEngine<Dp04ProductSnapshotItem> engine = new CompleteSnapshotEngine<>(
                OperationCode.DP04,
                provider,
                stageStore,
                writer,
                new SnapshotCheckpointCodec(),
                providerWaitTransition
        );
        return new OperationHandlerDataPullJob(
                OperationCode.DP04,
                CHANNEL,
                INITIAL_STEP,
                new NoonDataPullScopeSource(scopeMapper),
                engine
        );
    }
}
