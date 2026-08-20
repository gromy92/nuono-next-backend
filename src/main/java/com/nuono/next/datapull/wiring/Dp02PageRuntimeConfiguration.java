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
import com.nuono.next.datapull.snapshot.SnapshotFactApplyGuard;
import com.nuono.next.datapull.snapshot.SnapshotStageStore;
import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import com.nuono.next.infrastructure.mapper.SnapshotTwoPassMapper;
import com.nuono.next.noonpull.NoonOrderFactWriter;
import com.nuono.next.noonpull.NoonOrderLineFact;
import com.nuono.next.noonpull.NoonOrderReportRowClassifier;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.noonpull.NoonSalesPageQueryProvider;
import com.nuono.next.noonpull.datapull.Dp02OrderFactCodec;
import com.nuono.next.noonpull.datapull.Dp02OrderPageProvider;
import com.nuono.next.noonpull.datapull.Dp02OrderPageWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Composition root for the DP02 exact-window, two-pass page collection. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
@ConditionalOnBean(NoonSalesPageQueryProvider.class)
public class Dp02PageRuntimeConfiguration {
    static final String INITIAL_STEP = "DP02_PAGE_FETCH";

    @Bean
    Dp02OrderFactCodec dp02OrderFactCodec(ObjectMapper objectMapper) {
        return new Dp02OrderFactCodec(objectMapper);
    }

    @Bean(name = "dp02OrderPageStageStore")
    SnapshotStageStore<NoonOrderLineFact> dp02OrderPageStageStore(
            CompleteSnapshotStageMapper mapper,
            SnapshotTwoPassMapper twoPassMapper,
            Dp02OrderFactCodec codec
    ) {
        return new MyBatisSnapshotStageStore<>(mapper, twoPassMapper, codec, codec);
    }

    @Bean(name = "dp02OrderPageProvider")
    Dp02OrderPageProvider dp02OrderPageProvider(
            NoonSalesPageQueryProvider provider,
            NoonPullStoreBindingResolver bindingResolver,
            NoonOrderReportRowClassifier classifier,
            ObjectMapper objectMapper
    ) {
        return new Dp02OrderPageProvider(
                provider, bindingResolver, classifier, objectMapper
        );
    }

    @Bean
    Dp02OrderPageWriter dp02OrderPageWriter(
            SnapshotFactApplyGuard applyGuard,
            Dp02OrderFactCodec codec,
            NoonOrderFactWriter factWriter
    ) {
        return new Dp02OrderPageWriter(applyGuard, codec, factWriter);
    }

    @Bean
    DataPullJob dp02OrderPageJob(
            NoonDataPullScopeMapper scopeMapper,
            @Qualifier("dp02OrderPageProvider") Dp02OrderPageProvider provider,
            @Qualifier("dp02OrderPageStageStore")
            SnapshotStageStore<NoonOrderLineFact> stageStore,
            Dp02OrderPageWriter writer,
            ProviderWaitTransition providerWaitTransition
    ) {
        CompleteSnapshotEngine<NoonOrderLineFact> engine = new CompleteSnapshotEngine<>(
                OperationCode.DP02,
                provider,
                stageStore,
                writer,
                new SnapshotCheckpointCodec(),
                providerWaitTransition
        );
        return new OperationHandlerDataPullJob(
                OperationCode.DP02,
                Dp02OrderPageProvider.CHANNEL,
                INITIAL_STEP,
                new NoonDataPullScopeSource(scopeMapper),
                engine
        );
    }
}
