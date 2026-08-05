package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkRetentionMapper;
import com.nuono.next.infrastructure.mapper.DataPullReportArtifactMapper;
import com.nuono.next.infrastructure.mapper.DataPullReportLocatorMapper;
import com.nuono.next.infrastructure.mapper.ReportStageRetentionMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Adds report retention to the existing single DP runtime scheduler. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class ReportRetentionConfiguration {

    @Bean
    ReportRetentionCleaner reportRetentionCleaner(
            ReportStageRetentionMapper stages,
            DataPullReportArtifactChunkRetentionMapper chunks,
            DataPullReportArtifactMapper artifacts,
            DataPullReportLocatorMapper locators,
            ReportRetentionProperties properties
    ) {
        return new ReportRetentionCleaner(
                stages, chunks, artifacts, locators, properties
        );
    }
}
