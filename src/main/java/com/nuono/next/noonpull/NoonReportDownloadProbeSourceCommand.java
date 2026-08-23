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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
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
            values.put("logging.level.root", "ERROR");
            values.put("spring.main.banner-mode", "off");
            values.put("spring.jmx.enabled", "false");
            StandardEnvironment environment = new StandardEnvironment();
            environment.setActiveProfiles("local-db");
            environment.getPropertySources().addFirst(
                    new MapPropertySource("probe-env", values)
            );
            SpringApplication application = new SpringApplication(ProbeConfiguration.class);
            application.setEnvironment(environment);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setRegisterShutdownHook(false);
            application.setLogStartupInfo(false);
            try (ConfigurableApplicationContext context = application.run()) {
                String source = resolveFreshSource(context, Clock.systemUTC());
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

    static String resolveFreshSource(ConfigurableApplicationContext context, Clock clock) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        NoonReportStatusProbeTransport transport =
                context.getBean(NoonReportStatusProbeTransport.class);
        Environment environment = context.getEnvironment();
        String statusUrl = environment.getProperty(
                "NUONO_NOON_PULL_REAL_PROVIDER_REPORT_EXPORT_STATUS_URL",
                DEFAULT_STATUS_URL
        );
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

    @Configuration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean(destroyMethod = "close")
        HikariDataSource dataSource(Environment environment) {
            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl(required(environment, "NUONO_NEXT_DB_URL"));
            hikari.setUsername(required(environment, "NUONO_NEXT_DB_USERNAME"));
            hikari.setPassword(required(environment, "NUONO_NEXT_DB_PASSWORD"));
            hikari.setMaximumPoolSize(2);
            hikari.setMinimumIdle(0);
            hikari.setConnectionTimeout(10_000L);
            hikari.addDataSourceProperty("connectTimeout", "10000");
            hikari.addDataSourceProperty("socketTimeout", "30000");
            return new HikariDataSource(hikari);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JdbcTemplate jdbcTemplate(HikariDataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        NoonReportStatusProbeTransport reportStatusProbeTransport(
                ObjectMapper json,
                Environment environment
        ) {
            return new NoonReportStatusProbeTransport(
                    json,
                    Boolean.parseBoolean(environment.getProperty(
                            "NUONO_NOON_PROXY_ENABLED", "false")),
                    environment.getProperty("NUONO_NOON_PROXY_TYPE", "HTTP"),
                    environment.getProperty("NUONO_NOON_PROXY_HOST", ""),
                    environment.getProperty("NUONO_NOON_PROXY_PORT", Integer.class, 0),
                    environment.getProperty("NUONO_NOON_PROXY_PROVIDER_URL", ""),
                    environment.getProperty("NUONO_NOON_PROXY_MODE", "AUTO"),
                    environment.getProperty("NUONO_NOON_USER_AGENT", ""),
                    environment.getProperty("NUONO_NOON_ACCEPT_LANGUAGE", "")
            );
        }

        private static String required(Environment environment, String name) {
            String value = environment.getProperty(name);
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("required probe database setting is missing");
            }
            return value.trim();
        }
    }
}
