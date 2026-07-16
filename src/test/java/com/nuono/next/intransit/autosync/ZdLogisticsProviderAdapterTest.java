package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void groupsBoxesWithoutEntryNumberIntoDraftBatchesWhenTrackingIsEmbeddedInBoxCode() {
        ZdLogisticsProviderAdapter adapter = adapter(new LogisticsAutoSyncProperties(), Clock.systemUTC());

        PluginSyncCommand command = adapter.normalize(expressJson(), orphanBoxJson());

        assertThat(command.getBatches()).hasSize(3);
        assertThat(command.getSourceBatchExpectations()).hasSize(1);

        var firstSupplementalBatch = command.getBatches().get(1);
        assertThat(firstSupplementalBatch.getBatchNo()).isEqualTo("ZDSEA9009533");
        assertThat(firstSupplementalBatch.getBatchStatus()).isEqualTo("draft");
        assertThat(firstSupplementalBatch.getRawStatus()).isEqualTo("批次接口未返回，待人工订正");
        assertThat(firstSupplementalBatch.getTransportMode()).isNull();
        assertThat(firstSupplementalBatch.getDestination()).isEqualTo("RUH");
        assertThat(firstSupplementalBatch.getTargetWarehouseName()).isEqualTo("沙特仓");
        assertThat(firstSupplementalBatch.getTrackingNo()).isEqualTo("ZDSEA9009533");
        assertThat(firstSupplementalBatch.getExternalShipmentNo()).isEqualTo("ZDSEA9009533");
        assertThat(firstSupplementalBatch.getSourceCreatedAt()).isNull();
        assertThat(firstSupplementalBatch.getPackages()).extracting("boxNo")
                .containsExactly("SGGR-ZDSEA9009533-1件", "SGGR-ZDSEA9009533-2件");

        var secondSupplementalBatch = command.getBatches().get(2);
        assertThat(secondSupplementalBatch.getBatchNo()).isEqualTo("带电ZDSEA9009534");
        assertThat(secondSupplementalBatch.getPackages()).extracting("boxNo")
                .containsExactly("SGGR-带电ZDSEA9009534-8件");
    }

    @Test
    void rejectsFallbackWhenTrackingNumberIsNotEmbeddedInBoxCode() {
        ZdLogisticsProviderAdapter adapter = adapter(new LogisticsAutoSyncProperties(), Clock.systemUTC());
        String boxBody = orphanBoxJson().replace(
                "SGGR-ZDSEA9009533-1件",
                "SGGR-ZDSEA9009999-1件"
        );

        assertThatThrownBy(() -> adapter.normalize(expressJson(), boxBody))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("箱号与物流单号不一致");
    }

    @Test
    void rejectsFallbackBatchWhenBoxesDisagreeOnDestinationWarehouse() {
        ZdLogisticsProviderAdapter adapter = adapter(new LogisticsAutoSyncProperties(), Clock.systemUTC());
        String boxBody = orphanBoxJson().replaceFirst(
                "\\\"warehouseName\\\":\\\"沙特仓\\\"",
                "\\\"warehouseName\\\":\\\"迪拜仓\\\""
        );

        assertThatThrownBy(() -> adapter.normalize(expressJson(), boxBody))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("目的仓不一致");
    }

    @Test
    void normalizesOwner307HistoricalShapeIntoThreeProviderAndNineteenSupplementalBatches() {
        ZdLogisticsProviderAdapter adapter = adapter(new LogisticsAutoSyncProperties(), Clock.systemUTC());

        PluginSyncCommand command = adapter.normalize(owner307HistoricalExpressJson(), owner307HistoricalBoxJson());

        assertThat(command.getBatches()).hasSize(22);
        assertThat(command.getBatches()).flatExtracting(batch -> batch.getPackages()).hasSize(22);
        assertThat(command.getSourceBatchExpectations()).hasSize(3);
        assertThat(command.getBatches()).filteredOn(batch -> "draft".equals(batch.getBatchStatus())).hasSize(19);
        assertThat(command.getBatches()).filteredOn(batch -> "ZDSEA9009533".equals(batch.getBatchNo()))
                .singleElement()
                .satisfies(batch -> {
                    assertThat(batch.getTrackingNo()).isEqualTo("ZDSEA9009533");
                    assertThat(batch.getPackages()).extracting("boxNo")
                            .containsExactly("SGGR-ZDSEA9009533-1件");
                });
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

    private static String orphanBoxJson() {
        return "{\"code\":20000,\"msg\":\"\",\"data\":[{\"headers\":["
                + "{\"prop\":\"expressNumber\",\"label\":\"物流单号\"},"
                + "{\"prop\":\"boxCode\",\"label\":\"箱号\"}"
                + "],\"data\":["
                + "{\"expressNumber\":\"ZDSEA9009533\",\"boxCode\":\"SGGR-ZDSEA9009533-1件\",\"warehouseName\":\"沙特仓\"},"
                + "{\"expressNumber\":\"ZDSEA9009533\",\"boxCode\":\"SGGR-ZDSEA9009533-2件\",\"warehouseName\":\"沙特仓\"},"
                + "{\"expressNumber\":\"带电ZDSEA9009534\",\"boxCode\":\"SGGR-带电ZDSEA9009534-8件\",\"warehouseName\":\"沙特仓\"}"
                + "]}]}";
    }

    private static String owner307HistoricalExpressJson() {
        return "{\"code\":20000,\"msg\":\"\",\"data\":[{\"data\":["
                + providerBatchRow("ZDSEA9008390") + ","
                + providerBatchRow("带电ZDSEA9008392") + ","
                + providerBatchRow("带电ZDSEA9008391")
                + "]}]}";
    }

    private static String owner307HistoricalBoxJson() {
        String matchedRows = String.join(",",
                matchedBoxRow("SGGR-带电ZDSEA9008391-2件", "带电ZDSEA9008391"),
                matchedBoxRow("SGGR-带电ZDSEA9008392-4件", "带电ZDSEA9008392"),
                matchedBoxRow("SGGR-ZDSEA9008390-1件", "ZDSEA9008390")
        );
        String orphanRows = String.join(",",
                orphanBoxRow("SGGR-ZDSEA9008389-21件", "ZDSEA9008389"),
                orphanBoxRow("SGGR-ZDSEA9009533-1件", "ZDSEA9009533"),
                orphanBoxRow("SGGR-ZDSEA9009536-1件", "ZDSEA9009536"),
                orphanBoxRow("SGGR-带电ZDSEA9009534-8件", "带电ZDSEA9009534"),
                orphanBoxRow("SGGR-带电ZDSEA9009535-3件", "带电ZDSEA9009535"),
                orphanBoxRow("SGGR-ZDSEA9009532-19箱", "ZDSEA9009532"),
                orphanBoxRow("SGGR-ZDSEA9009433-2件", "ZDSEA9009433"),
                orphanBoxRow("SGGR-ZDSEA9009434-2件", "ZDSEA9009434"),
                orphanBoxRow("SGGR-ZDSEA9009435-1件", "ZDSEA9009435"),
                orphanBoxRow("SGGR-ZDSEA9009436-1件", "ZDSEA9009436"),
                orphanBoxRow("SGGR-ZDSEA9009460-1件", "ZDSEA9009460"),
                orphanBoxRow("SGGR-ZDSEA9009459-4件", "ZDSEA9009459"),
                orphanBoxRow("SGGR-ZDSEA9009461-4件", "ZDSEA9009461"),
                orphanBoxRow("SGGR-ZDSEA9009462-2件", "ZDSEA9009462"),
                orphanBoxRow("SGGR-ZDSEA9009463-1件", "ZDSEA9009463"),
                orphanBoxRow("SGGR-ZDSEA9009458-18件", "ZDSEA9009458"),
                orphanBoxRow("SGGR-ZDSEA9009399-1件", "ZDSEA9009399"),
                orphanBoxRow("SGGR-ZDSEA9009397-2件", "ZDSEA9009397"),
                orphanBoxRow("SGGR-ZDSEA9009432-15箱", "ZDSEA9009432")
        );
        return "{\"code\":20000,\"msg\":\"\",\"data\":[{\"data\":["
                + matchedRows + "," + orphanRows + "]}]}";
    }

    private static String providerBatchRow(String trackingNo) {
        return "{\"entryNumber\":\"" + trackingNo + "\",\"expressNumber\":\"" + trackingNo
                + "\",\"quantity\":1,\"warehouseName\":\"沙特仓\",\"transportation\":\"沙特海运\","
                + "\"deliveryType\":\"沙特FBN\",\"expressStatus\":\"未签收\"}";
    }

    private static String matchedBoxRow(String boxCode, String trackingNo) {
        return "{\"boxCode\":\"" + boxCode + "\",\"entryNumber\":\"" + trackingNo
                + "\",\"expressNumber\":\"" + trackingNo + "\",\"warehouseName\":\"沙特仓\"}";
    }

    private static String orphanBoxRow(String boxCode, String trackingNo) {
        return "{\"boxCode\":\"" + boxCode + "\",\"expressNumber\":\"" + trackingNo
                + "\",\"warehouseName\":\"沙特仓\"}";
    }
}
