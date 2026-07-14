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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class YiteLogisticsProviderAdapterTest {

    private final YiteLogisticsProviderAdapter adapter = new YiteLogisticsProviderAdapter(new LogisticsAutoSyncProperties());

    @Test
    void buildsVerifiedYiteRequestContracts() {
        assertThat(adapter.buildShipmentListPayload(1)).isEqualTo(expectedShipmentListPayload());
        assertThat(adapter.buildShipmentViewPath("6a1979a93a8e5260c465758b"))
                .isEqualTo("/rest/tms/wos/shipment/view?id=6a1979a93a8e5260c465758b");
        assertThat(adapter.buildShipmentPackingListPath("6a1979a93a8e5260c465758b"))
                .isEqualTo("/rest/tms/wos/shipment/change_declaration_view?activeTab=packing_list&id=6a1979a93a8e5260c465758b");
    }

    @Test
    void defaultsToVerifiedYiteLoginPath() {
        assertThat(new LogisticsAutoSyncProperties().getYite().getLoginPath())
                .isEqualTo("/rest/tms/wos/auth/login?redirect_url=%2Ftms%2Fwos");
    }

    @Test
    void normalizesYiteListViewAndPackingListIntoPluginCommand() {
        PluginSyncCommand command = adapter.normalize(
                listJson(),
                Map.of("6a1979a93a8e5260c465758b", viewJson()),
                Map.of("6a1979a93a8e5260c465758b", packingListJson())
        );

        assertThat(command.getSourceSystem()).isEqualTo("YITE");
        assertThat(command.getForwarderName()).isEqualTo("义特");
        assertThat(command.getSourceBatchExpectations()).hasSize(1);
        assertThat(command.getSourceBatchExpectations().get(0).getBatchNo()).isEqualTo("YT2605306913");
        assertThat(command.getSourceBatchExpectations().get(0).getBoxNum()).isEqualTo(2);
        assertThat(command.getSourceBatchExpectations().get(0).getTotalQuantity()).isEqualTo(120);

        PluginSyncBatch batch = command.getBatches().get(0);
        assertThat(batch.getBatchNo()).isEqualTo("YT2605306913");
        assertThat(batch.getBatchStatus()).isEqualTo("转运中");
        assertThat(batch.getSourceStatus()).isEqualTo("转运中");
        assertThat(batch.getRawStatus()).isEqualTo("转运中");
        assertThat(batch.getTransportMode()).isEqualTo("SEA");
        assertThat(batch.getDestination()).isEqualTo("沙特阿拉伯");
        assertThat(batch.getSourceCreatedAt()).isEqualTo("2026-05-29 19:34:01");
        assertThat(batch.getOfficialEtaDate()).hasToString("2026-06-30");
        assertThat(batch.getEstimatedDepartureAt()).isEqualTo("2026-06-07 00:00:00");
        assertThat(batch.getEstimatedArrivalAt()).isEqualTo("2026-06-30 00:00:00");

        assertThat(batch.getNodes()).extracting("nodeStatus", "nodeTime", "description").containsExactly(
                org.assertj.core.groups.Tuple.tuple("in_transit", "2026-06-03 17:10:22", "预配航期 ETD: 6-7 ETA:6-30"),
                org.assertj.core.groups.Tuple.tuple("departed_origin", "2026-06-03 13:16:41", "义乌仓库 发往 宁波港"),
                org.assertj.core.groups.Tuple.tuple("handed_to_forwarder", "2026-06-01 15:49:53", "义乌仓库 已收货")
        );

        assertThat(batch.getPackages()).hasSize(2);
        PluginSyncPackage firstPackage = batch.getPackages().get(0);
        assertThat(firstPackage.getBoxNo()).isEqualTo("YT2605306913U001");
        assertThat(firstPackage.getExternalBoxNo()).isEqualTo("6a1979aa3a8e5260c465758c");
        assertThat(firstPackage.getLengthCm()).isEqualByComparingTo("54");
        assertThat(firstPackage.getWidthCm()).isEqualByComparingTo("45");
        assertThat(firstPackage.getHeightCm()).isEqualByComparingTo("32");
        assertThat(firstPackage.getWeightKg()).isEqualByComparingTo("17.68");
        assertThat(firstPackage.getVolumeCbm()).isEqualByComparingTo("0.077760");
        assertThat(firstPackage.getPackageStatus()).isEqualTo("转运中");
        assertThat(firstPackage.getLines()).hasSize(2);

        PluginSyncLine firstLine = firstPackage.getLines().get(0);
        assertThat(firstLine.getPsku()).isEqualTo("SGGRB142");
        assertThat(firstLine.getSku()).isEqualTo("SGGRB142");
        assertThat(firstLine.getMsku()).isEmpty();
        assertThat(firstLine.getProductName()).isEqualTo("8个装-透明抽屉隔板");
        assertThat(firstLine.getShippedQuantity()).isEqualTo(16);
        assertThat(firstLine.getReceivedQuantity()).isZero();
        assertThat(firstLine.getStoreCode()).isEmpty();
        assertThat(firstLine.getSiteCode()).isEmpty();

        PluginSyncPackage secondPackage = batch.getPackages().get(1);
        assertThat(secondPackage.getBoxNo()).isEqualTo("YT2605306913U002");
        assertThat(secondPackage.getLines()).hasSize(1);
        assertThat(secondPackage.getLines().get(0).getPsku()).isEqualTo("SGGRB142");
        assertThat(secondPackage.getLines().get(0).getShippedQuantity()).isEqualTo(24);
    }

    @Test
    void treatsYitePickedBackToOverseasWarehouseAsFinalWarehouseReceived() {
        PluginSyncCommand command = adapter.normalize(
                listJson(),
                Map.of("6a1979a93a8e5260c465758b", warehouseReceivedViewJson()),
                Map.of()
        );

        PluginSyncBatch batch = command.getBatches().get(0);

        assertThat(batch.getNodes()).hasSize(1);
        assertThat(batch.getNodes().get(0).getDescription()).isEqualTo("已提回海外仓，待拆柜派送");
        assertThat(batch.getNodes().get(0).getNodeStatus()).isEqualTo("warehouse_received");
    }

    @Test
    void returnsConfigurationFailureWhenHttpFetchIsDisabled() {
        LogisticsProviderFetchResult result = adapter.fetch(new LogisticsProviderFetchRequest());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(LogisticsProviderFailureCode.CONFIGURATION_ERROR);
    }

    @Test
    void fetchLogsInAndCollectsYiteDetailsAutomatically() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            server.handle("/login", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                String body = readBody(exchange);
                assertThat(body).contains("fake-user").contains("fake-password");
                exchange.getResponseHeaders().add("Set-Cookie", "token=yite-token; Path=/");
                sendJson(exchange, "{}");
            });
            server.handle("/rest/tms/wos/shipment/lists", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestHeaders().getFirst("Cookie")).contains("token=yite-token");
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("yite-token");
                assertThat(readBody(exchange)).contains("\"pageSize\":10");
                sendJson(exchange, listJson());
            });
            server.handle("/rest/tms/wos/shipment/view", exchange -> {
                assertThat(exchange.getRequestURI().getRawQuery()).contains("id=6a1979a93a8e5260c465758b");
                sendJson(exchange, viewJson());
            });
            server.handle("/rest/tms/wos/shipment/change_declaration_view", exchange -> {
                assertThat(exchange.getRequestURI().getRawQuery()).contains("activeTab=packing_list");
                assertThat(exchange.getRequestURI().getRawQuery()).contains("id=6a1979a93a8e5260c465758b");
                sendJson(exchange, packingListJson());
            });

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getYite().setEnabled(true);
            properties.getYite().setBaseUrl(server.baseUrl());
            properties.getYite().setLoginPath("/login");
            YiteLogisticsProviderAdapter httpAdapter = new YiteLogisticsProviderAdapter(properties);

            LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
            request.setLoginAccount("fake-user");
            request.setPassword("fake-password");
            request.setRecentLimit(10);

            LogisticsProviderFetchResult result = httpAdapter.fetch(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCommand().getSourceSystem()).isEqualTo("YITE");
            assertThat(result.getCommand().getBatches()).hasSize(1);
            assertThat(result.getPackageCount()).isEqualTo(2);
            assertThat(result.getLineCount()).isEqualTo(3);
            assertThat(result.getNodeCount()).isEqualTo(3);
            assertThat(result.getCommand().getBatches().get(0).getPackages().get(0).getLines().get(0).getPsku())
                    .isEqualTo("SGGRB142");
        }
    }

    @Test
    void fetchUsesYiteLoginResponseTokenWhenCookieTokenIsMissing() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            server.handle("/login", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                String body = readBody(exchange);
                assertThat(body).contains("fake-user").contains("fake-password");
                sendJson(exchange, "{\"success\":1,\"data\":{\"token\":\"body-token\"}}");
            });
            server.handle("/rest/tms/wos/shipment/lists", exchange -> {
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("body-token");
                sendJson(exchange, listJson());
            });
            server.handle("/rest/tms/wos/shipment/view", exchange -> sendJson(exchange, viewJson()));
            server.handle("/rest/tms/wos/shipment/change_declaration_view", exchange -> sendJson(exchange, packingListJson()));

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getYite().setEnabled(true);
            properties.getYite().setBaseUrl(server.baseUrl());
            properties.getYite().setLoginPath("/login");
            YiteLogisticsProviderAdapter httpAdapter = new YiteLogisticsProviderAdapter(properties);

            LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
            request.setLoginAccount("fake-user");
            request.setPassword("fake-password");
            request.setRecentLimit(10);

            LogisticsProviderFetchResult result = httpAdapter.fetch(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCommand().getSourceSystem()).isEqualTo("YITE");
            assertThat(result.getCommand().getBatches()).hasSize(1);
        }
    }

    @Test
    void returnsInvalidCredentialWhenYiteLoginJsonFails() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            AtomicBoolean listRequested = new AtomicBoolean(false);
            server.handle("/login", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                String body = readBody(exchange);
                assertThat(body).contains("fake-user").contains("fake-password");
                sendJson(exchange, "{\"success\":0,\"info\":\"账号或密码错误\"}");
            });
            server.handle("/rest/tms/wos/shipment/lists", exchange -> {
                listRequested.set(true);
                sendJson(exchange, listJson());
            });

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getYite().setEnabled(true);
            properties.getYite().setBaseUrl(server.baseUrl());
            properties.getYite().setLoginPath("/login");
            YiteLogisticsProviderAdapter httpAdapter = new YiteLogisticsProviderAdapter(properties);

            LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
            request.setLoginAccount("fake-user");
            request.setPassword("fake-password");
            request.setRecentLimit(10);

            LogisticsProviderFetchResult result = httpAdapter.fetch(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureCode()).isEqualTo(LogisticsProviderFailureCode.INVALID_CREDENTIAL);
            assertThat(result.getFailureMessage())
                    .contains("登录失败")
                    .doesNotContain("fake-user")
                    .doesNotContain("fake-password");
            assertThat(listRequested).isFalse();
        }
    }

    @Test
    void returnsProviderFailureWhenYiteListJsonFails() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            AtomicBoolean viewRequested = new AtomicBoolean(false);
            server.handle("/login", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(readBody(exchange)).contains("fake-user").contains("fake-password");
                sendJson(exchange, "{\"success\":1,\"data\":{\"token\":\"body-token\"}}");
            });
            server.handle("/rest/tms/wos/shipment/lists", exchange -> {
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("body-token");
                sendJson(exchange, "{\"success\":0,\"info\":\"账号无权限\"}");
            });
            server.handle("/rest/tms/wos/shipment/view", exchange -> {
                viewRequested.set(true);
                sendJson(exchange, viewJson());
            });

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getYite().setEnabled(true);
            properties.getYite().setBaseUrl(server.baseUrl());
            properties.getYite().setLoginPath("/login");
            YiteLogisticsProviderAdapter httpAdapter = new YiteLogisticsProviderAdapter(properties);

            LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
            request.setLoginAccount("fake-user");
            request.setPassword("fake-password");
            request.setRecentLimit(10);

            LogisticsProviderFetchResult result = httpAdapter.fetch(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureCode()).isEqualTo(LogisticsProviderFailureCode.INVALID_CREDENTIAL);
            assertThat(result.getFailureMessage())
                    .contains("账号无权限")
                    .doesNotContain("fake-user")
                    .doesNotContain("fake-password");
            assertThat(viewRequested).isFalse();
        }
    }

    private static String listJson() {
        return json(
                "{",
                "  \"success\": 1,",
                "  \"data\": {",
                "    \"components\": {",
                "      \"gridView\": {",
                "        \"table\": {",
                "          \"dataSource\": [",
                "            {",
                "              \"id\": \"6a1979a93a8e5260c465758b\",",
                "              \"shipment_id\": \"<span>YT2605306913</span>\",",
                "              \"shipment_number\": \"YT2605306913\",",
                "              \"service\": \"沙特海运双清\",",
                "              \"to_address_country\": \"沙特阿拉伯<br/>沙特\",",
                "              \"parcel_count\": 2,",
                "              \"actual_weight\": \"16.00\",",
                "              \"chargeable_weight\": \"3.59m³\",",
                "              \"created\": 1780054441",
                "            }",
                "          ]",
                "        }",
                "      }",
                "    }",
                "  }",
                "}"
        );
    }

    private static Map<String, Object> expectedShipmentListPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timeLimit", 0);
        payload.put("page", 1);
        payload.put("pageSize", 1);
        payload.put("keywords", "");
        payload.put("service", "");
        payload.put("picking_daterange", "");
        payload.put("delivered_daterange", "");
        payload.put("created_daterange", "");
        payload.put("country", "");
        payload.put("to_warehouse_code", "");
        payload.put("name", "");
        payload.put("store_id", "");
        payload.put("config", "");
        payload.put("postcode", "");
        payload.put("isActiveTab", "in_transit");
        payload.put("restart", 0);
        payload.put("btnType", "");
        payload.put("scenes", 0);
        return payload;
    }

    private static String viewJson() {
        return json(
                "{",
                "  \"success\": 1,",
                "  \"data\": {",
                "    \"title\": \"# YT2605306913\",",
                "    \"rows\": [",
                "      {",
                "        \"components\": [",
                "          {",
                "            \"comKey\": \"tms-csos-shipment-base\",",
                "            \"data\": {",
                "              \"body\": {",
                "                \"components\": [",
                "                  {",
                "                    \"data\": {",
                "                      \"items\": [",
                "                        { \"title\": \"服务类型\", \"value\": \"沙特海运双清\" },",
                "                        { \"title\": \"发往国家\", \"value\": \"沙特阿拉伯\" },",
                "                        { \"title\": \"收费重量\", \"value\": \"3.59\" },",
                "                        { \"title\": \"状态\", \"value\": \"转运中\" }",
                "                      ]",
                "                    }",
                "                  }",
                "                ]",
                "              }",
                "            }",
                "          },",
                "          {",
                "            \"comKey\": \"tms-csos-shipment-tracking\",",
                "            \"data\": {",
                "              \"body\": {",
                "                \"components\": [",
                "                  {",
                "                    \"data\": {",
                "                      \"dataSource\": [",
                "                        {",
                "                          \"title\": \"2026-06-03 17:10:22\",",
                "                          \"info\": \"预配航期 ETD: 6-7 ETA:6-30\",",
                "                          \"event\": \"\"",
                "                        },",
                "                        {",
                "                          \"title\": \"2026-06-03 13:16:41\",",
                "                          \"info\": \"义乌仓库 发往 宁波港\",",
                "                          \"event\": \"shiped\"",
                "                        },",
                "                        {",
                "                          \"title\": \"2026-06-01 15:49:53\",",
                "                          \"info\": \"义乌仓库 已收货\",",
                "                          \"event\": \"pickup\"",
                "                        }",
                "                      ]",
                "                    }",
                "                  }",
                "                ]",
                "              }",
                "            }",
                "          }",
                "        ]",
                "      },",
                "      {",
                "        \"components\": [",
                "          {",
                "            \"comKey\": \"tms-csos-shipment-box\",",
                "            \"data\": {",
                "              \"body\": {",
                "                \"components\": [",
                "                  {",
                "                    \"data\": {",
                "                      \"table\": {",
                "                        \"dataSource\": [",
                "                          {",
                "                            \"id\": \"6a1979aa3a8e5260c465758c\",",
                "                            \"number\": \"YT2605306913U001<br/>1/2\",",
                "                            \"size\": \"8.00(kg)<br>40×40×40(cm)\",",
                "                            \"volume\": \"17.68<br>54×45×32(cm)\",",
                "                            \"status\": \"转运中\"",
                "                          },",
                "                          {",
                "                            \"id\": \"6a1979aa3a8e5260c465758d\",",
                "                            \"number\": \"YT2605306913U002<br/>2/2\",",
                "                            \"size\": \"8.00(kg)<br>40×40×40(cm)\",",
                "                            \"volume\": \"17.58<br>53×47×32(cm)\",",
                "                            \"status\": \"转运中\"",
                "                          }",
                "                        ]",
                "                      }",
                "                    }",
                "                  }",
                "                ]",
                "              }",
                "            }",
                "          }",
                "        ]",
                "      }",
                "    ]",
                "  }",
                "}"
        );
    }

    private static String warehouseReceivedViewJson() {
        return json(
                "{",
                "  \"success\": 1,",
                "  \"data\": {",
                "    \"rows\": [",
                "      {",
                "        \"components\": [",
                "          {",
                "            \"data\": {",
                "              \"items\": [",
                "                { \"title\": \"状态\", \"value\": \"已提回海外仓，待拆柜派送\" }",
                "              ],",
                "              \"dataSource\": [",
                "                {",
                "                  \"title\": \"2026-07-05 09:30:00\",",
                "                  \"info\": \"已提回海外仓，待拆柜派送\",",
                "                  \"event\": \"\"",
                "                }",
                "              ]",
                "            }",
                "          }",
                "        ]",
                "      }",
                "    ]",
                "  }",
                "}"
        );
    }

    private static String packingListJson() {
        return json(
                "{",
                "  \"success\": 1,",
                "  \"data\": {",
                "    \"body\": {",
                "      \"components\": [",
                "        {",
                "          \"data\": {",
                "            \"table\": {",
                "              \"dataSource\": [",
                "                {",
                "                  \"id\": \"YT2605306913U001\",",
                "                  \"number\": \"YT2605306913U001<br>1/2\",",
                "                  \"sku\": \"SGGRB142\",",
                "                  \"name_zh\": \"8个装-透明抽屉隔板\",",
                "                  \"name_en\": \"8-Pack Clear Drawer Dividers\",",
                "                  \"unit_value\": \"38.000\",",
                "                  \"qty\": \"16\",",
                "                  \"photo_url\": [],",
                "                  \"photos\": []",
                "                },",
                "                {",
                "                  \"id\": \"YT2605306913U001\",",
                "                  \"number\": \"YT2605306913U001<br>1/2\",",
                "                  \"sku\": \"PAPERSAYSB158\",",
                "                  \"name_zh\": \"蓝色可擦笔12支\",",
                "                  \"name_en\": \"12 Blue Erasable Pens\",",
                "                  \"qty\": \"80\"",
                "                },",
                "                {",
                "                  \"id\": \"YT2605306913U002\",",
                "                  \"number\": \"YT2605306913U002<br>2/2\",",
                "                  \"sku\": \"SGGRB142\",",
                "                  \"name_zh\": \"8个装-透明抽屉隔板\",",
                "                  \"qty\": \"24\"",
                "                }",
                "              ]",
                "            }",
                "          }",
                "        },",
                "        {",
                "          \"data\": {",
                "            \"columns\": [",
                "              {",
                "                \"data\": [",
                "                  {",
                "                    \"data\": {",
                "                      \"label\": \"产品总数\",",
                "                      \"value\": 120",
                "                    }",
                "                  }",
                "                ]",
                "              }",
                "            ]",
                "          }",
                "        }",
                "      ]",
                "    }",
                "  }",
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
