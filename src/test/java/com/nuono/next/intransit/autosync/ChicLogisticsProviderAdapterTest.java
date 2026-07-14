package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncBatch;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncLine;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncNode;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncPackage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ChicLogisticsProviderAdapterTest {

    private final ChicLogisticsProviderAdapter adapter = new ChicLogisticsProviderAdapter(
            new LogisticsAutoSyncProperties()
    );

    @Test
    void defaultsToVerifiedChicLoginPath() {
        assertThat(new LogisticsAutoSyncProperties().getChic().getLoginPath())
                .isEqualTo("/api/login");
    }

    @Test
    void normalizesRecentChicBatchesFromListDetailsAndOrderReport() {
        PluginSyncCommand command = adapter.normalize(
                listJson(12),
                Map.of(
                        "53000", detailJson("XGGEKSA04070", "XGGEKSA04070-1"),
                        "53001", detailJson("XGGEKSA04071", "XGGEKSA04071-1")
                ),
                json(
                        "{",
                        "  \"data\": {",
                        "    \"records\": [",
                        "      {",
                        "        \"purchaseBatchSn\": \"XGGEKSA04070\",",
                        "        \"warehousingSn\": \"XGGEKSA04070-1\",",
                        "        \"shippingNo\": \"CHIC-SHIP-04070\",",
                        "        \"status\": \"已入仓\",",
                        "        \"statusTime\": \"2026/06/03 9:05\",",
                        "        \"officialEtaDate\": \"2026-06-12\",",
                        "        \"deliveryAppointmentText\": \"16点后\",",
                        "        \"estimatedDepartureAt\": \"2026-06-08 10:30\",",
                        "        \"estimatedArrivalAt\": \"2026-06-11 18:00\",",
                        "        \"chargeableWeight\": 16",
                        "      }",
                        "    ]",
                        "  }",
                        "}"
                )
        );

        assertThat(command.getSourceSystem()).isEqualTo("CHIC");
        assertThat(command.getForwarderName()).isEqualTo("启客");
        assertThat(command.getSourceBatchExpectations()).hasSize(10);
        assertThat(command.getSourceBatchExpectations().get(0).getBatchNo()).isEqualTo("XGGEKSA04070");
        assertThat(command.getSourceBatchExpectations().get(0).getBoxNum()).isEqualTo(1);
        assertThat(command.getSourceBatchExpectations().get(0).getTotalQuantity()).isEqualTo(30);
        assertThat(command.getBatches()).hasSize(2);

        PluginSyncBatch firstBatch = command.getBatches().get(0);
        assertThat(firstBatch.getBatchNo()).isEqualTo("XGGEKSA04070");
        assertThat(firstBatch.getBatchStatus()).isEqualTo("in_transit");
        assertThat(firstBatch.getSourceStatus()).isEqualTo("已入仓");
        assertThat(firstBatch.getRawStatus()).isEqualTo("已入仓");
        assertThat(firstBatch.getExternalShipmentNo()).isEqualTo("CHIC-SHIP-04070");
        assertThat(firstBatch.getOfficialEtaDate()).hasToString("2026-06-12");
        assertThat(firstBatch.getDeliveryAppointmentText()).isEqualTo("16点后");
        assertThat(firstBatch.getEstimatedDepartureAt()).isEqualTo("2026-06-08 10:30:00");
        assertThat(firstBatch.getEstimatedArrivalAt()).isEqualTo("2026-06-11 18:00:00");

        PluginSyncPackage firstPackage = firstBatch.getPackages().get(0);
        assertThat(firstPackage.getBoxNo()).isEqualTo("XGGEKSA04070-1");
        assertThat(firstPackage.getLengthCm()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(firstPackage.getWidthCm()).isEqualByComparingTo(new BigDecimal("40"));
        assertThat(firstPackage.getHeightCm()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(firstPackage.getVolumeCbm()).isEqualByComparingTo(new BigDecimal("0.060000"));
        assertThat(firstPackage.getWeightKg()).isEqualByComparingTo(new BigDecimal("15.5"));
        assertThat(firstPackage.getChargeableWeightKg()).isEqualByComparingTo(new BigDecimal("16"));

        PluginSyncLine line = firstPackage.getLines().get(0);
        assertThat(line.getPsku()).isEqualTo("SGGRB219");
        assertThat(line.getSku()).isEqualTo("SGGRB219");
        assertThat(line.getStoreCode()).isEqualTo("STR245027-NSA");
        assertThat(line.getSiteCode()).isEqualTo("SA");
        assertThat(line.getShippedQuantity()).isEqualTo(30);

        assertThat(firstBatch.getNodes())
                .extracting(PluginSyncNode::getNodeStatus, PluginSyncNode::getNodeTime, PluginSyncNode::getDescription)
                .contains(tuple("handed_to_forwarder", "2026-06-01 08:30:00", "国内收货完成"))
                .contains(tuple("warehouse_received", "2026-06-03 09:05:00", "已入仓 外部出货编号 CHIC-SHIP-04070"));
    }

    @Test
    void onlyKeepsTenUniqueBatchListItemsForExpectations() {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        for (int index = 0; index < 12; index += 1) {
            details.put(String.valueOf(53000 + index), detailJson("XGGEKSA0407" + index, "XGGEKSA0407" + index + "-1"));
        }

        PluginSyncCommand command = adapter.normalize(listJson(12), details, null);

        assertThat(command.getSourceBatchExpectations()).hasSize(10);
        assertThat(command.getBatches()).hasSize(10);
        assertThat(command.getBatches())
                .extracting(PluginSyncBatch::getBatchNo)
                .containsExactly(
                        "XGGEKSA04070",
                        "XGGEKSA04071",
                        "XGGEKSA04072",
                        "XGGEKSA04073",
                        "XGGEKSA04074",
                        "XGGEKSA04075",
                        "XGGEKSA04076",
                        "XGGEKSA04077",
                        "XGGEKSA04078",
                        "XGGEKSA04079"
                );
    }

    @Test
    void doesNotCopyDestinationIntoLineStoreOrSite() {
        PluginSyncCommand command = adapter.normalize(
                "{\"data\":{\"records\":[{\"purchaseBatchId\":53000,\"purchaseBatchSn\":\"XGGEKSA04070\"}]}}",
                Map.of(
                        "53000",
                        json(
                                "{",
                                "  \"data\": {",
                                "    \"purchaseBatchSn\": \"XGGEKSA04070\",",
                                "    \"destination\": \"SA\",",
                                "    \"purchaseOrderList\": [",
                                "      {",
                                "        \"warehousingSn\": \"XGGEKSA04070-1\",",
                                "        \"goodsList\": [",
                                "          {",
                                "            \"psku\": \"SKU-NO-STORE\",",
                                "            \"sku\": \"SKU-NO-STORE\",",
                                "            \"quantity\": 6,",
                                "            \"countryCode\": \"SA\"",
                                "          }",
                                "        ]",
                                "      }",
                                "    ]",
                                "  }",
                                "}"
                        )
                ),
                null
        );

        PluginSyncLine line = command.getBatches().get(0).getPackages().get(0).getLines().get(0);

        assertThat(command.getBatches().get(0).getDestination()).isEqualTo("SA");
        assertThat(line.getStoreCode()).isEmpty();
        assertThat(line.getSiteCode()).isEmpty();
    }

    @Test
    void returnsConfigurationFailureWhenHttpFetchIsDisabled() {
        LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
        request.setLoginAccount("fake-user");
        request.setPassword("fake-password");
        request.setRecentLimit(10);

        LogisticsProviderFetchResult result = adapter.fetch(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(LogisticsProviderFailureCode.CONFIGURATION_ERROR);
        assertThat(result.getFailureMessage())
                .doesNotContain("fake-user")
                .doesNotContain("fake-password");
    }

    @Test
    void fetchSendsChicTokenHeadersRequiredByRealApi() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            server.handle("/login", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                String body = readBody(exchange);
                assertThat(body).contains("fake-user").contains("fake-password");
                sendJson(exchange, "{\"data\":{\"token\":\"chic-token\"}}");
            });
            server.handle("/api/purchase/purchase-order/purchaseBatch/query", exchange -> {
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("chic-token");
                assertThat(exchange.getRequestHeaders().getFirst("X-Token")).isEqualTo("chic-token");
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("chic-token");
                assertThat(readBody(exchange)).contains("\"rows\":1");
                sendJson(exchange, listJson(1));
            });
            server.handle("/api/purchase/purchase-order/purchaseBatch/detail", exchange -> {
                assertThat(exchange.getRequestHeaders().getFirst("X-Token")).isEqualTo("chic-token");
                assertThat(readBody(exchange)).contains("53000");
                sendJson(exchange, detailJson("XGGEKSA04070", "XGGEKSA04070-1"));
            });
            server.handle("/api/order/report/list", exchange -> {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("chic-token");
                sendJson(exchange, json(
                        "{",
                        "  \"data\": {",
                        "    \"records\": []",
                        "  }",
                        "}"
                ));
            });

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getChic().setEnabled(true);
            properties.getChic().setBaseUrl(server.baseUrl());
            properties.getChic().setLoginPath("/login");
            ChicLogisticsProviderAdapter httpAdapter = new ChicLogisticsProviderAdapter(properties);

            LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
            request.setLoginAccount("fake-user");
            request.setPassword("fake-password");
            request.setRecentLimit(1);

            LogisticsProviderFetchResult result = httpAdapter.fetch(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCommand().getSourceSystem()).isEqualTo("CHIC");
            assertThat(result.getCommand().getBatches()).hasSize(1);
        }
    }

    @Test
    void returnsInvalidCredentialWhenChicApiReportsUnauthorizedBusinessCode() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            AtomicBoolean detailRequested = new AtomicBoolean(false);
            server.handle("/login", exchange -> sendJson(exchange, "{\"data\":{\"token\":\"chic-token\"}}"));
            server.handle("/api/purchase/purchase-order/purchaseBatch/query", exchange ->
                    sendJson(exchange, "{\"code\":6,\"message\":\"未经授权请求\"}"));
            server.handle("/api/purchase/purchase-order/purchaseBatch/detail", exchange -> {
                detailRequested.set(true);
                sendJson(exchange, detailJson("XGGEKSA04070", "XGGEKSA04070-1"));
            });

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getChic().setEnabled(true);
            properties.getChic().setBaseUrl(server.baseUrl());
            properties.getChic().setLoginPath("/login");
            ChicLogisticsProviderAdapter httpAdapter = new ChicLogisticsProviderAdapter(properties);

            LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
            request.setLoginAccount("fake-user");
            request.setPassword("fake-password");
            request.setRecentLimit(1);

            LogisticsProviderFetchResult result = httpAdapter.fetch(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureCode()).isEqualTo(LogisticsProviderFailureCode.INVALID_CREDENTIAL);
            assertThat(result.getFailureMessage())
                    .contains("无权限")
                    .doesNotContain("fake-user")
                    .doesNotContain("fake-password");
            assertThat(detailRequested).isFalse();
        }
    }

    private static String listJson(int count) {
        StringBuilder builder = new StringBuilder("{\"data\":{\"records\":[");
        for (int index = 0; index < count; index += 1) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append("{\"purchaseBatchId\":")
                    .append(53000 + index)
                    .append(",\"purchaseBatchSn\":\"XGGEKSA0407")
                    .append(index)
                    .append("\",\"boxNum\":1,\"totalQuantity\":30}");
        }
        builder.append("]}}");
        return builder.toString();
    }

    private static String detailJson(String batchNo, String boxNo) {
        return String.format(
                json(
                        "{",
                        "  \"data\": {",
                        "    \"purchaseBatchSn\": \"%s\",",
                        "    \"destination\": \"SA\",",
                        "    \"purchaseOrderList\": [",
                        "      {",
                        "        \"warehousingSn\": \"%s\",",
                        "        \"boxSpec\": \"50*40*30cm\",",
                        "        \"weight\": 15.5,",
                        "        \"goodsList\": [",
                        "          {",
                        "            \"psku\": \"SGGRB219\",",
                        "            \"sku\": \"SGGRB219\",",
                        "            \"goodsName\": \"高弹力头巾美容帽\",",
                        "            \"storeCode\": \"STR245027-NSA\",",
                        "            \"siteCode\": \"SA\",",
                        "            \"quantity\": 30",
                        "          }",
                        "        ]",
                        "      }",
                        "    ],",
                        "    \"trackingList\": [",
                        "      {",
                        "        \"status\": \"国内收货完成\",",
                        "        \"time\": \"2026-06-01 08:30:00\"",
                        "      }",
                        "    ]",
                        "  }",
                        "}"
                ),
                batchNo,
                boxNo
        );
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.api.Assertions.tuple(values);
    }

    private static String json(String... lines) {
        return String.join("\n", lines);
    }

    private static final class StubHttpServer implements AutoCloseable {
        private final HttpServer server;

        private StubHttpServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(String path, HttpHandler handler) {
            server.createContext(path, exchange -> {
                try {
                    handler.handle(exchange);
                } catch (Throwable exception) {
                    byte[] bytes = exception.getMessage() == null
                            ? exception.getClass().getName().getBytes(StandardCharsets.UTF_8)
                            : exception.getMessage().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                }
            });
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
