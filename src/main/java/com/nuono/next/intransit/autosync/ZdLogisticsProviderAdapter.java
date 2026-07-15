package com.nuono.next.intransit.autosync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncBatch;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncPackage;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncSourceBatchExpectation;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ZdLogisticsProviderAdapter implements LogisticsProviderAdapter {
    private static final int PROVIDER_SUCCESS_CODE = 20000;
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private final LogisticsAutoSyncProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZdLogisticsProviderAdapter(LogisticsAutoSyncProperties properties) {
        this(properties, Clock.system(SHANGHAI_ZONE));
    }

    ZdLogisticsProviderAdapter(LogisticsAutoSyncProperties properties, Clock clock) {
        this.properties = properties == null ? new LogisticsAutoSyncProperties() : properties;
        this.clock = clock == null ? Clock.system(SHANGHAI_ZONE) : clock;
    }

    @Override
    public String sourceSystem() {
        return "ZD";
    }

    @Override
    public LogisticsProviderFetchResult fetch(LogisticsProviderFetchRequest request) {
        LogisticsAutoSyncProperties.Zd zd = zdProperties();
        if (!zd.isEnabled()) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.CONFIGURATION_ERROR,
                    "众鸫自动同步 HTTP 拉取未启用。"
            );
        }
        if (!validConfiguration(zd)) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.CONFIGURATION_ERROR,
                    "众鸫自动同步缺少接口配置。"
            );
        }
        if (Math.max(0, zd.getLookbackDays()) + Math.max(0, zd.getLookaheadDays()) > 60) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.CONFIGURATION_ERROR,
                    "众鸫自动同步查询跨度不能超过 60 天。"
            );
        }
        if (request == null
                || !StringUtils.hasText(request.getLoginAccount())
                || !StringUtils.hasText(request.getPassword())) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.INVALID_CREDENTIAL,
                    "众鸫自动同步账号或密码为空。"
            );
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds(zd)))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            String token = login(client, zd, request);
            LocalDate today = LocalDate.now(clock.withZone(SHANGHAI_ZONE));
            LocalDate fromDate = today.minusDays(Math.max(0, zd.getLookbackDays()));
            LocalDate toDate = today.plusDays(Math.max(0, zd.getLookaheadDays()));
            String expressBody = get(client, zd, zd.getExpressPath(), token, fromDate, toDate);
            String boxBody = get(client, zd, zd.getBoxPath(), token, fromDate, toDate);
            PluginSyncCommand command = normalize(expressBody, boxBody);
            LogisticsProviderFetchResult result = LogisticsProviderFetchResult.success(command);
            result.setPackageCount(countPackages(command));
            result.setLineCount(0);
            result.setNodeCount(0);
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.NETWORK_ERROR,
                    "众鸫自动同步请求被中断。"
            );
        } catch (IOException exception) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.NETWORK_ERROR,
                    "众鸫自动同步网络请求失败。"
            );
        } catch (ProviderException exception) {
            return LogisticsProviderFetchResult.failure(exception.failureCode, exception.getMessage());
        } catch (RuntimeException exception) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.PROVIDER_ERROR,
                    "众鸫自动同步返回数据解析失败。"
            );
        }
    }

    PluginSyncCommand normalize(String expressBody, String boxBody) {
        List<JsonNode> expressRows = dataRows(requireProviderSuccess(expressBody, "众鸫物流批次接口返回异常。"));
        List<JsonNode> boxRows = dataRows(requireProviderSuccess(boxBody, "众鸫箱子接口返回异常。"));
        Map<String, PluginSyncBatch> batchesByEntryNumber = new LinkedHashMap<>();
        Map<String, String> trackingByEntryNumber = new LinkedHashMap<>();
        Map<String, Integer> expectedBoxesByEntryNumber = new LinkedHashMap<>();

        for (JsonNode row : expressRows) {
            String entryNumber = requiredText(row, "entryNumber", "众鸫物流批次缺少入仓号。");
            String key = normalizeKey(entryNumber);
            if (batchesByEntryNumber.containsKey(key)) {
                throw providerError("众鸫物流批次存在重复入仓号。");
            }
            String transportation = text(row, "transportation");
            String deliveryType = text(row, "deliveryType");
            String warehouseName = text(row, "warehouseName");
            String rawStatus = text(row, "expressStatus");
            String trackingNo = requiredText(row, "expressNumber", "众鸫物流批次缺少物流单号。");

            PluginSyncBatch batch = new PluginSyncBatch();
            batch.setBatchNo(entryNumber);
            batch.setSourceStatus(mapStatus(rawStatus));
            batch.setRawStatus(rawStatus);
            batch.setTransportMode(mapTransportMode(transportation));
            batch.setDestination(mapDestination(transportation, deliveryType, warehouseName));
            batch.setTargetWarehouseName(firstText(deliveryType, warehouseName));
            batch.setTrackingNo(trackingNo);
            batch.setExternalShipmentNo(firstText(text(row, "id"), trackingNo));
            batch.setSourceCreatedAt(text(row, "gmtCreate"));
            batch.setPackages(new ArrayList<>());
            batchesByEntryNumber.put(key, batch);
            trackingByEntryNumber.put(key, trackingNo);
            expectedBoxesByEntryNumber.put(key, integerValue(row, "quantity"));
        }

        Set<String> packageKeys = new HashSet<>();
        for (JsonNode row : boxRows) {
            String entryNumber = requiredText(row, "entryNumber", "众鸫箱子缺少入仓号。");
            String entryKey = normalizeKey(entryNumber);
            PluginSyncBatch batch = batchesByEntryNumber.get(entryKey);
            if (batch == null) {
                throw providerError("众鸫箱子无法匹配物流批次。");
            }
            String boxCode = requiredText(row, "boxCode", "众鸫箱子缺少箱号。");
            if (!packageKeys.add(entryKey + "\n" + normalizeKey(boxCode))) {
                throw providerError("众鸫同一物流批次存在重复箱号。");
            }
            String boxTrackingNo = text(row, "expressNumber");
            if (StringUtils.hasText(boxTrackingNo)
                    && !sameText(boxTrackingNo, trackingByEntryNumber.get(entryKey))) {
                throw providerError("众鸫箱子物流单号与批次不一致。");
            }
            PluginSyncPackage itemPackage = new PluginSyncPackage();
            itemPackage.setBoxNo(boxCode);
            itemPackage.setExternalBoxNo(boxCode);
            itemPackage.setTrackingNo(firstText(boxTrackingNo, trackingByEntryNumber.get(entryKey)));
            itemPackage.setLines(List.of());
            batch.getPackages().add(itemPackage);
        }

        PluginSyncCommand command = new PluginSyncCommand();
        command.setSourceSystem("ZD");
        command.setForwarderName("众鸫");
        command.setBatches(new ArrayList<>(batchesByEntryNumber.values()));
        List<PluginSyncSourceBatchExpectation> expectations = new ArrayList<>();
        for (Map.Entry<String, PluginSyncBatch> entry : batchesByEntryNumber.entrySet()) {
            PluginSyncSourceBatchExpectation expectation = new PluginSyncSourceBatchExpectation();
            expectation.setBatchNo(entry.getValue().getBatchNo());
            expectation.setBoxNum(expectedBoxesByEntryNumber.get(entry.getKey()));
            expectation.setTotalQuantity(null);
            expectations.add(expectation);
        }
        command.setSourceBatchExpectations(expectations);
        return command;
    }

    private String login(
            HttpClient client,
            LogisticsAutoSyncProperties.Zd zd,
            LogisticsProviderFetchRequest request
    ) throws IOException, InterruptedException {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("name", base64(request.getLoginAccount().trim()));
        payload.put("pwd", base64(request.getPassword()));
        HttpRequest loginRequest = requestBuilder(zd, zd.getLoginPath())
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)))
                .build();
        JsonNode root = parseJson(send(client, loginRequest));
        if (root.path("code").asInt(Integer.MIN_VALUE) != PROVIDER_SUCCESS_CODE) {
            throw new ProviderException(LogisticsProviderFailureCode.INVALID_CREDENTIAL, "众鸫登录失败或账号无权限。");
        }
        String token = text(root.path("data"), "token");
        if (!StringUtils.hasText(token)) {
            throw new ProviderException(LogisticsProviderFailureCode.INVALID_CREDENTIAL, "众鸫登录未返回有效凭证。");
        }
        return token;
    }

    private String get(
            HttpClient client,
            LogisticsAutoSyncProperties.Zd zd,
            String path,
            String token,
            LocalDate fromDate,
            LocalDate toDate
    ) throws IOException, InterruptedException {
        String queryPath = path
                + (path.contains("?") ? "&" : "?")
                + "fromDate=" + url(fromDate.toString())
                + "&toDate=" + url(toDate.toString());
        HttpRequest request = requestBuilder(zd, queryPath)
                .header("X-Token", token)
                .GET()
                .build();
        return send(client, request);
    }

    private String send(HttpClient client, HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ProviderException(LogisticsProviderFailureCode.INVALID_CREDENTIAL, "众鸫登录失效或账号无权限。");
        }
        if (response.statusCode() >= 400) {
            throw providerError("众鸫接口请求失败。");
        }
        return response.body() == null ? "" : response.body();
    }

    private HttpRequest.Builder requestBuilder(LogisticsAutoSyncProperties.Zd zd, String path) {
        return HttpRequest.newBuilder(uri(zd.getBaseUrl(), path))
                .timeout(Duration.ofSeconds(timeoutSeconds(zd)))
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", trailingSlash(zd.getBaseUrl()))
                .header("User-Agent", "Nuono-ZD-Logistics-Sync/1.0");
    }

    private JsonNode requireProviderSuccess(String body, String fallbackMessage) {
        JsonNode root = parseJson(body);
        if (root.path("code").asInt(Integer.MIN_VALUE) != PROVIDER_SUCCESS_CODE) {
            String message = text(root, "msg");
            throw providerError(StringUtils.hasText(message) ? "众鸫接口返回业务错误：" + message : fallbackMessage);
        }
        return root;
    }

    private List<JsonNode> dataRows(JsonNode root) {
        JsonNode sections = root.path("data");
        if (!sections.isArray() || sections.isEmpty()) {
            return List.of();
        }
        JsonNode data = sections.get(0).path("data");
        if (!data.isArray()) {
            throw providerError("众鸫接口 data[0].data 结构异常。");
        }
        List<JsonNode> rows = new ArrayList<>();
        data.forEach(rows::add);
        return rows;
    }

    private String mapStatus(String value) {
        String normalized = clean(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.contains("未签收")) {
            return "in_transit";
        }
        if (normalized.contains("签收")) {
            return "warehouse_received";
        }
        if (normalized.contains("取消")) {
            return "cancelled";
        }
        if (normalized.contains("异常")) {
            return "exception";
        }
        return normalized;
    }

    private String mapTransportMode(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT);
        if (normalized.contains("空运") || normalized.contains("AIR")) {
            return "AIR";
        }
        if (normalized.contains("海运") || normalized.contains("SEA")) {
            return "SEA";
        }
        return value;
    }

    private String mapDestination(String... values) {
        String joined = String.join(" ", values == null ? new String[0] : values).toUpperCase(Locale.ROOT);
        if (joined.contains("沙特") || joined.contains("KSA") || joined.contains("SAUDI")) {
            return "RUH";
        }
        if (joined.contains("阿联酋") || joined.contains("UAE") || joined.contains("迪拜") || joined.contains("DUBAI")) {
            return "DB";
        }
        return null;
    }

    private int countPackages(PluginSyncCommand command) {
        return command.getBatches().stream().mapToInt(batch -> batch.getPackages().size()).sum();
    }

    private boolean validConfiguration(LogisticsAutoSyncProperties.Zd zd) {
        return StringUtils.hasText(zd.getBaseUrl())
                && StringUtils.hasText(zd.getLoginPath())
                && StringUtils.hasText(zd.getExpressPath())
                && StringUtils.hasText(zd.getBoxPath());
    }

    private LogisticsAutoSyncProperties.Zd zdProperties() {
        return properties.getZd() == null ? new LogisticsAutoSyncProperties.Zd() : properties.getZd();
    }

    private int timeoutSeconds(LogisticsAutoSyncProperties.Zd zd) {
        return Math.max(5, zd.getTimeoutSeconds());
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "" : body);
        } catch (JsonProcessingException exception) {
            throw providerError("众鸫接口返回的不是有效 JSON。");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw providerError("众鸫登录请求构造失败。");
        }
    }

    private String requiredText(JsonNode row, String field, String message) {
        String value = text(row, field);
        if (!StringUtils.hasText(value)) {
            throw providerError(message);
        }
        return value;
    }

    private String text(JsonNode row, String field) {
        if (row == null || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode value = row.path(field);
        return value.isMissingNode() || value.isNull() ? null : clean(value.asText());
    }

    private Integer integerValue(JsonNode row, String field) {
        JsonNode value = row == null ? null : row.path(field);
        if (value == null || value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())) {
            return null;
        }
        try {
            return value.isIntegralNumber() ? value.intValue() : Integer.valueOf(value.asText().trim());
        } catch (NumberFormatException exception) {
            throw providerError("众鸫物流批次箱数格式异常。");
        }
    }

    private String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return clean(value);
                }
            }
        }
        return null;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeKey(String value) {
        return clean(value).toUpperCase(Locale.ROOT);
    }

    private boolean sameText(String left, String right) {
        return normalizeKey(left).equals(normalizeKey(right));
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private URI uri(String baseUrl, String path) {
        String base = clean(baseUrl).replaceAll("/+$", "");
        String suffix = clean(path);
        return URI.create(base + (suffix.startsWith("/") ? suffix : "/" + suffix));
    }

    private String trailingSlash(String value) {
        String clean = clean(value);
        return clean.endsWith("/") ? clean : clean + "/";
    }

    private ProviderException providerError(String message) {
        return new ProviderException(LogisticsProviderFailureCode.PROVIDER_ERROR, message);
    }

    private static final class ProviderException extends RuntimeException {
        private final String failureCode;

        private ProviderException(String failureCode, String message) {
            super(message);
            this.failureCode = failureCode;
        }
    }
}
