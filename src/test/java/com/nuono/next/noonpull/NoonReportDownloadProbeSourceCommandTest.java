package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonReportStatusProbeTransport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.test.util.ReflectionTestUtils;
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
    void obtainsTheSourceThroughTheMinimalReadOnlyTransport() {
        NoonReportStatusProbeTransport transport = mock(NoonReportStatusProbeTransport.class);
        ObjectMapper json = new ObjectMapper();
        ObjectNode response = json.createObjectNode();
        response.putObject("export")
                .put("status_code", "COMPLETE")
                .put("download_url", PREFIX + "Expires=1787254201");
        when(transport.poll(
                "https://noon-catalog.noon.partners/status",
                "PRJ108065", "STR108065-NSA", "SA", "EXP4CP4RTOQO", "sid=persisted"
        )).thenReturn(response);

        assertEquals(PREFIX + "Expires=1787254201",
                NoonReportDownloadProbeSourceCommand.pollExistingExportOnce(
                        transport, "https://noon-catalog.noon.partners/status",
                        "PRJ108065", "STR108065-NSA", "SA", "EXP4CP4RTOQO", "sid=persisted"
                ));
        verify(transport).poll(
                "https://noon-catalog.noon.partners/status",
                "PRJ108065", "STR108065-NSA", "SA", "EXP4CP4RTOQO", "sid=persisted"
        );
    }

    @Test
    void releaseCommandContainsNoConfigurationCandidateForProductionScanning() {
        assertFalse(Arrays.stream(NoonReportDownloadProbeSourceCommand.class
                        .getDeclaredClasses())
                .anyMatch(type -> type.isAnnotationPresent(
                        org.springframework.context.annotation.Configuration.class)));
    }

    @Test
    void isolatedTransportReadsTheExactUppercaseEnvironmentKeys() {
        NoonReportStatusProbeTransport transport =
                NoonReportDownloadProbeSourceCommand.transport(
                        new ObjectMapper(),
                        Map.of(
                                "NUONO_NOON_PROXY_ENABLED", "true",
                                "NUONO_NOON_PROXY_TYPE", "HTTP",
                                "NUONO_NOON_PROXY_PROVIDER_URL", "https://provider.test/route",
                                "NUONO_NOON_PROXY_MODE", "PROVIDER"
                        ));

        assertEquals("PROVIDER", ReflectionTestUtils.getField(transport, "proxyMode"));
        Object routes = ReflectionTestUtils.getField(transport, "routes");
        assertEquals(true, ReflectionTestUtils.getField(routes, "proxyEnabled"));
        assertEquals("https://provider.test/route",
                ReflectionTestUtils.getField(routes, "proxyProviderUrl"));
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
    void proxyProviderDiagnosticFindsOnlySafeStatusAndBusinessCode() {
        IllegalStateException provider = new IllegalStateException(
                "Noon proxy provider unavailable: HTTP 400 CODE 205"
        );
        IllegalStateException command = new IllegalStateException(
                "fresh Noon report URL unavailable", provider
        );

        assertEquals(
                "PROXY_PROVIDER_HTTP_400_205",
                NoonReportDownloadProbeSourceSupport.safeMessage(command)
        );
    }

    @Test
    void reportStatusDiagnosticExposesOnlyTheHttpCode() {
        IllegalStateException command = new IllegalStateException(
                "fresh Noon report URL unavailable",
                new NoonHttpException(403, "secret edge response", "/status")
        );

        assertEquals(
                "REPORT_STATUS_HTTP_403",
                NoonReportDownloadProbeSourceSupport.safeMessage(command)
        );
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
}
