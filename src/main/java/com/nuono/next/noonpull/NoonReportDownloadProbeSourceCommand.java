package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noon.NoonCatalogApiRoutes;
import com.nuono.next.noon.NoonReportStatusProbeTransport;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/** Release-only command that obtains one fresh, secret report URL without starting schedulers. */
public final class NoonReportDownloadProbeSourceCommand {
    private static final String COMMAND = "dp-report-download-probe-source";
    private static final String DEFAULT_STATUS_URL = NoonCatalogApiRoutes.EXPORT_STATUS;
    private NoonReportDownloadProbeSourceCommand() {
    }

    public static boolean handles(String[] args) {
        return args != null && args.length > 0 && COMMAND.equals(args[0]);
    }

    public static int run(String[] args) {
        try {
            Arguments command = Arguments.parse(args);
            Map<String, Object> values = NoonReportDownloadProbeSourceSupport
                    .loadEnvironment(command.envFile);
            try (HikariDataSource dataSource = dataSource(values)) {
                String source = resolveFreshSource(
                        new JdbcTemplate(dataSource),
                        transport(new ObjectMapper(), values),
                        value(values,
                                "NUONO_NOON_PULL_REAL_PROVIDER_REPORT_EXPORT_STATUS_URL",
                                DEFAULT_STATUS_URL),
                        Clock.systemUTC()
                );
                NoonReportDownloadProbeSourceSupport.writeSecret(
                        command.outputFile, source
                );
                System.out.println("DP_REPORT_PROBE_SOURCE=FRESH");
                System.out.println("DP_REPORT_PROBE_SOURCE_SHA256="
                        + NoonReportDownloadProbeSourceSupport.sha256(source));
            }
            return 0;
        } catch (RuntimeException | IOException failure) {
            System.err.println("DP_REPORT_PROBE_SOURCE=FAIL:"
                    + NoonReportDownloadProbeSourceSupport.safeMessage(failure));
            return 2;
        }
    }

    static String resolveFreshSource(
            JdbcTemplate jdbc,
            NoonReportStatusProbeTransport transport,
            String statusUrl,
            Clock clock
    ) {
        List<Scope> scopes = jdbc.query(
                "SELECT store_code, site_code, project_code, "
                        + "report_export_id, noon_partner_cookie FROM ("
                        + "SELECT t.owner_user_id, t.store_code, t.site_code, "
                        + "us.project_code, t.report_export_id, up.noon_partner_cookie, "
                        + "t.gmt_updated, "
                        + "ROW_NUMBER() OVER (PARTITION BY t.owner_user_id, us.project_code "
                        + "ORDER BY t.gmt_updated DESC, t.id DESC) AS scope_rank "
                        + "FROM noon_pull_task t JOIN user_store us "
                        + "ON us.user_id=t.owner_user_id "
                        + "AND BINARY us.store_code=BINARY t.store_code "
                        + "AND us.is_deleted=b'0' JOIN user_project up "
                        + "ON up.user_id=t.owner_user_id "
                        + "AND BINARY up.project_code=BINARY us.project_code "
                        + "AND up.is_deleted=b'0' "
                        + "WHERE t.is_deleted=b'0' AND t.pull_type='REPORT' "
                        + "AND t.report_export_status='READY' "
                        + "AND t.data_domain IN ('SALES','FINANCE_TRANSACTION') "
                        + "AND NULLIF(t.report_export_id, '') IS NOT NULL "
                        + "AND NULLIF(up.noon_partner_cookie, '') IS NOT NULL"
                        + ") candidates WHERE scope_rank=1 "
                        + "ORDER BY gmt_updated DESC LIMIT 10",
                (row, ignored) -> new Scope(
                        row.getString("store_code"),
                        row.getString("site_code"),
                        row.getString("project_code"),
                        row.getString("report_export_id"),
                        row.getString("noon_partner_cookie")
                )
        );
        RuntimeException lastFailure = null;
        for (Scope scope : scopes) {
            try {
                String source = pollExistingExportOnce(
                        transport, statusUrl,
                        scope.projectCode, scope.storeCode, scope.siteCode,
                        scope.exportId, scope.persistedCookie
                );
                if (NoonReportDownloadProbeSourceSupport.freshNoonUrl(source, clock)) {
                    return source;
                }
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        if (lastFailure != null) {
            throw new IllegalStateException("fresh Noon report URL unavailable", lastFailure);
        }
        throw new IllegalStateException("fresh Noon report URL unavailable");
    }

    static NoonReportStatusProbeTransport transport(
            ObjectMapper json,
            Map<String, Object> values
    ) {
        return new NoonReportStatusProbeTransport(
                json,
                Boolean.parseBoolean(value(values, "NUONO_NOON_PROXY_ENABLED", "false")),
                value(values, "NUONO_NOON_PROXY_TYPE", "HTTP"),
                value(values, "NUONO_NOON_PROXY_HOST", ""),
                integer(values, "NUONO_NOON_PROXY_PORT", 0),
                value(values, "NUONO_NOON_PROXY_PROVIDER_URL", ""),
                value(values, "NUONO_NOON_PROXY_MODE", "AUTO"),
                value(values, "NUONO_NOON_USER_AGENT", ""),
                value(values, "NUONO_NOON_ACCEPT_LANGUAGE", "")
        );
    }

    private static HikariDataSource dataSource(Map<String, Object> values) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(required(values, "NUONO_NEXT_DB_URL"));
        hikari.setUsername(required(values, "NUONO_NEXT_DB_USERNAME"));
        hikari.setPassword(required(values, "NUONO_NEXT_DB_PASSWORD"));
        hikari.setMaximumPoolSize(2);
        hikari.setMinimumIdle(0);
        hikari.setConnectionTimeout(10_000L);
        hikari.addDataSourceProperty("connectTimeout", "10000");
        hikari.addDataSourceProperty("socketTimeout", "30000");
        return new HikariDataSource(hikari);
    }

    private static String required(Map<String, Object> values, String name) {
        String result = value(values, name, "");
        if (!StringUtils.hasText(result)) {
            throw new IllegalArgumentException("required probe database setting is missing");
        }
        return result;
    }

    private static String value(Map<String, Object> values, String name, String fallback) {
        Object raw = values.get(name);
        return raw == null ? fallback : String.valueOf(raw).trim();
    }

    private static int integer(Map<String, Object> values, String name, int fallback) {
        String raw = value(values, name, "");
        return raw.isEmpty() ? fallback : Integer.parseInt(raw);
    }

    static String pollExistingExportOnce(
            NoonReportStatusProbeTransport transport,
            String statusUrl,
            String projectCode,
            String storeCode,
            String siteCode,
            String exportId,
            String persistedCookie
    ) {
        JsonNode root = transport.poll(
                statusUrl,
                projectCode,
                storeCode,
                siteCode,
                exportId,
                persistedCookie
        );
        JsonNode export = root.path("export");
        String status = export.path("status_code").asText();
        if (!"COMPLETE".equalsIgnoreCase(status)
                && !"COMPLETED".equalsIgnoreCase(status)) {
            return null;
        }
        for (String name : List.of("download_url", "downloadUrl", "download")) {
            String value = export.path(name).asText(null);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static final class Scope {
        private final String storeCode;
        private final String siteCode;
        private final String projectCode;
        private final String exportId;
        private final String persistedCookie;

        private Scope(
                String storeCode,
                String siteCode,
                String projectCode,
                String exportId,
                String persistedCookie
        ) {
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.projectCode = projectCode;
            this.exportId = exportId;
            this.persistedCookie = persistedCookie;
        }
    }

    private static final class Arguments {
        private final Path envFile;
        private final Path outputFile;

        private Arguments(Path envFile, Path outputFile) {
            this.envFile = envFile;
            this.outputFile = outputFile;
        }

        static Arguments parse(String[] args) {
            if (!handles(args) || args.length != 5
                    || !"--env-file".equals(args[1])
                    || !"--output-file".equals(args[3])) {
                throw new IllegalArgumentException("invalid probe source command arguments");
            }
            return new Arguments(Path.of(args[2]).toAbsolutePath().normalize(),
                    Path.of(args[4]).toAbsolutePath().normalize());
        }
    }

}
