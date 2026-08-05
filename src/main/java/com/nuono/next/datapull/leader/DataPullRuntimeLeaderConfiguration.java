package com.nuono.next.datapull.leader;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.infrastructure.mapper.DataPullRuntimeLeaderMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Composition root for the independent database scheduler-leader Module. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class DataPullRuntimeLeaderConfiguration {

    @Bean
    DataPullRuntimeLeaderStore dataPullRuntimeLeaderStore(DataPullRuntimeLeaderMapper mapper) {
        return new MyBatisDataPullRuntimeLeaderStore(mapper);
    }

    @Bean
    DataPullRuntimeLeadership dataPullRuntimeLeadership(
            DataPullRuntimeLeaderStore store,
            DataPullRuntimeProperties properties
    ) {
        properties.validate();
        return new DataPullRuntimeLeadership(
                store,
                runtimeOwner(),
                properties.leaderLeaseDuration()
        );
    }

    private String runtimeOwner() {
        return "dp:" + java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
                + ":" + java.util.UUID.randomUUID();
    }
}
