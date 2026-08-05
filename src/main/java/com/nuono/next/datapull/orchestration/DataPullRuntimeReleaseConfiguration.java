package com.nuono.next.datapull.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    DataPullRuntimeReleaseEvidence dp04StableSnapshotEvidence(
            Environment environment,
            ObjectMapper objectMapper
    ) {
        return managedContract(
                DataPullRuntimeReleaseRequirement.DP04_STABLE_SNAPSHOT,
                environment,
                objectMapper
        );
    }

    @Bean
    DataPullRuntimeReleaseEvidence dp06CompleteCampaignEnumerationEvidence(
            Environment environment,
            ObjectMapper objectMapper
    ) {
        return managedContract(
                DataPullRuntimeReleaseRequirement.DP06_COMPLETE_CAMPAIGN_ENUMERATION,
                environment,
                objectMapper
        );
    }

    @Bean
    DataPullRuntimeReleaseEvidence dp07aStableSnapshotEvidence(
            Environment environment,
            ObjectMapper objectMapper
    ) {
        return managedContract(
                DataPullRuntimeReleaseRequirement.DP07A_STABLE_SNAPSHOT,
                environment,
                objectMapper
        );
    }

    @Bean
    DataPullRuntimeReleaseEvidence dp10ModifiedTimeVisibilityEvidence(
            Environment environment,
            ObjectMapper objectMapper
    ) {
        return managedContract(
                DataPullRuntimeReleaseRequirement.DP10_MODIFIED_TIME_VISIBILITY_CONTRACT,
                environment,
                objectMapper
        );
    }

    @Bean
    DataPullRuntimeReleaseGate dataPullRuntimeReleaseGate(
            DataPullRuntimeReleaseEvidenceRegistry evidenceRegistry
    ) {
        return new DataPullRuntimeReleaseGate(evidenceRegistry);
    }

    private DataPullRuntimeReleaseEvidence managedContract(
            DataPullRuntimeReleaseRequirement requirement,
            Environment environment,
            ObjectMapper objectMapper
    ) {
        return new DataPullManagedContractEvidence(requirement, environment, objectMapper);
    }
}
