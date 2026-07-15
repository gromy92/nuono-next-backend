package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ZdLogisticsProviderAdapterTest {
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void normalizesExpressAndBoxRowsWithoutInventingProductLinesOrPackageSpecs() {
        ZdLogisticsProviderAdapter adapter = adapter(new LogisticsAutoSyncProperties(), Clock.systemUTC());

        PluginSyncCommand command = adapter.normalize(expressJson(), boxJson());

        assertThat(command.getSourceSystem()).isEqualTo("ZD");
        assertThat(command.getForwarderName()).isEqualTo("众鸫");
        assertThat(command.getBatches()).hasSize(1);
        assertThat(command.getSourceBatchExpectations()).hasSize(1);
        assertThat(command.getSourceBatchExpectations().get(0).getBoxNum()).isEqualTo(2);
        assertThat(command.getSourceBatchExpectations().get(0).getTotalQuantity()).isNull();

        var batch = command.getBatches().get(0);
        assertThat(batch.getBatchNo()).isEqualTo("GOKSA0037");
        assertThat(batch.getSourceStatus()).isEqualTo("in_transit");
        assertThat(batch.getRawStatus()).isEqualTo("未签收");
        assertThat(batch.getTransportMode()).isEqualTo("AIR");
        assertThat(batch.getDestination()).isEqualTo("RUH");
        assertThat(batch.getTargetWarehouseName()).isEqualTo("沙特FBN");
        assertThat(batch.getTrackingNo()).isEqualTo("ZD-TRACK-0037");
        assertThat(batch.getPackages()).hasSize(2);
        assertThat(batch.getPackages()).allSatisfy(itemPackage -> {
            assertThat(itemPackage.getLines()).isEmpty();
            assertThat(itemPackage.getWeightKg()).isNull();
            assertThat(itemPackage.getLengthCm()).isNull();
            assertThat(itemPackage.getWidthCm()).isNull();
            assertThat(itemPackage.getHeightCm()).isNull();
        });
        assertThat(batch.getPackages()).extracting("boxNo")
                .containsExactly("ZD-BOX-001", "ZD-BOX-002");
    }

    @Test
    void logsInAndFetchesProviderMaximumTwoMonthWindow() throws Exception {
        AtomicReference<String> loginBody = new AtomicReference<>();
        AtomicReference<String> expressQuery = new AtomicReference<>();
        AtomicReference<String> boxQuery = new AtomicReference<>();
        AtomicReference<String> expressToken = new AtomicReference<>();
        AtomicReference<String> boxToken = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/login", exchange -> {
            loginBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, "{\"code\":20000,\"msg\":\"\",\"data\":{\"token\":\"fixture-token\"}}");
        });
        server.createContext("/api/v1/customer/wuliu/express/integral/q", exchange -> {
            expressQuery.set(exchange.getRequestURI().getRawQuery());
            expressToken.set(exchange.getRequestHeaders().getFirst("X-Token"));
            sendJson(exchange, expressJson());
        });
        server.createContext("/api/v1/customer/wuliu/box/q", exchange -> {
            boxQuery.set(exchange.getRequestURI().getRawQuery());
            boxToken.set(exchange.getRequestHeaders().getFirst("X-Token"));
            sendJson(exchange, boxJson());
        });
        server.start();

        LogisticsAutoSyncProperties properties = enabledProperties();
        properties.getZd().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T04:00:00Z"), SHANGHAI_ZONE);
        LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
        request.setLoginAccount("demo-account");
        request.setPassword("demo-secret");

        LogisticsProviderFetchResult result = adapter(properties, clock).fetch(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBatchCount()).isEqualTo(1);
        assertThat(result.getPackageCount()).isEqualTo(2);
        assertThat(result.getLineCount()).isZero();
        assertThat(result.getNodeCount()).isZero();
        assertThat(loginBody.get()).contains("ZGVtby1hY2NvdW50").contains("ZGVtby1zZWNyZXQ=");
        assertThat(expressQuery.get()).isEqualTo("fromDate=2026-05-17&toDate=2026-07-16");
        assertThat(boxQuery.get()).isEqualTo("fromDate=2026-05-17&toDate=2026-07-16");
        assertThat(expressToken.get()).isEqualTo("fixture-token");
        assertThat(boxToken.get()).isEqualTo("fixture-token");
    }

    @Test
    void rejectsConfiguredWindowThatExceedsProviderLimitBeforeLogin() {
        LogisticsAutoSyncProperties properties = enabledProperties();
        properties.getZd().setLookbackDays(60);
        properties.getZd().setLookaheadDays(1);
        LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
        request.setLoginAccount("demo-account");
        request.setPassword("demo-secret");

        LogisticsProviderFetchResult result = adapter(properties, Clock.systemUTC()).fetch(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(LogisticsProviderFailureCode.CONFIGURATION_ERROR);
        assertThat(result.getFailureMessage()).contains("不能超过 60 天");
        assertThat(result.getFailureMessage()).doesNotContain("demo-account").doesNotContain("demo-secret");
    }

    @Test
    void wiresAdapterWithConfigurationPropertiesInSpringContext() {
        new ApplicationContextRunner()
                .withBean(LogisticsAutoSyncProperties.class)
                .withBean(ZdLogisticsProviderAdapter.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ZdLogisticsProviderAdapter.class);
                });
    }

    private ZdLogisticsProviderAdapter adapter(LogisticsAutoSyncProperties properties, Clock clock) {
        return new ZdLogisticsProviderAdapter(properties, clock);
    }

    private LogisticsAutoSyncProperties enabledProperties() {
        LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
        properties.getZd().setEnabled(true);
        properties.getZd().setLookbackDays(59);
        properties.getZd().setLookaheadDays(1);
        return properties;
    }

    private static void sendJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String expressJson() {
        return "{\"code\":20000,\"msg\":\"\",\"data\":[{\"headers\":["
                + "{\"prop\":\"entryNumber\",\"label\":\"入仓号\"},"
                + "{\"prop\":\"quantity\",\"label\":\"箱数\"}"
                + "],\"data\":[{"
                + "\"id\":37001,"
                + "\"warehouseName\":\"深圳仓\","
                + "\"transportation\":\"沙特空运\","
                + "\"deliveryType\":\"沙特FBN\","
                + "\"entryNumber\":\"GOKSA0037\","
                + "\"quantity\":2,"
                + "\"expressNumber\":\"ZD-TRACK-0037\","
                + "\"expressStatus\":\"未签收\","
                + "\"gmtCreate\":\"2026-07-14 09:30:00\","
                + "\"weightBill\":120.5"
                + "}]}]}";
    }

    private static String boxJson() {
        return "{\"code\":20000,\"msg\":\"\",\"data\":[{\"headers\":["
                + "{\"prop\":\"expressNumber\",\"label\":\"物流单号\"},"
                + "{\"prop\":\"boxCode\",\"label\":\"箱号\"}"
                + "],\"data\":["
                + "{\"entryNumber\":\"GOKSA0037\",\"expressNumber\":\"ZD-TRACK-0037\",\"boxCode\":\"ZD-BOX-001\",\"warehouseName\":\"沙特FBN\",\"extInfo\":\"billing-summary\"},"
                + "{\"entryNumber\":\"GOKSA0037\",\"expressNumber\":\"ZD-TRACK-0037\",\"boxCode\":\"ZD-BOX-002\",\"warehouseName\":\"沙特FBN\",\"extInfo\":\"billing-summary\"}"
                + "]}]}";
    }
}
