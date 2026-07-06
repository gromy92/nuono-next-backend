package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncPreviewView;
import com.nuono.next.intransit.InTransitPluginSyncService;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogisticsAutoSyncSchedulerTest {

    @Mock
    private LogisticsAutoSyncMapper mapper;
    @Mock
    private LogisticsAutoSyncService service;

    @Test
    void disabledSchedulerReturnsZeroAndDoesNotLoadAccounts() {
        LogisticsAutoSyncScheduler scheduler = scheduler(false, 5);

        int count = scheduler.runOnce();

        assertThat(count).isZero();
        verify(mapper, never()).listDueAccounts(5);
    }

    @Test
    void enabledSchedulerSkipsWhenAlreadyRunning() {
        LogisticsAutoSyncScheduler scheduler = scheduler(true, 5);
        markRunning(scheduler);

        int count = scheduler.runOnce();

        assertThat(count).isZero();
        verify(mapper, never()).listDueAccounts(5);
    }

    @Test
    void enabledSchedulerRunsAtMostConfiguredAccounts() {
        LogisticsAutoSyncScheduler scheduler = scheduler(true, 2);
        LogisticsAutoSyncAccount first = account(180001L);
        LogisticsAutoSyncAccount second = account(180002L);
        LogisticsAutoSyncAccount third = account(180003L);
        when(mapper.listDueAccounts(2)).thenReturn(List.of(first, second, third));

        int count = scheduler.runOnce();

        assertThat(count).isEqualTo(2);
        verify(service).runAccount(first);
        verify(service).runAccount(second);
        verify(service, never()).runAccount(third);
    }

    @Test
    void schedulerSkipsAccountsOutsideWindowOrCooldown() {
        LogisticsAutoSyncScheduler scheduler = scheduler(true, 10);
        LogisticsAutoSyncAccount outsideWindow = account(180001L);
        outsideWindow.setScheduleWindowStart(LocalTime.of(10, 0));
        outsideWindow.setScheduleWindowEnd(LocalTime.of(11, 0));
        LogisticsAutoSyncAccount coolingDown = account(180002L);
        coolingDown.setCooldownUntil(LocalDateTime.of(2026, 7, 6, 13, 0));
        LogisticsAutoSyncAccount runnable = account(180003L);
        when(mapper.listDueAccounts(10)).thenReturn(List.of(outsideWindow, coolingDown, runnable));

        int count = scheduler.runOnce();

        assertThat(count).isEqualTo(1);
        verify(service, never()).runAccount(outsideWindow);
        verify(service, never()).runAccount(coolingDown);
        verify(service).runAccount(runnable);
    }

    @Test
    void runOnceIsPackagePrivateNotPublicApi() throws Exception {
        assertThat(Modifier.isPublic(LogisticsAutoSyncScheduler.class.getDeclaredMethod("runOnce").getModifiers()))
                .isFalse();
    }

    @Test
    void scheduledTickPullsYiteDataThroughHttpProviderAndStopsAtPreviewOnly() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            server.handle("/login", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(readBody(exchange)).contains("scheduled-user").contains("scheduled-password");
                exchange.getResponseHeaders().add("Set-Cookie", "token=scheduled-token; Path=/");
                sendJson(exchange, "{}");
            });
            server.handle("/rest/tms/wos/shipment/lists", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("scheduled-token");
                sendJson(exchange, yiteListJson());
            });
            server.handle("/rest/tms/wos/shipment/view", exchange -> {
                assertThat(exchange.getRequestURI().getRawQuery()).contains("id=shipment-record-1");
                sendJson(exchange, yiteViewJson());
            });
            server.handle("/rest/tms/wos/shipment/change_declaration_view", exchange -> {
                assertThat(exchange.getRequestURI().getRawQuery()).contains("activeTab=packing_list");
                assertThat(exchange.getRequestURI().getRawQuery()).contains("id=shipment-record-1");
                sendJson(exchange, yitePackingListJson());
            });

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getScheduler().setEnabled(true);
            properties.getScheduler().setMaxAccountsPerTick(1);
            properties.getYite().setEnabled(true);
            properties.getYite().setBaseUrl(server.baseUrl());
            properties.getYite().setLoginPath("/login");
            YiteLogisticsProviderAdapter provider = new YiteLogisticsProviderAdapter(properties);

            LogisticsAutoSyncAccessContextFactory accessContextFactory = Mockito.mock(LogisticsAutoSyncAccessContextFactory.class);
            InTransitPluginSyncService pluginSyncService = Mockito.mock(InTransitPluginSyncService.class);
            OperationalTaskService taskService = Mockito.mock(OperationalTaskService.class);
            LogisticsAutoSyncAccount account = scheduledYiteAccount();
            when(mapper.listDueAccounts(1)).thenReturn(List.of(account));
            when(accessContextFactory.requireAccessContext(account)).thenReturn(context());
            PluginSyncPreviewView preview = new PluginSyncPreviewView();
            preview.setCommittable(true);
            preview.setBatchCount(1);
            preview.setPackageCount(1);
            preview.setLineCount(1);
            preview.setNodeCount(1);
            when(pluginSyncService.preview(any(PluginSyncCommand.class))).thenReturn(preview);
            OperationalTask task = new OperationalTask();
            task.setId(92001L);
            when(taskService.start(eq("LOGISTICS_AUTO_SYNC"), any(), any(OperationalTaskPayload.class))).thenReturn(task);

            LogisticsAutoSyncService realService = new LogisticsAutoSyncService(
                    List.of(provider),
                    cipher(),
                    accessContextFactory,
                    pluginSyncService,
                    taskService,
                    mapper
            );
            LogisticsAutoSyncScheduler scheduler = new LogisticsAutoSyncScheduler(
                    properties,
                    mapper,
                    realService,
                    Clock.fixed(Instant.parse("2026-07-06T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
            );

            int executed = scheduler.runOnce();

            assertThat(executed).isEqualTo(1);
            ArgumentCaptor<PluginSyncCommand> commandCaptor = ArgumentCaptor.forClass(PluginSyncCommand.class);
            verify(pluginSyncService).preview(commandCaptor.capture());
            verify(pluginSyncService, never()).commit(any());
            assertThat(commandCaptor.getValue().getSourceSystem()).isEqualTo("YITE");
            assertThat(commandCaptor.getValue().getBatches()).hasSize(1);
            assertThat(commandCaptor.getValue().getBatches().get(0).getBatchNo()).isEqualTo("YT2607000001");
            verify(taskService).complete(eq(92001L), any(), eq("物流自动同步完成：预览通过，账号未开启自动提交。"));
            verify(mapper).updateAccountRunState(
                    eq(180901L),
                    eq(92001L),
                    eq("SUCCESS"),
                    eq("SUCCESS"),
                    eq("PREVIEW_ONLY"),
                    eq("READY"),
                    any(LocalDateTime.class),
                    any(LocalDateTime.class),
                    eq(null),
                    eq(null),
                    eq(null),
                    eq(408L)
            );
        }
    }

    @Test
    void scheduledTickPullsChicDataThroughHttpProviderAndStopsAtPreviewOnly() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            server.handle("/login", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(readBody(exchange)).contains("scheduled-user").contains("scheduled-password");
                sendJson(exchange, "{\"data\":{\"token\":\"scheduled-chic-token\"}}");
            });
            server.handle("/api/purchase/purchase-order/purchaseBatch/query", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("scheduled-chic-token");
                assertThat(readBody(exchange)).contains("\"rows\":1");
                sendJson(exchange, chicListJson());
            });
            server.handle("/api/purchase/purchase-order/purchaseBatch/detail", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("scheduled-chic-token");
                assertThat(readBody(exchange)).contains("53000");
                sendJson(exchange, chicDetailJson());
            });
            server.handle("/api/order/report/list", exchange -> {
                assertThat(exchange.getRequestMethod()).isEqualTo("GET");
                assertThat(exchange.getRequestHeaders().getFirst("token")).isEqualTo("scheduled-chic-token");
                sendJson(exchange, chicOrderReportJson());
            });

            LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
            properties.getScheduler().setEnabled(true);
            properties.getScheduler().setMaxAccountsPerTick(1);
            properties.getChic().setEnabled(true);
            properties.getChic().setBaseUrl(server.baseUrl());
            properties.getChic().setLoginPath("/login");
            ChicLogisticsProviderAdapter provider = new ChicLogisticsProviderAdapter(properties);

            LogisticsAutoSyncAccessContextFactory accessContextFactory = Mockito.mock(LogisticsAutoSyncAccessContextFactory.class);
            InTransitPluginSyncService pluginSyncService = Mockito.mock(InTransitPluginSyncService.class);
            OperationalTaskService taskService = Mockito.mock(OperationalTaskService.class);
            LogisticsAutoSyncAccount account = scheduledChicAccount();
            when(mapper.listDueAccounts(1)).thenReturn(List.of(account));
            when(accessContextFactory.requireAccessContext(account)).thenReturn(context());
            PluginSyncPreviewView preview = new PluginSyncPreviewView();
            preview.setCommittable(true);
            preview.setBatchCount(1);
            preview.setPackageCount(1);
            preview.setLineCount(1);
            preview.setNodeCount(1);
            when(pluginSyncService.preview(any(PluginSyncCommand.class))).thenReturn(preview);
            OperationalTask task = new OperationalTask();
            task.setId(92002L);
            when(taskService.start(eq("LOGISTICS_AUTO_SYNC"), any(), any(OperationalTaskPayload.class))).thenReturn(task);

            LogisticsAutoSyncService realService = new LogisticsAutoSyncService(
                    List.of(provider),
                    cipher(),
                    accessContextFactory,
                    pluginSyncService,
                    taskService,
                    mapper
            );
            LogisticsAutoSyncScheduler scheduler = new LogisticsAutoSyncScheduler(
                    properties,
                    mapper,
                    realService,
                    Clock.fixed(Instant.parse("2026-07-06T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
            );

            int executed = scheduler.runOnce();

            assertThat(executed).isEqualTo(1);
            ArgumentCaptor<PluginSyncCommand> commandCaptor = ArgumentCaptor.forClass(PluginSyncCommand.class);
            verify(pluginSyncService).preview(commandCaptor.capture());
            verify(pluginSyncService, never()).commit(any());
            assertThat(commandCaptor.getValue().getSourceSystem()).isEqualTo("CHIC");
            assertThat(commandCaptor.getValue().getBatches()).hasSize(1);
            assertThat(commandCaptor.getValue().getBatches().get(0).getBatchNo()).isEqualTo("XGGEKSA04070");
            assertThat(commandCaptor.getValue().getBatches().get(0).getExternalShipmentNo()).isEqualTo("CHIC-SHIP-04070");
            verify(taskService).complete(eq(92002L), any(), eq("物流自动同步完成：预览通过，账号未开启自动提交。"));
            verify(mapper).updateAccountRunState(
                    eq(180902L),
                    eq(92002L),
                    eq("SUCCESS"),
                    eq("SUCCESS"),
                    eq("PREVIEW_ONLY"),
                    eq("READY"),
                    any(LocalDateTime.class),
                    any(LocalDateTime.class),
                    eq(null),
                    eq(null),
                    eq(null),
                    eq(408L)
            );
        }
    }

    private LogisticsAutoSyncScheduler scheduler(boolean enabled, int maxAccountsPerTick) {
        LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
        properties.getScheduler().setEnabled(enabled);
        properties.getScheduler().setMaxAccountsPerTick(maxAccountsPerTick);
        return new LogisticsAutoSyncScheduler(
                properties,
                mapper,
                service,
                Clock.fixed(Instant.parse("2026-07-06T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
    }

    private static LogisticsAutoSyncAccount account(Long id) {
        LogisticsAutoSyncAccount account = new LogisticsAutoSyncAccount();
        account.setId(id);
        account.setEnabled(true);
        account.setScheduleEnabled(true);
        return account;
    }

    private static void markRunning(LogisticsAutoSyncScheduler scheduler) {
        try {
            java.lang.reflect.Field field = LogisticsAutoSyncScheduler.class.getDeclaredField("running");
            field.setAccessible(true);
            ((AtomicBoolean) field.get(scheduler)).set(true);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static LogisticsAutoSyncAccount scheduledYiteAccount() {
        LogisticsAutoSyncAccount account = account(180901L);
        account.setOwnerUserId(307L);
        account.setOperatorUserId(408L);
        account.setSourceSystem("YITE");
        account.setForwarderName("义特");
        account.setLoginAccount("scheduled-user");
        account.setPasswordCipher(cipher().encrypt("scheduled-password"));
        account.setCommitEnabled(false);
        account.setMinIntervalHours(24);
        return account;
    }

    private static LogisticsAutoSyncAccount scheduledChicAccount() {
        LogisticsAutoSyncAccount account = account(180902L);
        account.setOwnerUserId(307L);
        account.setOperatorUserId(408L);
        account.setSourceSystem("CHIC");
        account.setForwarderName("启客");
        account.setLoginAccount("scheduled-user");
        account.setPasswordCipher(cipher().encrypt("scheduled-password"));
        account.setCommitEnabled(false);
        account.setMinIntervalHours(24);
        return account;
    }

    private static LogisticsCredentialCipher cipher() {
        LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
        properties.setCredentialCipherSecret("test-logistics-cipher-secret");
        return new LogisticsCredentialCipher(properties);
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(408L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .menuPaths(Set.of("/purchase/in-transit-goods"))
                .storeCodes(Set.of("STR108065-NSA"))
                .build();
    }

    private static String yiteListJson() {
        return json(
                "{",
                "  \"success\": 1,",
                "  \"data\": {",
                "    \"components\": {",
                "      \"gridView\": {",
                "        \"table\": {",
                "          \"dataSource\": [",
                "            {",
                "              \"id\": \"shipment-record-1\",",
                "              \"shipment_number\": \"YT2607000001\",",
                "              \"service\": \"沙特海运双清\",",
                "              \"to_address_country\": \"沙特阿拉伯\",",
                "              \"parcel_count\": 1,",
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

    private static String yiteViewJson() {
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
                "                { \"title\": \"服务类型\", \"value\": \"沙特海运双清\" },",
                "                { \"title\": \"发往国家\", \"value\": \"沙特阿拉伯\" },",
                "                { \"title\": \"状态\", \"value\": \"转运中\" }",
                "              ],",
                "              \"dataSource\": [",
                "                {",
                "                  \"title\": \"2026-06-03 17:10:22\",",
                "                  \"info\": \"预配航期 ETD: 6-7 ETA:6-30\",",
                "                  \"event\": \"\"",
                "                },",
                "                {",
                "                  \"id\": \"box-row-1\",",
                "                  \"number\": \"YT2607000001U001<br/>1/1\",",
                "                  \"volume\": \"17.68<br>54×45×32(cm)\",",
                "                  \"status\": \"转运中\"",
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

    private static String yitePackingListJson() {
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
                "                  \"id\": \"YT2607000001U001\",",
                "                  \"number\": \"YT2607000001U001<br>1/1\",",
                "                  \"sku\": \"SGGRB142\",",
                "                  \"name_zh\": \"8个装-透明抽屉隔板\",",
                "                  \"qty\": \"16\"",
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
                "                  { \"data\": { \"label\": \"产品总数\", \"value\": 16 } }",
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

    private static String chicListJson() {
        return json(
                "{",
                "  \"data\": {",
                "    \"records\": [",
                "      {",
                "        \"purchaseBatchId\": 53000,",
                "        \"purchaseBatchSn\": \"XGGEKSA04070\",",
                "        \"boxNum\": 1,",
                "        \"totalQuantity\": 30",
                "      }",
                "    ]",
                "  }",
                "}"
        );
    }

    private static String chicDetailJson() {
        return json(
                "{",
                "  \"data\": {",
                "    \"purchaseBatchSn\": \"XGGEKSA04070\",",
                "    \"destination\": \"SA\",",
                "    \"transportMode\": \"SEA\",",
                "    \"purchaseOrderList\": [",
                "      {",
                "        \"warehousingSn\": \"XGGEKSA04070-1\",",
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
        );
    }

    private static String chicOrderReportJson() {
        return json(
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
