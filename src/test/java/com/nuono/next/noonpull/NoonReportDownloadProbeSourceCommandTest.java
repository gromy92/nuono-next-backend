package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class NoonReportDownloadProbeSourceCommandTest {
    @TempDir
    Path directory;
    private static final Clock NOW = Clock.fixed(
            Instant.parse("2026-08-20T19:00:00Z"),
            ZoneOffset.UTC
    );
    private static final String PREFIX =
            "https://storage.googleapis.com/noonprd-mp-gcs--partner-impex/report.csv?";

    @Test
    void recognizesOnlyTheDedicatedCommand() {
        assertTrue(NoonReportDownloadProbeSourceCommand.handles(
                new String[]{"dp-report-download-probe-source"}
        ));
        assertFalse(NoonReportDownloadProbeSourceCommand.handles(new String[]{"other"}));
        assertFalse(NoonReportDownloadProbeSourceCommand.handles(null));
    }

    @Test
    void acceptsLegacyAndV4SignaturesOnlyWhenAtLeastFifteenMinutesRemain() {
        assertTrue(NoonReportDownloadProbeSourceSupport.freshNoonUrl(
                PREFIX + "Expires=1787254201&GoogleAccessId=id&Signature=value", NOW
        ));
        assertFalse(NoonReportDownloadProbeSourceSupport.freshNoonUrl(
                PREFIX + "Expires=1787253000&GoogleAccessId=id&Signature=value", NOW
        ));
        assertTrue(NoonReportDownloadProbeSourceSupport.freshNoonUrl(
                PREFIX + "X-Goog-Date=20260820T190000Z&X-Goog-Expires=3600&X-Goog-Signature=value",
                NOW
        ));
    }

    @Test
    void rejectsWrongEndpointMissingExpiryAndDuplicateQueryKeys() {
        assertFalse(NoonReportDownloadProbeSourceSupport.freshNoonUrl(
                "https://example.com/report.csv?Expires=1787254201", NOW
        ));
        assertFalse(NoonReportDownloadProbeSourceSupport.freshNoonUrl(
                PREFIX + "Signature=value", NOW
        ));
        assertFalse(NoonReportDownloadProbeSourceSupport.freshNoonUrl(
                PREFIX + "Expires=1787254201&Expires=1787255201", NOW
        ));
    }

    @Test
    void obtainsTheSourceWithExactlyOneUnverifiedReadRequest() {
        NoonPullStoreBindingResolver resolver = mock(NoonPullStoreBindingResolver.class);
        NoonPullGatewaySessionFactory sessions = mock(NoonPullGatewaySessionFactory.class);
        NoonPullGatewaySession session = mock(NoonPullGatewaySession.class);
        NoonPullStoreBinding binding = new NoonPullStoreBinding(
                307L, "PRJ108065", "STR108065-NSA", "SA",
                "108065", "merchant", "cookie"
        );
        NoonReportPullRequest request = NoonReportPullRequest.builder()
                .ownerUserId(307L).storeCode("STR108065-NSA").siteCode("SA")
                .dataDomain(NoonPullDataDomain.ORDER).reportType("ORDER")
                .dateFrom(java.time.LocalDate.of(2026, 8, 19))
                .dateTo(java.time.LocalDate.of(2026, 8, 19)).build();
        ObjectMapper json = new ObjectMapper();
        ObjectNode response = json.createObjectNode().put("status", "Success");
        response.putObject("export_attachment").put("url", PREFIX + "Expires=1787254201");
        when(resolver.resolve(request)).thenReturn(binding);
        when(sessions.openOneShot(binding)).thenReturn(session);
        when(session.postJsonOnce(anyString(), any(), eq(false), anyMap()))
                .thenReturn(response);

        assertEquals(PREFIX + "Expires=1787254201",
                NoonReportDownloadProbeSourceCommand.pollLatestOnce(
                        json, resolver, sessions, "https://reports.noon.partners/latest", request
                ));
        verify(sessions).openOneShot(binding);
        verify(sessions, never()).login(any());
        verify(session).postJsonOnce(anyString(), any(), eq(false), anyMap());
    }

    @Test
    void exposesTheExplicitMapperBeanAsItsInterfaceType() throws Exception {
        Environment environment = new Environment(
                "probe-test",
                new JdbcTransactionFactory(),
                mock(DataSource.class)
        );
        SqlSessionFactory sqlSessionFactory = mock(SqlSessionFactory.class);
        when(sqlSessionFactory.getConfiguration())
                .thenReturn(new Configuration(environment));

        StoreSyncMapper mapper = new NoonReportDownloadProbeSourceCommand
                .ProbeConfiguration().storeSyncMapper(sqlSessionFactory);

        assertTrue(StoreSyncMapper.class.isInstance(mapper));
    }

    @Test
    void exposesTheJdbcTemplateRequiredByFreshSourceResolution() {
        com.zaxxer.hikari.HikariDataSource dataSource =
                mock(com.zaxxer.hikari.HikariDataSource.class);

        JdbcTemplate jdbc = new NoonReportDownloadProbeSourceCommand
                .ProbeConfiguration().jdbcTemplate(dataSource);

        assertEquals(dataSource, jdbc.getDataSource());
    }

    @Test
    void missingDependencyDiagnosticNamesOnlyTheBeanType() {
        assertEquals(
                "NoSuchBeanDefinitionException.JdbcTemplate",
                NoonReportDownloadProbeSourceSupport.safeMessage(
                        new NoSuchBeanDefinitionException(JdbcTemplate.class)
                )
        );
    }

    @Test
    void isolatedProbeContextResolvesEveryFreshSourceDependency() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProbeDependencyGraph.class)
                .withInitializer(context ->
                        context.getEnvironment().setActiveProfiles("local-db"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcTemplate.class);
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(NoonPullStoreBindingResolver.class);
                    assertThat(context).hasSingleBean(NoonPullGatewaySessionFactory.class);
                });
    }

    @Test
    void rejectsDuplicateEnvironmentAndCreatesANewOwnerOnlySourceFile() throws Exception {
        Path env = directory.resolve("duplicate.env");
        Files.writeString(env, "NUONO_NEXT_DB_URL=first\nNUONO_NEXT_DB_URL=second\n");
        assertThrows(IllegalArgumentException.class,
                () -> NoonReportDownloadProbeSourceSupport.loadEnvironment(env));

        Path source = directory.resolve("source-url");
        NoonReportDownloadProbeSourceSupport.writeSecret(source, "signed-value");
        assertEquals("signed-value\n", Files.readString(source));
        assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(
                Files.getPosixFilePermissions(source)
        ));
        assertThrows(IllegalArgumentException.class,
                () -> NoonReportDownloadProbeSourceSupport.writeSecret(source, "replacement"));
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @Import({
            NoonPullStoreBindingResolver.class,
            NoonSessionGateway.class
    })
    static class ProbeDependencyGraph {
        @Bean
        HikariDataSource dataSource() {
            return mock(HikariDataSource.class);
        }

        @Bean
        StoreSyncMapper storeSyncMapper() {
            return mock(StoreSyncMapper.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JdbcTemplate jdbcTemplate(HikariDataSource dataSource) {
            return new NoonReportDownloadProbeSourceCommand.ProbeConfiguration()
                    .jdbcTemplate(dataSource);
        }

        @Bean
        NoonPullGatewaySessionFactory noonPullGatewaySessionFactory(
                NoonSessionGateway gateway
        ) {
            return new NoonReportDownloadProbeSourceCommand.ProbeConfiguration()
                    .noonPullGatewaySessionFactory(gateway);
        }
    }
}
