package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/** Release-only command that obtains one fresh, secret report URL without starting schedulers. */
public final class NoonReportDownloadProbeSourceCommand {
    private static final String COMMAND = "dp-report-download-probe-source";
    private static final String DEFAULT_LATEST_URL =
            "https://reports.noon.partners/_vs/mp/"
                    + "mp-inventory-health-api-sales-dashboard/export/latest";
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
        ObjectMapper json = context.getBean(ObjectMapper.class);
        NoonPullStoreBindingResolver resolver =
                context.getBean(NoonPullStoreBindingResolver.class);
        NoonPullGatewaySessionFactory sessions =
                context.getBean(NoonPullGatewaySessionFactory.class);
        Environment environment = context.getEnvironment();
        String latestUrl = environment.getProperty(
                "nuono.noon.pull.real-provider.sales-dashboard-export.latest-url",
                DEFAULT_LATEST_URL
        );
        List<Scope> scopes = jdbc.query(
                "SELECT owner_user_id, store_code, site_code, target_date_from, target_date_to "
                        + "FROM noon_pull_task WHERE is_deleted=b'0' "
                        + "AND pull_type='REPORT' AND target_date_from IS NOT NULL "
                        + "AND target_date_to IS NOT NULL AND report_export_id IS NOT NULL "
                        + "GROUP BY owner_user_id, store_code, site_code, "
                        + "target_date_from, target_date_to "
                        + "ORDER BY MAX(gmt_updated) DESC LIMIT 5",
                (row, ignored) -> new Scope(
                        row.getLong("owner_user_id"),
                        row.getString("store_code"),
                        row.getString("site_code"),
                        row.getDate("target_date_from").toLocalDate(),
                        row.getDate("target_date_to").toLocalDate()
                )
        );
        RuntimeException lastFailure = null;
        for (Scope scope : scopes) {
            try {
                String source = pollLatestOnce(
                        json, resolver, sessions, latestUrl, scope.request()
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

    static String pollLatestOnce(
            ObjectMapper json,
            NoonPullStoreBindingResolver resolver,
            NoonPullGatewaySessionFactory sessions,
            String latestUrl,
            NoonReportPullRequest request
    ) {
        NoonPullStoreBinding binding = resolver.resolve(request);
        ObjectNode body = json.createObjectNode();
        body.put("country_code", binding.getSiteCode());
        body.set("filters", json.createObjectNode());
        body.put("search", "");
        body.put("from_date", request.getDateFrom().toString());
        body.put("to_date", request.getDateTo().toString());
        String site = binding.getSiteCode().toLowerCase(java.util.Locale.ROOT);
        JsonNode root = sessions.openOneShot(binding).postJsonOnce(
                latestUrl,
                body,
                false,
                Map.of("X-Project", binding.getProjectCode(),
                        "X-Locale", "en-" + site, "X-Lang", "en")
        );
        if (!"Success".equalsIgnoreCase(root.path("status").asText())) {
            return null;
        }
        JsonNode attachment = root.path("export_attachment");
        for (String name : List.of("url", "download_url", "downloadUrl")) {
            String value = attachment.path(name).asText(null);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static final class Scope {
        private final long ownerUserId;
        private final String storeCode;
        private final String siteCode;
        private final LocalDate from;
        private final LocalDate to;

        private Scope(
                long ownerUserId,
                String storeCode,
                String siteCode,
                LocalDate from,
                LocalDate to
        ) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.from = from;
            this.to = to;
        }

        NoonReportPullRequest request() {
            return NoonReportPullRequest.builder()
                    .ownerUserId(ownerUserId)
                    .storeCode(storeCode)
                    .siteCode(siteCode)
                    .dataDomain(NoonPullDataDomain.ORDER)
                    .reportType("ORDER")
                    .dateFrom(from)
                    .dateTo(to)
                    .build();
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
    @Import({
            NoonPullStoreBindingResolver.class,
            com.nuono.next.noon.NoonSessionGateway.class
    })
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
        SqlSessionFactory sqlSessionFactory(HikariDataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            return factory.getObject();
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
        NoonPullGatewaySessionFactory noonPullGatewaySessionFactory(
                com.nuono.next.noon.NoonSessionGateway gateway
        ) {
            return new NoonSessionGatewayPullSessionFactory(gateway);
        }

        @Bean
        StoreSyncMapper storeSyncMapper(
                SqlSessionFactory sqlSessionFactory
        ) throws Exception {
            MapperFactoryBean<StoreSyncMapper> mapper =
                    new MapperFactoryBean<>(StoreSyncMapper.class);
            mapper.setSqlSessionFactory(sqlSessionFactory);
            mapper.afterPropertiesSet();
            return mapper.getObject();
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
