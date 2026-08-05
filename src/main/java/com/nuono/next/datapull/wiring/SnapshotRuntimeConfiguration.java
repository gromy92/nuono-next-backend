package com.nuono.next.datapull.wiring;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.snapshot.SnapshotFactApplyGuard;
import com.nuono.next.infrastructure.mapper.SnapshotCarryProgressMapper;
import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Composition root for snapshot persistence shared by snapshot DP Modules. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class SnapshotRuntimeConfiguration {

    @Bean
    SnapshotFactApplyGuard snapshotFactApplyGuard(
            SnapshotFactApplyMapper mapper,
            SnapshotCarryProgressMapper carryMapper
    ) {
        return new SnapshotFactApplyGuard(mapper, carryMapper);
    }
}
