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
import com.nuono.next.datapull.snapshot.SnapshotApplyTargetStore;
import com.nuono.next.datapull.snapshot.SnapshotStageStore;
import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import com.nuono.next.infrastructure.mapper.InventorySnapshotRuntimeMapper;
import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import com.nuono.next.infrastructure.mapper.SnapshotTwoPassMapper;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotCodec;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotBatchWriter;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotItem;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotProvider;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Composition root for the DP-07-A complete inventory-snapshot Module. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
@ConditionalOnBean(OfficialWarehouseFbnInventoryProvider.class)
public class Dp07SnapshotRuntimeConfiguration {
    static final String CHANNEL = "NOON_FBN_INVENTORY";
    static final String INITIAL_STEP = "SNAPSHOT_FETCH";

    @Bean
    Dp07InventorySnapshotCodec dp07InventorySnapshotCodec(ObjectMapper objectMapper) {
        return new Dp07InventorySnapshotCodec(objectMapper);
    }

    @Bean(name = "dp07aSnapshotStageStore")
    SnapshotStageStore<Dp07InventorySnapshotItem> dp07aSnapshotStageStore(
            CompleteSnapshotStageMapper mapper,
            SnapshotTwoPassMapper twoPassMapper,
            Dp07InventorySnapshotCodec codec
    ) {
        return new MyBatisSnapshotStageStore<>(mapper, twoPassMapper, codec, codec);
    }

    @Bean
    Dp07InventorySnapshotProvider dp07InventorySnapshotProvider(
            OfficialWarehouseFbnInventoryProvider provider,
            NoonPullStoreBindingResolver bindingResolver,
            ObjectMapper objectMapper
    ) {
        return new Dp07InventorySnapshotProvider(provider, bindingResolver, objectMapper);
    }

    @Bean
    Dp07InventorySnapshotWriter dp07InventorySnapshotWriter(
            SnapshotFactApplyGuard applyGuard,
            Dp07InventorySnapshotCodec codec,
            Dp07InventorySnapshotBatchWriter batchWriter
    ) {
        return new Dp07InventorySnapshotWriter(applyGuard, codec, batchWriter);
    }

    @Bean
    SnapshotApplyTargetStore dp07SnapshotApplyTargetStore(SnapshotFactApplyMapper mapper) {
        return new SnapshotApplyTargetStore(mapper);
    }

    @Bean
    Dp07InventorySnapshotBatchWriter dp07InventorySnapshotBatchWriter(
            OfficialWarehouseStatisticsMapper mapper,
            InventorySnapshotRuntimeMapper runtimeMapper,
            ObjectMapper objectMapper,
            SnapshotApplyTargetStore targets
    ) {
        return new Dp07InventorySnapshotBatchWriter(
                mapper, runtimeMapper, objectMapper, targets
        );
    }

    @Bean
    DataPullJob dp07aInventorySnapshotJob(
            NoonDataPullScopeMapper scopeMapper,
            Dp07InventorySnapshotProvider provider,
            @Qualifier("dp07aSnapshotStageStore")
            SnapshotStageStore<Dp07InventorySnapshotItem> stageStore,
            Dp07InventorySnapshotWriter writer,
            ProviderWaitTransition providerWaitTransition
    ) {
        CompleteSnapshotEngine<Dp07InventorySnapshotItem> engine = new CompleteSnapshotEngine<>(
                OperationCode.DP07A,
                provider,
                stageStore,
                writer,
                new SnapshotCheckpointCodec(),
                providerWaitTransition
        );
        return new OperationHandlerDataPullJob(
                OperationCode.DP07A,
                CHANNEL,
                INITIAL_STEP,
                new NoonDataPullScopeSource(scopeMapper),
                engine
        );
    }
}
