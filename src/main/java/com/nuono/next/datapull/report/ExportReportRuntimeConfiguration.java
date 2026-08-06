package com.nuono.next.datapull.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.NoonDataPullScopeSource;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.infrastructure.mapper.DataPullReportArtifactChunkMapper;
import com.nuono.next.infrastructure.mapper.DataPullReportLocatorMapper;
import com.nuono.next.infrastructure.mapper.FbnReportBulkMapper;
import com.nuono.next.infrastructure.mapper.LegacyReportFactBulkMapper;
import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import com.nuono.next.infrastructure.mapper.ReportCreateAttemptMapper;
import com.nuono.next.infrastructure.mapper.ReportFactApplyMapper;
import com.nuono.next.infrastructure.mapper.ReportStageMapper;
import com.nuono.next.noonpull.NoonOrderReportAdapter;
import com.nuono.next.noonpull.NoonSalesReportAdapter;
import com.nuono.next.noonpull.RealNoonFinanceTransactionReportProvider;
import com.nuono.next.noonpull.RealNoonOrderReportSmokeProvider;
import com.nuono.next.noonpull.RealNoonSalesReportSmokeProvider;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnStageClassifier;
import com.nuono.next.orderfinance.NoonFinanceTransactionReportAdapter;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Composition root for DP-01/02/03/07-B report bridges. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class ExportReportRuntimeConfiguration {
    private static final Duration POLL_DELAY = Duration.ofMinutes(2);
    private static final Duration RECONCILE_DELAY = Duration.ofMinutes(5);
    private final NoonReportDefinitions definitions;
    public ExportReportRuntimeConfiguration(
            @Value("${nuono.noon.pull.real-provider.finance-transaction-report.export-category-code:"
                    + com.nuono.next.noonpull.NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE + "}")
            String financeTransactionReportType
    ) {
        this.definitions = new NoonReportDefinitions(financeTransactionReportType);
    }
    @Bean
    ReportArtifactStore reportArtifactStore(DataPullReportArtifactChunkMapper mapper) {
        return new MyBatisReportArtifactStore(mapper);
    }
    @Bean
    ReportDownloadLocatorVault reportDownloadLocatorVault(
            DataPullReportLocatorMapper mapper,
            @Value("${nuono.data-pull.report.locator-key-base64:}") String keyBase64
    ) {
        return new AesGcmReportDownloadLocatorVault(mapper, keyBase64);
    }
    @Bean
    ReportFactApplyGuard reportFactApplyGuard(ReportFactApplyMapper mapper) {
        return new MyBatisReportFactApplyGuard(mapper);
    }
    @Bean
    ReportStageStore reportStageStore(
            ReportStageMapper stageMapper,
            ReportFactApplyMapper applyMapper,
            LegacyReportFactBulkMapper legacyMapper,
            FbnReportBulkMapper fbnMapper
    ) {
        return new MyBatisReportStageStore(stageMapper, applyMapper, legacyMapper, fbnMapper);
    }

    @Bean
    ReportCreateAttemptFence reportCreateAttemptFence(ReportCreateAttemptMapper mapper) {
        return new MyBatisReportCreateAttemptFence(mapper);
    }

    @Bean("dp01ReportProvider")
    LegacyNoonReportProviderBridge dp01ReportProvider(
            RealNoonSalesReportSmokeProvider delegate,
            ReportDownloadLocatorVault vault,
            ReportArtifactStore artifacts
    ) {
        return legacy(
                definitions.dp01(),
                delegate,
                LegacyNoonReportProviderBridge.ReadbackMode.UNAVAILABLE,
                LegacyNoonReportProviderBridge.EmptyProofMode.UNAVAILABLE,
                LegacyNoonReportProviderBridge.ArtifactCompletenessMode.UNAVAILABLE,
                vault,
                artifacts
        );
    }

    @Bean("dp02ReportProvider")
    LegacyNoonReportProviderBridge dp02ReportProvider(
            RealNoonOrderReportSmokeProvider delegate,
            ReportDownloadLocatorVault vault,
            ReportArtifactStore artifacts
    ) {
        return legacy(
                definitions.dp02(),
                delegate,
                // Noon sales-dashboard /latest has returned an older rolling export for
                // a requested single-day window in production. It cannot prove the create.
                LegacyNoonReportProviderBridge.ReadbackMode.UNAVAILABLE,
                LegacyNoonReportProviderBridge.EmptyProofMode.UNAVAILABLE,
                LegacyNoonReportProviderBridge.ArtifactCompletenessMode.UNAVAILABLE,
                vault,
                artifacts
        );
    }

    @Bean("dp03ReportProvider")
    LegacyNoonReportProviderBridge dp03ReportProvider(
            RealNoonFinanceTransactionReportProvider delegate,
            ReportDownloadLocatorVault vault,
            ReportArtifactStore artifacts
    ) {
        return legacy(
                definitions.dp03(),
                delegate,
                LegacyNoonReportProviderBridge.ReadbackMode.UNAVAILABLE,
                LegacyNoonReportProviderBridge.EmptyProofMode.UNAVAILABLE,
                LegacyNoonReportProviderBridge.ArtifactCompletenessMode.UNAVAILABLE,
                vault,
                artifacts
        );
    }

    @Bean("dp07bReportProvider")
    FbnReceivedExportReportProvider dp07bReportProvider(
            OfficialWarehouseFbnExportProvider delegate,
            FbnReportDownloadTransport downloadTransport,
            ReportDownloadLocatorVault vault,
            ReportArtifactStore artifacts
    ) {
        return new FbnReceivedExportReportProvider(
                definitions.dp07b(),
                delegate,
                downloadTransport,
                vault,
                artifacts,
                ReportProviderCapabilities.EmptyProofEvidence.UNAVAILABLE,
                ReportProviderCapabilities.ArtifactCompletenessEvidence.UNAVAILABLE
        );
    }

    @Bean
    ReportRuntimeReleaseEvidence reportRuntimeReleaseEvidence(
            @Qualifier("dp01ReportProvider") LegacyNoonReportProviderBridge dp01,
            @Qualifier("dp02ReportProvider") LegacyNoonReportProviderBridge dp02,
            @Qualifier("dp03ReportProvider") LegacyNoonReportProviderBridge dp03,
            @Qualifier("dp07bReportProvider") FbnReceivedExportReportProvider dp07b
    ) {
        return new ReportRuntimeReleaseEvidence(dp01, dp02, dp03, dp07b);
    }

    @Bean("dp01ReportJob")
    DataPullJob dp01ReportJob(
            NoonDataPullScopeMapper scopes,
            @Qualifier("dp01ReportProvider") ExportReportProvider provider,
            NoonSalesReportAdapter adapter,
            ReportArtifactStore artifacts,
            ReportStageStore stageStore,
            ObjectMapper objectMapper,
            ReportCreateAttemptFence createAttemptFence,
            ProviderWaitTransition providerWaitTransition
    ) {
        NoonReportDefinition definition = definitions.dp01();
        return job(definition, scopes, provider,
                importer(definition, artifacts, stageStore, objectMapper,
                        adapter::requireStageHeader, adapter::classifyStageRows,
                        adapter::stageIdentity), createAttemptFence, providerWaitTransition);
    }

    @Bean("dp02ReportJob")
    DataPullJob dp02ReportJob(
            NoonDataPullScopeMapper scopes,
            @Qualifier("dp02ReportProvider") ExportReportProvider provider,
            NoonOrderReportAdapter adapter,
            ReportArtifactStore artifacts,
            ReportStageStore stageStore,
            ObjectMapper objectMapper,
            ReportCreateAttemptFence createAttemptFence,
            ProviderWaitTransition providerWaitTransition
    ) {
        NoonReportDefinition definition = definitions.dp02();
        return job(definition, scopes, provider,
                importer(definition, artifacts, stageStore, objectMapper,
                        adapter::requireStageHeader, adapter::classifyStageRows,
                        adapter::stageIdentity), createAttemptFence, providerWaitTransition);
    }

    @Bean("dp03ReportJob")
    DataPullJob dp03ReportJob(
            NoonDataPullScopeMapper scopes,
            @Qualifier("dp03ReportProvider") ExportReportProvider provider,
            NoonFinanceTransactionReportAdapter adapter,
            ReportArtifactStore artifacts,
            ReportStageStore stageStore,
            ObjectMapper objectMapper,
            ReportCreateAttemptFence createAttemptFence,
            ProviderWaitTransition providerWaitTransition
    ) {
        NoonReportDefinition definition = definitions.dp03();
        return job(definition, scopes, provider,
                importer(definition, artifacts, stageStore, objectMapper,
                        adapter::requireStageHeader, adapter::classifyStageRows,
                        adapter::stageIdentity), createAttemptFence, providerWaitTransition);
    }

    @Bean("dp07bReportJob")
    DataPullJob dp07bReportJob(
            NoonDataPullScopeMapper scopes,
            @Qualifier("dp07bReportProvider") ExportReportProvider provider,
            OfficialWarehouseFbnStageClassifier classifier,
            ReportArtifactStore artifacts,
            ReportStageStore stageStore,
            ObjectMapper objectMapper,
            ReportCreateAttemptFence createAttemptFence,
            ProviderWaitTransition providerWaitTransition
    ) {
        NoonReportDefinition definition = definitions.dp07b();
        return job(definition, scopes, provider,
                new FbnReceivedReportRuntimeImporter(
                        definition,
                        artifacts,
                        new JsonReportFactPlanAdapter<>(
                                classifier::requireHeader,
                                classifier::classify,
                                classifier::identity,
                                objectMapper
                        ),
                        stageStore,
                        objectMapper
                ),
                createAttemptFence, providerWaitTransition);
    }

    private ExportReportJob job(
            NoonReportDefinition definition,
            NoonDataPullScopeMapper scopeMapper,
            ExportReportProvider provider,
            ExportReportImporter importer,
            ReportCreateAttemptFence createAttemptFence,
            ProviderWaitTransition providerWaitTransition
    ) {
        NoonDataPullScopeSource source = new NoonDataPullScopeSource(scopeMapper);
        return new ExportReportJob(
                definition.getOperationCode(),
                definition.getProviderChannel(),
                source::listScopes,
                provider,
                importer,
                createAttemptFence,
                providerWaitTransition,
                POLL_DELAY,
                RECONCILE_DELAY
        );
    }

    private LegacyNoonReportProviderBridge legacy(
            NoonReportDefinition definition,
            com.nuono.next.noonpull.NoonReportProvider delegate,
            LegacyNoonReportProviderBridge.ReadbackMode readback,
            LegacyNoonReportProviderBridge.EmptyProofMode emptyProof,
            LegacyNoonReportProviderBridge.ArtifactCompletenessMode artifactCompleteness,
            ReportDownloadLocatorVault vault,
            ReportArtifactStore artifacts
    ) {
        return new LegacyNoonReportProviderBridge(
                definition,
                delegate,
                readback,
                emptyProof,
                artifactCompleteness,
                vault,
                artifacts
        );
    }

    private <T> LegacyNoonReportImporter importer(
            NoonReportDefinition definition,
            ReportArtifactStore artifacts,
            ReportStageStore stages,
            ObjectMapper objectMapper,
            Consumer<String[]> header,
            JsonReportFactPlanAdapter.Classifier<T> classifier,
            Function<T, String> identity
    ) {
        return new LegacyNoonReportImporter(
                definition, artifacts,
                new JsonReportFactPlanAdapter<>(header, classifier, identity, objectMapper),
                stages, objectMapper
        );
    }
}
