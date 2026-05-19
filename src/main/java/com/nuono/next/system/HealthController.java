package com.nuono.next.system;

import com.nuono.next.foundation.FoundationOverview;
import com.nuono.next.foundation.FoundationUserDetail;
import com.nuono.next.foundation.LocalDbFoundationOverviewService;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/system")
public class HealthController {

    private final Environment environment;
    private final BootstrapProperties bootstrapProperties;
    private final ObjectProvider<LocalDbBootstrapStatusService> localDbBootstrapStatusServiceProvider;
    private final ObjectProvider<LocalDbFoundationOverviewService> localDbFoundationOverviewServiceProvider;

    public HealthController(
            Environment environment,
            BootstrapProperties bootstrapProperties,
            ObjectProvider<LocalDbBootstrapStatusService> localDbBootstrapStatusServiceProvider,
            ObjectProvider<LocalDbFoundationOverviewService> localDbFoundationOverviewServiceProvider
    ) {
        this.environment = environment;
        this.bootstrapProperties = bootstrapProperties;
        this.localDbBootstrapStatusServiceProvider = localDbBootstrapStatusServiceProvider;
        this.localDbFoundationOverviewServiceProvider = localDbFoundationOverviewServiceProvider;
    }

    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application", "nuono-next-backend");
        payload.put("status", "UP");
        payload.put("phase", bootstrapProperties.isDbEnabled() ? "LOCAL_DB_READY" : "LOCAL_BOOTSTRAP");
        payload.put("time", OffsetDateTime.now().toString());
        payload.put("nextModules", List.of("auth", "user", "role", "store", "binding"));
        payload.put("profiles", Arrays.asList(environment.getActiveProfiles()));
        payload.put("database", describeDatabase());
        return payload;
    }

    private Map<String, Object> describeDatabase() {
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("enabled", bootstrapProperties.isDbEnabled());
        database.put("schema", bootstrapProperties.getSchema());
        database.put("expectedCoreTables", bootstrapProperties.getExpectedCoreTables());

        LocalDbBootstrapStatusService statusService = localDbBootstrapStatusServiceProvider.getIfAvailable();
        if (statusService != null) {
            database.putAll(statusService.describe());
        } else {
            database.put("mode", "bootstrap-only");
            database.put("message", "当前仍在无数据库骨架模式。切换到 local-db profile 后可检查本地库状态。");
        }

        return database;
    }

    @GetMapping("/foundation-overview")
    public FoundationOverview foundationOverview() {
        LocalDbFoundationOverviewService overviewService = localDbFoundationOverviewServiceProvider.getIfAvailable();
        if (overviewService != null) {
            return overviewService.buildOverview();
        }

        FoundationOverview overview = new FoundationOverview();
        overview.setMode("bootstrap-only");
        overview.setReady(false);
        overview.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取本地样本数据。");
        return overview;
    }

    @GetMapping("/foundation-user-detail")
    public FoundationUserDetail foundationUserDetail(@RequestParam("userId") Long userId) {
        LocalDbFoundationOverviewService overviewService = localDbFoundationOverviewServiceProvider.getIfAvailable();
        if (overviewService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前仍在无数据库骨架模式。");
        }
        try {
            return overviewService.buildUserDetail(userId);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }
}
