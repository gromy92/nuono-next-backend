package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncBatch;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncLine;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncPackage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class EtLogisticsProviderAdapterTest {

    private final EtLogisticsProviderAdapter adapter = new EtLogisticsProviderAdapter(new LogisticsAutoSyncProperties());

    @Test
    void buildsVerifiedEtRequestPaths() {
        assertThat(adapter.buildShipOrderListPath(10, "0.7281240260615135"))
                .isEqualTo("/Delivery/ShipOrder/GetGridJson?t=0.7281240260615135&page=1&limit=10&storeroomId=&transportId=&status=&shipOrderId=&startTime=&endTime=&sType=&keyWords=&barcode=&skuCode=&doSort=&boxId=&overDifference=-1&cod=-1");
        assertThat(adapter.buildShipOrderBoxDetailPath("F2601155395539", 1, 10, "0.8898156883973837"))
                .isEqualTo("/Delivery/ShipOrder/GetBoxDetailForm?t=0.8898156883973837&page=1&limit=10&shipOrderId=F2601155395539");
        assertThat(adapter.buildBoxModifyPath()).isEqualTo("/Delivery/BoxList/GetModifyBox");
        assertThat(adapter.buildBoxListDetailPath("X26043047357", 1, 1000, "0.3230527343616969"))
                .isEqualTo("/Delivery/BoxList/GetDetailsGridJson?t=0.3230527343616969&page=1&limit=1000&boxId=X26043047357");
    }

    @Test
    void normalizesEtListAndBoxDetailsIntoPluginCommand() {
        PluginSyncCommand command = adapter.normalize(
                listJson(),
                Map.of("F2604304851631", shipOrderBoxDetailJson()),
                Map.of("X26043047357", boxModifyJson()),
                Map.of("X26043047357", boxListDetailJson())
        );

        assertThat(command.getSourceSystem()).isEqualTo("ET");
        assertThat(command.getForwarderName()).isEqualTo("易通");
        assertThat(command.getSourceBatchExpectations()).hasSize(1);
        assertThat(command.getSourceBatchExpectations().get(0).getBatchNo()).isEqualTo("F2604304851631");
        assertThat(command.getSourceBatchExpectations().get(0).getBoxNum()).isEqualTo(24);
        assertThat(command.getSourceBatchExpectations().get(0).getTotalQuantity()).isEqualTo(1928);

        PluginSyncBatch batch = command.getBatches().get(0);
        assertThat(batch.getBatchNo()).isEqualTo("F2604304851631");
        assertThat(batch.getDestination()).isEqualTo("RUH");
        assertThat(batch.getTargetWarehouseName()).isEqualTo("ETRUH01整箱仓");
        assertThat(batch.getTransportMode()).isEqualTo("SEA");
        assertThat(batch.getSourceCreatedAt()).isEqualTo("2026-04-30 10:14:51");
        assertThat(batch.getEstimatedDepartureAt()).isEqualTo("2026-05-22 00:00:00");
        assertThat(batch.getEstimatedArrivalAt()).isEqualTo("2026-06-15 00:00:00");
        assertThat(batch.getOfficialEtaDate()).hasToString("2026-06-15");

        PluginSyncPackage itemPackage = batch.getPackages().get(0);
        assertThat(itemPackage.getBoxNo()).isEqualTo("24-1");
        assertThat(itemPackage.getExternalBoxNo()).isEqualTo("X26043047357");
        assertThat(itemPackage.getLengthCm()).isNull();
        assertThat(itemPackage.getWeightKg()).isNull();
        assertThat(itemPackage.getPackageStatus()).isEqualTo("已出库");
        assertThat(itemPackage.getLines()).hasSize(1);

        PluginSyncLine line = itemPackage.getLines().get(0);
        assertThat(line.getBarcode()).isEqualTo("PAPERSAYSB293");
        assertThat(line.getPsku()).isEqualTo("PAPERSAYSB293");
        assertThat(line.getSku()).isEqualTo("PAPERSAYSB293");
        assertThat(line.getMsku()).isEqualTo("PAPERSAYSB293");
        assertThat(line.getProductName()).isEqualTo("粉盒马克笔24支48色");
        assertThat(line.getShippedQuantity()).isEqualTo(48);
        assertThat(line.getStoreCode()).isEmpty();
        assertThat(line.getSiteCode()).isEmpty();
    }

    @Test
    void prefersEtBarcodeWhenSkuCodeIsAnotherValidProductCode() {
        PluginSyncCommand command = adapter.normalize(
                listJson(),
                Map.of("F2604304851631", shipOrderBoxDetailJson()),
                Map.of("X26043047357", boxModifyJson()),
                Map.of("X26043047357", boxListDetailJson().replace(
                        "\"SkuCode\": \"PAPERSAYSB293\"",
                        "\"SkuCode\": \"OTHER-PRODUCT-VALID-BARCODE\""
                ))
        );

        PluginSyncLine line = command.getBatches().get(0).getPackages().get(0).getLines().get(0);

        assertThat(line.getBarcode()).isEqualTo("PAPERSAYSB293");
        assertThat(line.getSku()).isEqualTo("PAPERSAYSB293");
        assertThat(line.getPsku()).isEqualTo("OTHER-PRODUCT-VALID-BARCODE");
    }

    @Test
    void usesOnlyPositiveListCountsForSourceBatchExpectations() {
        PluginSyncCommand command = adapter.normalize(
                "{\"data\":[{\"ShipOrderId\":\"F2606285327085\",\"SendBoxCount\":0,\"SendQuantity\":0}]}",
                Map.of(),
                Map.of(),
                Map.of()
        );

        assertThat(command.getSourceBatchExpectations()).hasSize(1);
        assertThat(command.getSourceBatchExpectations().get(0).getBatchNo()).isEqualTo("F2606285327085");
        assertThat(command.getSourceBatchExpectations().get(0).getBoxNum()).isNull();
        assertThat(command.getSourceBatchExpectations().get(0).getTotalQuantity()).isNull();
    }

    @Test
    void keepsEtOverseasWarehouseStatusInTransit() {
        PluginSyncCommand command = adapter.normalize(
                json(
                        "{",
                        "  \"data\": [",
                        "    {",
                        "      \"ShipOrderId\": \"ETSO-1003\",",
                        "      \"BoxId\": \"ETBOX-1003\",",
                        "      \"CreateTime\": \"2026/06/03 09:05\",",
                        "      \"CaseQuantity\": 30,",
                        "      \"ShipOrderStatusName\": \"ET海外仓入库\",",
                        "      \"SkuCode\": \"SGGRB221\"",
                        "    }",
                        "  ]",
                        "}"
                ),
                Map.of(),
                Map.of(),
                Map.of()
        );

        PluginSyncBatch batch = command.getBatches().get(0);

        assertThat(batch.getBatchStatus()).isEqualTo("in_transit");
        assertThat(batch.getSourceStatus()).isEqualTo("ET海外仓入库");
        assertThat(batch.getNodes().get(0).getNodeStatus()).isEqualTo("in_transit");
    }

    @Test
    void returnsConfigurationFailureWhenHttpFetchIsDisabled() {
        LogisticsProviderFetchResult result = adapter.fetch(new LogisticsProviderFetchRequest());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(LogisticsProviderFailureCode.CONFIGURATION_ERROR);
    }

    @Test
    void fetchLogsInAndCollectsEtDetailsAutomatically() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            server.handle("/login", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                String body = readBody(exchange);
                assertThat(body).contains("fake-user").contains("fake-password");
                exchange.getResponseHeaders().add("Set-Cookie", "et_session=ok; Path=/");
                sendJson(exchange, "{}");
            });
            server.handle("/Delivery/ShipOrder/GetGridJson", exchange -> {
                assertThat(exchange.getRequestHeaders().getFirst("Cookie")).contains("et_session=ok");
                sendJson(exchange, listJson());
            });
            server.handle("/Delivery/ShipOrder/GetBoxDetailForm", exchange -> {
                assertThat(exchange.getRequestURI().getRawQuery()).contains("shipOrderId=F2604304851631");
                sendJson(exchange, shipOrderBoxDetailJson());
            });
            server.handle("/Delivery/BoxList/GetModifyBox", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(readBody(exchange)).contains("boxId=X26043047357");
                sendJson(exchange, boxModifyJson());
            });
            server.handle("/Delivery/BoxList/GetDetailsGridJson", exchange -> {
                assertThat(exchange.getRequestURI().getRawQuery()).contains("boxId=X26043047357");
                sendJson(exchange, boxListDetailJson());
            });

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getEt().setEnabled(true);
            properties.getEt().setBaseUrl(server.baseUrl());
            properties.getEt().setLoginPath("/login");
            EtLogisticsProviderAdapter httpAdapter = new EtLogisticsProviderAdapter(properties);

            LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
            request.setLoginAccount("fake-user");
            request.setPassword("fake-password");
            request.setRecentLimit(10);

            LogisticsProviderFetchResult result = httpAdapter.fetch(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCommand().getSourceSystem()).isEqualTo("ET");
            assertThat(result.getCommand().getBatches()).hasSize(1);
            assertThat(result.getPackageCount()).isEqualTo(1);
            assertThat(result.getLineCount()).isEqualTo(1);
            assertThat(result.getCommand().getBatches().get(0).getPackages().get(0).getLines().get(0).getPsku())
                    .isEqualTo("PAPERSAYSB293");
        }
    }

    @Test
    void returnsCaptchaRequiredWhenLoginJsonRequiresCaptcha() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            AtomicBoolean listRequested = new AtomicBoolean(false);
            server.handle("/login", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(readBody(exchange)).contains("fake-user").contains("fake-password");
                sendJson(exchange, "{\"success\":false,\"message\":\"请输入验证码\"}");
            });
            server.handle("/Delivery/ShipOrder/GetGridJson", exchange -> {
                listRequested.set(true);
                sendJson(exchange, listJson());
            });

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getEt().setEnabled(true);
            properties.getEt().setBaseUrl(server.baseUrl());
            properties.getEt().setLoginPath("/login");
            EtLogisticsProviderAdapter httpAdapter = new EtLogisticsProviderAdapter(properties);

            LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
            request.setLoginAccount("fake-user");
            request.setPassword("fake-password");
            request.setRecentLimit(10);

            LogisticsProviderFetchResult result = httpAdapter.fetch(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureCode()).isEqualTo(LogisticsProviderFailureCode.CAPTCHA_REQUIRED);
            assertThat(result.getFailureMessage())
                    .contains("验证码")
                    .doesNotContain("fake-user")
                    .doesNotContain("fake-password");
            assertThat(listRequested).isFalse();
        }
    }

    @Test
    void returnsInvalidCredentialWhenEtLoginJsonFailsWithoutCaptcha() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            AtomicBoolean listRequested = new AtomicBoolean(false);
            server.handle("/login", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(readBody(exchange)).contains("fake-user").contains("fake-password");
                sendJson(exchange, "{\"success\":false,\"message\":\"账号或密码错误\"}");
            });
            server.handle("/Delivery/ShipOrder/GetGridJson", exchange -> {
                listRequested.set(true);
                sendJson(exchange, listJson());
            });

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getEt().setEnabled(true);
            properties.getEt().setBaseUrl(server.baseUrl());
            properties.getEt().setLoginPath("/login");
            EtLogisticsProviderAdapter httpAdapter = new EtLogisticsProviderAdapter(properties);

            LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
            request.setLoginAccount("fake-user");
            request.setPassword("fake-password");
            request.setRecentLimit(10);

            LogisticsProviderFetchResult result = httpAdapter.fetch(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureCode()).isEqualTo(LogisticsProviderFailureCode.INVALID_CREDENTIAL);
            assertThat(result.getFailureMessage())
                    .contains("登录失败")
                    .contains("账号或密码错误")
                    .doesNotContain("fake-user")
                    .doesNotContain("fake-password");
            assertThat(listRequested).isFalse();
        }
    }

    private static String listJson() {
        return json(
                "{",
                "  \"data\": [",
                "    {",
                "      \"AllBoxNumber\": 24,",
                "      \"CityId\": \"RUH\",",
                "      \"CountryId\": \"KSA\",",
                "      \"CreateTime\": \"2026-04-30 10:14:51\",",
                "      \"ETD_ETA\": \"2026-05-22 To 2026-06-15\",",
                "      \"InlandQuantity\": 1928,",
                "      \"SendBoxCount\": 24,",
                "      \"SendQuantity\": 1928,",
                "      \"ShipOrderId\": \"F2604304851631\",",
                "      \"Status\": 7,",
                "      \"StoreroomTitle\": \"ETRUH01整箱仓\",",
                "      \"TransportTitle\": \"海运BySea\"",
                "    }",
                "  ]",
                "}"
        );
    }

    private static String shipOrderBoxDetailJson() {
        return json(
                "{",
                "  \"data\": [",
                "    {",
                "      \"BoxId\": \"X26043047357\",",
                "      \"CaseQuantity\": 48,",
                "      \"ClientBoxId\": \"24-1\",",
                "      \"CountryId\": \"KSA\",",
                "      \"Height\": 30,",
                "      \"Length\": 40,",
                "      \"ShipOrderId\": \"F2604304851631\",",
                "      \"StatusName\": \"已出库\",",
                "      \"Weight\": 7,",
                "      \"Width\": 30",
                "    }",
                "  ]",
                "}"
        );
    }

    private static String boxModifyJson() {
        return json(
                "{",
                "  \"data\": {",
                "    \"BoxId\": \"X26043047357\",",
                "    \"ClientBoxId\": \"24-1\",",
                "    \"Height\": 30,",
                "    \"Length\": 40,",
                "    \"Weight\": 7,",
                "    \"Width\": 30",
                "  }",
                "}"
        );
    }

    private static String boxListDetailJson() {
        return json(
                "{",
                "  \"data\": [",
                "    {",
                "      \"Barcode\": \"PAPERSAYSB293\",",
                "      \"BoxId\": \"X26043047357\",",
                "      \"CaseQuantity\": 48,",
                "      \"GoodsTitle\": \"粉盒马克笔24支48色\",",
                "      \"ModelNumber\": \"PAPERSAYSB293\",",
                "      \"RealQuantity\": 48,",
                "      \"SkuCode\": \"PAPERSAYSB293\"",
                "    }",
                "  ]",
                "}"
        );
    }

    private static String json(String... lines) {
        return String.join("\n", lines);
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    private static final class StubHttpServer implements AutoCloseable {
        private final HttpServer server;

        private StubHttpServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(String path, ExchangeHandler handler) {
            server.createContext(path, exchange -> {
                try {
                    handler.handle(exchange);
                } catch (Exception exception) {
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
