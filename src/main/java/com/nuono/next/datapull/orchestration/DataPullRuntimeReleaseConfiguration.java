package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.schedule.DataPullScopeAdmissionStore;
import com.nuono.next.infrastructure.mapper.DataPullLegacyCutoverMapper;
import com.nuono.next.infrastructure.mapper.DataPullReleaseDatabaseMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import com.nuono.next.infrastructure.mapper.Dp08LegacyTaskReconciliationMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Release-only evidence composition, kept separate from runtime execution wiring. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
class DataPullRuntimeReleaseConfiguration {

    @Bean
    DataPullRuntimeReleaseEvidenceRegistry dataPullRuntimeReleaseEvidenceRegistry(
            List<DataPullRuntimeReleaseEvidence> evidenceProviders
    ) {
        return new DataPullRuntimeReleaseEvidenceRegistry(evidenceProviders);
    }

    @Bean
    DataPullRuntimeReleaseEvidence dataPullCutoverReconciliationEvidence(
            DataPullJobRegistry jobs,
            DataPullScheduleAnchorMapper mapper,
            DataPullScopeAdmissionStore admissions
    ) {
        return new DataPullCutoverReconciliationEvidence(jobs, mapper, admissions);
    }

    @Bean
    DataPullRuntimeReleaseEvidence dp08LegacyTaskReconciliationEvidence(
            Dp08LegacyTaskReconciliationMapper mapper
    ) {
        return new Dp08LegacyTaskReconciliationEvidence(mapper);
    }

    @Bean
    DataPullRuntimeReleaseEvidence dataPullLegacyTaskDrainEvidence(
            DataPullLegacyCutoverMapper mapper
    ) {
        return new DataPullLegacyTaskDrainEvidence(mapper);
    }

    @Bean
    DataPullRuntimeReleaseEvidence dataPullManagedReleaseProvenanceEvidence(
            DataPullReleaseDatabaseMapper mapper,
            Environment environment
    ) {
        return new DataPullManagedReleaseProvenanceEvidence(mapper, environment);
    }

    @Bean
    DataPullRuntimeReleaseEvidence dataPullRuntimeSchemaEvidence(
            DataPullReleaseDatabaseMapper mapper,
            Environment environment
    ) {
        return new DataPullRuntimeSchemaEvidence(mapper, environment);
    }

    @Bean
    DataPullRuntimeReleaseGate dataPullRuntimeReleaseGate(
            DataPullRuntimeReleaseEvidenceRegistry evidenceRegistry
    ) {
        return new DataPullRuntimeReleaseGate(evidenceRegistry);
    }
}
