package com.nuono.next.intransit.autosync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncBatch;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncLine;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncNode;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncPackage;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncSourceBatchExpectation;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class YiteLogisticsProviderAdapter implements LogisticsProviderAdapter {
    static final int DEFAULT_SHIPMENT_LIMIT = 10;

    private static final DateTimeFormatter NODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FLEXIBLE_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-M-d")
            .optionalStart()
            .appendLiteral(' ')
            .appendPattern("H:mm")
            .optionalStart()
            .appendPattern(":ss")
            .optionalEnd()
            .optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter();
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern SHIPMENT_NO_PATTERN = Pattern.compile("[A-Z]{1,8}\\d{6,}\\w*", Pattern.CASE_INSENSITIVE);

    private final LogisticsAutoSyncProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public YiteLogisticsProviderAdapter(LogisticsAutoSyncProperties properties) {
        this.properties = properties == null ? new LogisticsAutoSyncProperties() : properties;
    }

    @Override
    public String sourceSystem() {
        return "YITE";
    }

    @Override
    public LogisticsProviderFetchResult fetch(LogisticsProviderFetchRequest request) {
        LogisticsAutoSyncProperties.Yite yite = yiteProperties();
        if (!yite.isEnabled()) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.CONFIGURATION_ERROR,
                    "义特自动同步 HTTP 拉取未启用。"
            );
        }
        if (!StringUtils.hasText(yite.getBaseUrl()) || !StringUtils.hasText(yite.getLoginPath())) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.CONFIGURATION_ERROR,
                    "义特自动同步缺少 baseUrl 或 loginPath 配置。"
            );
        }
        if (request == null || !StringUtils.hasText(request.getLoginAccount()) || !StringUtils.hasText(request.getPassword())) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.INVALID_CREDENTIAL,
                    "义特自动同步账号或密码为空。"
            );
        }

        try {
            FetchSession session = login(yite, request);
            int limit = Math.max(1, Math.min(DEFAULT_SHIPMENT_LIMIT, request.getRecentLimit() <= 0 ? DEFAULT_SHIPMENT_LIMIT : request.getRecentLimit()));
            String listBody = postJson(session, "/rest/tms/wos/shipment/lists", buildShipmentListPayload(limit));
            List<JsonNode> shipmentRows = extractShipmentRows(parseJson(listBody), limit);
            Map<String, String> viewJsonByShipmentRecordId = new LinkedHashMap<>();
            Map<String, String> packingListJsonByShipmentRecordId = new LinkedHashMap<>();
            for (JsonNode row : shipmentRows) {
                String recordId = pickText(row, "id");
                if (!StringUtils.hasText(recordId)) {
                    continue;
                }
                viewJsonByShipmentRecordId.put(recordId, get(session, buildShipmentViewPath(recordId)));
                packingListJsonByShipmentRecordId.put(recordId, get(session, buildShipmentPackingListPath(recordId)));
            }
            PluginSyncCommand command = normalize(listBody, viewJsonByShipmentRecordId, packingListJsonByShipmentRecordId);
            LogisticsProviderFetchResult result = LogisticsProviderFetchResult.success(command);
            result.setPackageCount(countPackages(command));
            result.setLineCount(countLines(command));
            result.setNodeCount(countNodes(command));
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LogisticsProviderFetchResult.failure(LogisticsProviderFailureCode.NETWORK_ERROR, "义特自动同步请求被中断。");
        } catch (IOException exception) {
            return LogisticsProviderFetchResult.failure(LogisticsProviderFailureCode.NETWORK_ERROR, "义特自动同步网络请求失败。");
        } catch (ProviderAuthException exception) {
            return LogisticsProviderFetchResult.failure(exception.failureCode, exception.getMessage());
        } catch (RuntimeException exception) {
            return LogisticsProviderFetchResult.failure(LogisticsProviderFailureCode.PROVIDER_ERROR, "义特自动同步返回数据解析失败。");
        }
    }

    private FetchSession login(LogisticsAutoSyncProperties.Yite yite, LogisticsProviderFetchRequest request)
            throws IOException, InterruptedException {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds(yite.getTimeoutSeconds())))
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        FetchSession session = new FetchSession(yite.getBaseUrl(), httpClient, cookieManager);
        Map<String, String> loginPayload = new LinkedHashMap<>();
        loginPayload.put(defaultText(yite.getLoginAccountField(), "username"), request.getLoginAccount());
        loginPayload.put(defaultText(yite.getLoginPasswordField(), "password"), request.getPassword());
        HttpRequest.Builder builder = requestBuilder(session, yite.getLoginPath())
                .header("accept", "application/json");
        String payloadType = defaultText(yite.getLoginPayloadType(), "json").trim().toLowerCase(Locale.ROOT);
        HttpRequest requestMessage;
        if ("form".equals(payloadType)) {
            requestMessage = builder
                    .header("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(toForm(loginPayload)))
                    .build();
        } else {
            requestMessage = builder
                    .header("content-type", "application/json;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(loginPayload)))
                    .build();
        }
        String loginBody = requireJsonBody(send(session, requestMessage), "义特登录接口返回异常。");
        return new FetchSession(yite.getBaseUrl(), httpClient, cookieManager, extractLoginToken(loginBody));
    }

    private String postJson(FetchSession session, String path, Object payload) throws IOException, InterruptedException {
        return requireJsonBody(send(session, requestBuilder(session, path)
                .header("accept", "application/json")
                .header("content-type", "application/json;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                .build()), "义特接口返回异常。");
    }

    private String get(FetchSession session, String path) throws IOException, InterruptedException {
        return requireJsonBody(send(session, requestBuilder(session, path)
                .header("accept", "application/json")
                .GET()
                .build()), "义特接口返回异常。");
    }

    private HttpResponse<String> send(FetchSession session, HttpRequest request) throws IOException, InterruptedException {
        return session.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder requestBuilder(FetchSession session, String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(session.baseUrl, path))
                .timeout(Duration.ofSeconds(timeoutSeconds((properties.getYite() == null ? new LogisticsAutoSyncProperties.Yite() : properties.getYite()).getTimeoutSeconds())))
                .header("token-type", "web");
        String token = firstText(session.token, sessionCookie(session, "token", "os_wos"));
        if (StringUtils.hasText(token)) {
            builder.header("token", token);
        }
        return builder;
    }

    private String requireJsonBody(HttpResponse<String> response, String errorMessage) {
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ProviderAuthException(LogisticsProviderFailureCode.INVALID_CREDENTIAL, "义特登录失败或账号无权限。");
        }
        if (response.statusCode() >= 400) {
            throw new ProviderAuthException(LogisticsProviderFailureCode.PROVIDER_ERROR, errorMessage);
        }
        String body = response.body() == null ? "" : response.body();
        if (looksLikeHtml(body)) {
            throw new ProviderAuthException(LogisticsProviderFailureCode.CAPTCHA_REQUIRED, "义特接口返回登录页面，可能需要验证码或风控验证。");
        }
        return body;
    }

    private String extractLoginToken(String body) {
        JsonNode root = parseJson(body);
        JsonNode success = readValue(root, "success");
        if (isExplicitFailure(success)) {
            String message = firstText(
                    pickText(root, "info", "message", "msg", "error"),
                    pickText(readValue(root, "data"), "info", "message", "msg", "error"),
                    "义特登录失败。"
            );
            throw new ProviderAuthException(loginFailureCode(message), "义特登录失败：" + message);
        }
        String token = firstText(
                pickText(root, "token", "accessToken", "access_token"),
                pickText(readValue(root, "data"), "token", "accessToken", "access_token")
        );
        return StringUtils.hasText(token) ? token.replaceFirst("(?i)^Bearer\\s+", "").trim() : null;
    }

    private boolean isExplicitFailure(JsonNode success) {
        if (success == null || success.isMissingNode() || success.isNull()) {
            return false;
        }
        if (success.isBoolean()) {
            return !success.asBoolean();
        }
        if (success.isNumber()) {
            return success.asInt() == 0;
        }
        if (success.isTextual()) {
            String value = success.asText().trim().toLowerCase(Locale.ROOT);
            return "0".equals(value) || "false".equals(value) || "fail".equals(value) || "failed".equals(value);
        }
        return false;
    }

    private String loginFailureCode(String message) {
        String normalized = StringUtils.hasText(message) ? message.toLowerCase(Locale.ROOT) : "";
        if (normalized.contains("captcha") || normalized.contains("验证码") || normalized.contains("风控")) {
            return LogisticsProviderFailureCode.CAPTCHA_REQUIRED;
        }
        return LogisticsProviderFailureCode.INVALID_CREDENTIAL;
    }

    public Map<String, Object> buildShipmentListPayload(int limit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timeLimit", 0);
        payload.put("page", 1);
        payload.put("pageSize", clampLimit(limit));
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

    public String buildShipmentViewPath(String shipmentRecordId) {
        return "/rest/tms/wos/shipment/view?id=" + encode(defaultText(shipmentRecordId, ""));
    }

    public String buildShipmentPackingListPath(String shipmentRecordId) {
        return "/rest/tms/wos/shipment/change_declaration_view?activeTab=packing_list&id="
                + encode(defaultText(shipmentRecordId, ""));
    }

    public PluginSyncCommand normalize(
            String shipmentListJson,
            Map<String, String> viewJsonByShipmentRecordId,
            Map<String, String> packingListJsonByShipmentRecordId
    ) {
        List<JsonNode> shipmentRows = extractShipmentRows(parseJson(shipmentListJson), DEFAULT_SHIPMENT_LIMIT);
        List<PluginSyncBatch> batches = new ArrayList<>();
        Map<String, PackingContext> packingContextByBatchNo = new LinkedHashMap<>();
        for (JsonNode row : shipmentRows) {
            String batchNo = normalizeShipmentNumber(row);
            if (!StringUtils.hasText(batchNo)) {
                continue;
            }
            String detailJson = findJsonForShipment(row, viewJsonByShipmentRecordId);
            String packingJson = findJsonForShipment(row, packingListJsonByShipmentRecordId);
            ViewContext viewContext = StringUtils.hasText(detailJson)
                    ? buildViewContext(parseJson(detailJson))
                    : new ViewContext();
            PackingContext packingContext = StringUtils.hasText(packingJson)
                    ? buildPackingContext(parseJson(packingJson))
                    : new PackingContext();
            packingContextByBatchNo.put(batchNo, packingContext);
            PluginSyncBatch batch = buildBatch(row, viewContext, packingContext.rows);
            if (batch != null) {
                batches.add(batch);
            }
        }

        PluginSyncCommand command = new PluginSyncCommand();
        command.setSourceSystem("YITE");
        command.setForwarderName("义特");
        command.setBatches(batches);
        command.setSourceBatchExpectations(buildSourceBatchExpectations(shipmentRows, batches, packingContextByBatchNo));
        return command;
    }

    private PluginSyncBatch buildBatch(JsonNode shipmentRow, ViewContext viewContext, List<JsonNode> packingRows) {
        String batchNo = normalizeShipmentNumber(shipmentRow);
        if (!StringUtils.hasText(batchNo)) {
            return null;
        }
        List<PluginSyncPackage> packages = new ArrayList<>();
        for (JsonNode boxRow : viewContext.boxRows) {
            PluginSyncPackage itemPackage = buildPackageFromBoxRow(boxRow);
            if (itemPackage != null) {
                mergePackage(packages, itemPackage);
            }
        }
        for (JsonNode row : packingRows) {
            String boxNo = firstText(firstLine(readValue(row, "number")), pickText(row, "id"));
            PluginSyncLine line = buildLine(row);
            if (!StringUtils.hasText(boxNo) || line == null) {
                continue;
            }
            PluginSyncPackage target = mergePackage(packages, packageShell(boxNo));
            mergeLine(target, line);
        }

        String sourceStatus = defaultText(firstText(viewContext.status, pickText(shipmentRow, "status")), "");
        PluginSyncBatch batch = new PluginSyncBatch();
        batch.setBatchNo(batchNo);
        batch.setBatchStatus(sourceStatus);
        batch.setContainerNo("");
        batch.setDestination(firstText(viewContext.destination, pickText(shipmentRow, "to_address_country", "country")));
        batch.setEstimatedArrivalAt(viewContext.schedule.estimatedArrivalAt);
        batch.setEstimatedDepartureAt(viewContext.schedule.estimatedDepartureAt);
        batch.setNodes(viewContext.nodes);
        batch.setOfficialEtaDate(parseLocalDate(viewContext.schedule.officialEtaDate));
        batch.setPackages(packages);
        batch.setRawStatus(StringUtils.hasText(sourceStatus) ? sourceStatus : null);
        batch.setSourceCreatedAt(normalizeSourceCreatedAt(readValue(shipmentRow, "created")));
        batch.setSourceStatus(StringUtils.hasText(sourceStatus) ? sourceStatus : null);
        batch.setTrackingNo("");
        batch.setTransportMode(firstText(viewContext.transportMode, normalizeTransportMode(pickText(shipmentRow, "service"))));
        return batch;
    }

    private ViewContext buildViewContext(JsonNode root) {
        ViewContext context = new ViewContext();
        context.boxRows = extractViewBoxRows(root);
        context.destination = findTitleValue(root, "发往国家");
        context.nodes = normalizeNodes(root);
        context.schedule = parseShipmentSchedule(context.nodes);
        context.status = findTitleValue(root, "状态");
        context.transportMode = normalizeTransportMode(findTitleValue(root, "服务类型"));
        return context;
    }

    private PackingContext buildPackingContext(JsonNode root) {
        PackingContext context = new PackingContext();
        context.rows = extractPackingListRows(root);
        context.totalQuantity = findLabelNumber(root, "产品总数");
        return context;
    }

    private List<JsonNode> extractShipmentRows(JsonNode root, int limit) {
        List<JsonNode> rows = new ArrayList<>();
        for (JsonNode row : collectDataSourceRows(root)) {
            if (StringUtils.hasText(pickText(row, "shipment_number", "shipmentNumber", "shipment_id", "shipmentId"))) {
                rows.add(row);
            }
        }
        return rows.subList(0, Math.min(clampLimit(limit), rows.size()));
    }

    private List<JsonNode> extractViewBoxRows(JsonNode root) {
        List<JsonNode> rows = new ArrayList<>();
        for (JsonNode row : collectDataSourceRows(root)) {
            if (StringUtils.hasText(pickText(row, "number"))
                    && (StringUtils.hasText(pickText(row, "size")) || StringUtils.hasText(pickText(row, "volume")))
                    && !StringUtils.hasText(pickText(row, "sku"))) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<JsonNode> extractPackingListRows(JsonNode root) {
        List<JsonNode> rows = new ArrayList<>();
        for (JsonNode row : collectDataSourceRows(root)) {
            if (StringUtils.hasText(pickText(row, "number"))
                    && StringUtils.hasText(pickText(row, "sku"))
                    && pickInteger(row, "qty", "quantity", "数量") != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<JsonNode> collectDataSourceRows(JsonNode source) {
        List<JsonNode> rows = new ArrayList<>();
        collectDataSourceRows(source, rows);
        return rows;
    }

    private void collectDataSourceRows(JsonNode source, List<JsonNode> rows) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return;
        }
        if (source.isArray()) {
            source.forEach(item -> collectDataSourceRows(item, rows));
            return;
        }
        if (!source.isObject()) {
            return;
        }
        JsonNode dataSource = readValue(source, "dataSource");
        if (dataSource.isArray()) {
            dataSource.forEach(row -> {
                if (row.isObject()) {
                    rows.add(row);
                }
            });
        }
        source.elements().forEachRemaining(item -> collectDataSourceRows(item, rows));
    }

    private List<JsonNode> collectTitleValueItems(JsonNode source) {
        List<JsonNode> rows = new ArrayList<>();
        collectTitleValueItems(source, rows);
        return rows;
    }

    private void collectTitleValueItems(JsonNode source, List<JsonNode> rows) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return;
        }
        if (source.isArray()) {
            source.forEach(item -> collectTitleValueItems(item, rows));
            return;
        }
        if (!source.isObject()) {
            return;
        }
        if (!readValue(source, "title").isMissingNode() && !readValue(source, "value").isMissingNode()) {
            rows.add(source);
        }
        source.elements().forEachRemaining(item -> collectTitleValueItems(item, rows));
    }

    private List<JsonNode> collectLabelValueItems(JsonNode source) {
        List<JsonNode> rows = new ArrayList<>();
        collectLabelValueItems(source, rows);
        return rows;
    }

    private void collectLabelValueItems(JsonNode source, List<JsonNode> rows) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return;
        }
        if (source.isArray()) {
            source.forEach(item -> collectLabelValueItems(item, rows));
            return;
        }
        if (!source.isObject()) {
            return;
        }
        if (!readValue(source, "label").isMissingNode() && !readValue(source, "value").isMissingNode()) {
            rows.add(source);
        }
        source.elements().forEachRemaining(item -> collectLabelValueItems(item, rows));
    }

    private String findTitleValue(JsonNode source, String title) {
        for (JsonNode row : collectTitleValueItems(source)) {
            if (Objects.equals(stripHtml(readValue(row, "title")), title)) {
                return stripHtml(readValue(row, "value"));
            }
        }
        return null;
    }

    private Integer findLabelNumber(JsonNode source, String label) {
        for (JsonNode row : collectLabelValueItems(source)) {
            if (Objects.equals(stripHtml(readValue(row, "label")), label)) {
                return pickInteger(row, "value");
            }
        }
        return null;
    }

    private List<PluginSyncNode> normalizeNodes(JsonNode root) {
        List<PluginSyncNode> nodes = new ArrayList<>();
        for (JsonNode row : collectDataSourceRows(root)) {
            String title = pickText(row, "title");
            String info = pickText(row, "info");
            if (!StringUtils.hasText(title) || !StringUtils.hasText(info)) {
                continue;
            }
            PluginSyncNode node = new PluginSyncNode();
            node.setDescription(info);
            node.setNodeStatus(normalizeNodeStatus(row));
            node.setNodeTime(firstText(normalizeDateTime(title), title));
            nodes.add(node);
        }
        return nodes;
    }

    private String normalizeNodeStatus(JsonNode row) {
        String event = pickText(row, "event");
        String info = defaultText(pickText(row, "info"), "");
        if ("shipment.create".equals(event)) {
            return "created";
        }
        if ("pickup".equals(event)) {
            return "handed_to_forwarder";
        }
        if ("shiped".equals(event)) {
            return "departed_origin";
        }
        if (info.matches("(?s).*(已提回海外仓|待拆柜派送|签收|入仓|已收货).*")) {
            return "warehouse_received";
        }
        if (info.matches("(?s).*(异常|问题).*")) {
            return "exception";
        }
        return "in_transit";
    }

    private Schedule parseShipmentSchedule(List<PluginSyncNode> nodes) {
        for (PluginSyncNode node : nodes) {
            Matcher matcher = Pattern.compile("ETD:\\s*(\\d{1,2})[-/](\\d{1,2})\\s+ETA:\\s*(\\d{1,2})[-/](\\d{1,2})", Pattern.CASE_INSENSITIVE)
                    .matcher(defaultText(node.getDescription(), ""));
            if (!matcher.find()) {
                continue;
            }
            LocalDate nodeDate = parseLocalDate(normalizeDateTime(node.getNodeTime()) == null
                    ? null
                    : normalizeDateTime(node.getNodeTime()).substring(0, 10));
            if (nodeDate == null) {
                continue;
            }
            int departureMonth = Integer.parseInt(matcher.group(1));
            int departureDay = Integer.parseInt(matcher.group(2));
            int arrivalMonth = Integer.parseInt(matcher.group(3));
            int arrivalDay = Integer.parseInt(matcher.group(4));

            ScheduleCandidate best = null;
            for (int candidateYear = nodeDate.getYear() - 1; candidateYear <= nodeDate.getYear() + 1; candidateYear += 1) {
                int arrivalYear = arrivalMonth < departureMonth
                        || (arrivalMonth == departureMonth && arrivalDay < departureDay)
                        ? candidateYear + 1
                        : candidateYear;
                LocalDate departureDate = LocalDate.of(candidateYear, departureMonth, departureDay);
                LocalDate arrivalDate = LocalDate.of(arrivalYear, arrivalMonth, arrivalDay);
                long score = scoreScheduleCandidate(nodeDate, departureDate, arrivalDate);
                ScheduleCandidate candidate = new ScheduleCandidate(departureDate, arrivalDate, score);
                if (best == null || candidate.score < best.score
                        || (candidate.score == best.score && candidate.departureDate.isBefore(best.departureDate))) {
                    best = candidate;
                }
            }
            if (best != null) {
                Schedule schedule = new Schedule();
                schedule.estimatedDepartureAt = best.departureDate + " 00:00:00";
                schedule.estimatedArrivalAt = best.arrivalDate + " 00:00:00";
                schedule.officialEtaDate = best.arrivalDate.toString();
                return schedule;
            }
        }
        return new Schedule();
    }

    private long scoreScheduleCandidate(LocalDate nodeDate, LocalDate departureDate, LocalDate arrivalDate) {
        if (nodeDate.isBefore(departureDate)) {
            return ChronoUnit.DAYS.between(nodeDate, departureDate);
        }
        if (nodeDate.isAfter(arrivalDate)) {
            return ChronoUnit.DAYS.between(arrivalDate, nodeDate);
        }
        return 0L;
    }

    private PluginSyncPackage buildPackageFromBoxRow(JsonNode row) {
        String boxNo = firstLine(readValue(row, "number"));
        if (!StringUtils.hasText(boxNo)) {
            return null;
        }
        PluginSyncPackage itemPackage = new PluginSyncPackage();
        itemPackage.setBoxNo(boxNo);
        itemPackage.setExternalBoxNo(pickText(row, "id"));
        itemPackage.setPackageStatus(pickText(row, "status"));
        itemPackage.setTrackingNo("");
        applyBoxSpecs(itemPackage, row);
        return itemPackage;
    }

    private PluginSyncPackage packageShell(String boxNo) {
        PluginSyncPackage itemPackage = new PluginSyncPackage();
        itemPackage.setBoxNo(boxNo);
        itemPackage.setTrackingNo("");
        return itemPackage;
    }

    private void applyBoxSpecs(PluginSyncPackage itemPackage, JsonNode row) {
        String text = firstText(stripHtml(readValue(row, "volume")), stripHtml(readValue(row, "size")));
        if (!StringUtils.hasText(text)) {
            return;
        }
        String firstLine = text.split("\\n")[0];
        Matcher weightMatcher = Pattern.compile("([\\d.]+)").matcher(firstLine);
        if (weightMatcher.find()) {
            itemPackage.setWeightKg(decimal(weightMatcher.group(1)));
        }
        Matcher dimensionMatcher = Pattern.compile("([\\d.]+)\\s*[x×*]\\s*([\\d.]+)\\s*[x×*]\\s*([\\d.]+)\\s*\\(?\\s*cm\\s*\\)?", Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (dimensionMatcher.find()) {
            BigDecimal length = decimal(dimensionMatcher.group(1));
            BigDecimal width = decimal(dimensionMatcher.group(2));
            BigDecimal height = decimal(dimensionMatcher.group(3));
            itemPackage.setLengthCm(length);
            itemPackage.setWidthCm(width);
            itemPackage.setHeightCm(height);
            if (length != null && width != null && height != null) {
                itemPackage.setVolumeCbm(length.multiply(width).multiply(height)
                        .divide(new BigDecimal("1000000"), 6, RoundingMode.HALF_UP));
            }
        }
    }

    private PluginSyncPackage mergePackage(List<PluginSyncPackage> packages, PluginSyncPackage nextPackage) {
        PluginSyncPackage target = packages.stream()
                .filter(candidate -> Objects.equals(candidate.getBoxNo(), nextPackage.getBoxNo()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            packages.add(nextPackage);
            return nextPackage;
        }
        if (!StringUtils.hasText(target.getExternalBoxNo())) target.setExternalBoxNo(nextPackage.getExternalBoxNo());
        if (!StringUtils.hasText(target.getPackageStatus())) target.setPackageStatus(nextPackage.getPackageStatus());
        if (target.getLengthCm() == null) target.setLengthCm(nextPackage.getLengthCm());
        if (target.getWidthCm() == null) target.setWidthCm(nextPackage.getWidthCm());
        if (target.getHeightCm() == null) target.setHeightCm(nextPackage.getHeightCm());
        if (target.getWeightKg() == null) target.setWeightKg(nextPackage.getWeightKg());
        if (target.getVolumeCbm() == null) target.setVolumeCbm(nextPackage.getVolumeCbm());
        return target;
    }

    private PluginSyncLine buildLine(JsonNode row) {
        String psku = pickText(row, "sku", "psku", "商品SKU");
        Integer shippedQuantity = pickInteger(row, "qty", "quantity", "数量");
        if (!StringUtils.hasText(psku) || shippedQuantity == null) {
            return null;
        }
        PluginSyncLine line = new PluginSyncLine();
        line.setPsku(psku);
        line.setSku(psku);
        line.setMsku("");
        line.setProductName(defaultText(pickText(row, "name_zh", "nameCn", "中文品名", "name_en"), ""));
        line.setReceivedQuantity(0);
        line.setShippedQuantity(shippedQuantity);
        line.setSiteCode("");
        line.setStoreCode("");
        return line;
    }

    private void mergeLine(PluginSyncPackage itemPackage, PluginSyncLine nextLine) {
        List<PluginSyncLine> lines = new ArrayList<>(itemPackage.getLines());
        for (PluginSyncLine existing : lines) {
            if (Objects.equals(existing.getPsku(), nextLine.getPsku())) {
                existing.setShippedQuantity(defaultInteger(existing.getShippedQuantity(), 0) + defaultInteger(nextLine.getShippedQuantity(), 0));
                if (!StringUtils.hasText(existing.getProductName())) {
                    existing.setProductName(nextLine.getProductName());
                }
                itemPackage.setLines(lines);
                return;
            }
        }
        lines.add(nextLine);
        itemPackage.setLines(lines);
    }

    private List<PluginSyncSourceBatchExpectation> buildSourceBatchExpectations(
            List<JsonNode> shipmentRows,
            List<PluginSyncBatch> batches,
            Map<String, PackingContext> packingContextByBatchNo
    ) {
        List<PluginSyncSourceBatchExpectation> expectations = new ArrayList<>();
        for (PluginSyncBatch batch : batches) {
            JsonNode sourceRow = shipmentRows.stream()
                    .filter(row -> Objects.equals(normalizeShipmentNumber(row), batch.getBatchNo()))
                    .findFirst()
                    .orElse(MissingNode.getInstance());
            PackingContext packingContext = packingContextByBatchNo.get(batch.getBatchNo());
            PluginSyncSourceBatchExpectation expectation = new PluginSyncSourceBatchExpectation();
            expectation.setBatchNo(batch.getBatchNo());
            expectation.setBoxNum(firstInteger(pickInteger(sourceRow, "parcel_count"), batch.getPackages().size()));
            expectation.setTotalQuantity(packingContext != null && packingContext.totalQuantity != null
                    ? packingContext.totalQuantity
                    : sumLineQuantity(batch));
            expectations.add(expectation);
        }
        return expectations;
    }

    private Integer sumLineQuantity(PluginSyncBatch batch) {
        int total = 0;
        for (PluginSyncPackage itemPackage : batch.getPackages()) {
            for (PluginSyncLine line : itemPackage.getLines()) {
                total += defaultInteger(line.getShippedQuantity(), 0);
            }
        }
        return total;
    }

    private String findJsonForShipment(JsonNode row, Map<String, String> jsonByShipmentRecordId) {
        if (jsonByShipmentRecordId == null || jsonByShipmentRecordId.isEmpty()) {
            return null;
        }
        for (String key : new String[]{pickText(row, "id"), normalizeShipmentNumber(row), pickText(row, "shipment_id", "shipmentId")}) {
            if (StringUtils.hasText(key) && jsonByShipmentRecordId.containsKey(key)) {
                return jsonByShipmentRecordId.get(key);
            }
        }
        return null;
    }

    private String normalizeShipmentNumber(JsonNode row) {
        String text = pickText(row, "shipment_number", "shipmentNumber", "shipment_id", "shipmentId");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = SHIPMENT_NO_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : text;
    }

    private String normalizeSourceCreatedAt(JsonNode source) {
        Integer seconds = integer(source);
        if (seconds != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), SHANGHAI_ZONE).format(NODE_TIME_FORMATTER);
        }
        return normalizeDateTime(stripHtml(source));
    }

    private String normalizeTransportMode(String value) {
        if (!StringUtils.hasText(value)) return null;
        if (value.matches("(?i).*(海运|SEA|船).*")) return "SEA";
        if (value.matches("(?i).*(空运|AIR|航空|航班).*")) return "AIR";
        return null;
    }

    private String normalizeDateTime(String value) {
        if (!StringUtils.hasText(value)) return null;
        Matcher matcher = Pattern.compile("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?").matcher(value.trim());
        if (!matcher.find()) return null;
        String normalized = matcher.group().replace('/', '-');
        try {
            return LocalDateTime.parse(normalized, FLEXIBLE_DATE_TIME_FORMATTER).format(NODE_TIME_FORMATTER);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private LocalDate parseLocalDate(String value) {
        return StringUtils.hasText(value) ? LocalDate.parse(value) : null;
    }

    private JsonNode parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("义特 JSON 解析失败。", exception);
        }
    }

    private JsonNode readValue(JsonNode source, String key) {
        if (source == null || !source.isObject()) {
            return MissingNode.getInstance();
        }
        if (source.has(key)) {
            return source.get(key);
        }
        String normalized = normalizeRecordKey(key);
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (normalizeRecordKey(entry.getKey()).equals(normalized)) {
                return entry.getValue();
            }
        }
        return MissingNode.getInstance();
    }

    private String pickText(JsonNode source, String... keys) {
        for (String key : keys) {
            String value = stripHtml(readValue(source, key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer pickInteger(JsonNode source, String... keys) {
        for (String key : keys) {
            Integer value = integer(readValue(source, key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer integer(JsonNode node) {
        BigDecimal value = decimal(node);
        return value == null ? null : value.intValue();
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            return decimal(node.asText());
        }
        return null;
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stripHtml(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return stripHtml(node.asText());
        }
        if (node.isIntegralNumber()) {
            return String.valueOf(node.asLong());
        }
        if (node.isNumber()) {
            return node.decimalValue().stripTrailingZeros().toPlainString();
        }
        return null;
    }

    private String stripHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("(?i)&nbsp;", " ")
                .replaceAll("(?i)&amp;", "&")
                .replaceAll("\\s+\\n", "\n")
                .replaceAll("\\n\\s+", "\n")
                .trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String firstLine(JsonNode node) {
        String text = stripHtml(node);
        return firstLine(text);
    }

    private String firstLine(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.split("\\n")[0].trim();
    }

    private String normalizeRecordKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer firstInteger(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SHIPMENT_LIMIT;
        }
        return Math.max(1, Math.min(DEFAULT_SHIPMENT_LIMIT, limit));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private LogisticsAutoSyncProperties.Yite yiteProperties() {
        return properties.getYite() == null ? new LogisticsAutoSyncProperties.Yite() : properties.getYite();
    }

    private URI uri(String baseUrl, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return URI.create(path);
        }
        return URI.create(defaultText(baseUrl, "https://ywyite.nextsls.com").replaceAll("/+$", "")
                + "/" + path.replaceAll("^/+", ""));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("义特自动同步请求序列化失败。", exception);
        }
    }

    private String toForm(Map<String, String> fields) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(defaultText(entry.getKey(), ""), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(defaultText(entry.getValue(), ""), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private int timeoutSeconds(int value) {
        return Math.max(5, Math.min(120, value));
    }

    private String sessionCookie(FetchSession session, String... names) {
        for (HttpCookie cookie : session.cookieManager.getCookieStore().getCookies()) {
            for (String name : names) {
                if (Objects.equals(cookie.getName(), name) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private boolean looksLikeHtml(String body) {
        return StringUtils.hasText(body) && body.trim().matches("(?is)^\\s*<!doctype.*|^\\s*<html\\b.*");
    }

    private int countPackages(PluginSyncCommand command) {
        if (command == null || command.getBatches() == null) return 0;
        return command.getBatches().stream().mapToInt(batch -> batch.getPackages() == null ? 0 : batch.getPackages().size()).sum();
    }

    private int countLines(PluginSyncCommand command) {
        if (command == null || command.getBatches() == null) return 0;
        return command.getBatches().stream()
                .flatMap(batch -> batch.getPackages() == null ? java.util.stream.Stream.<PluginSyncPackage>empty() : batch.getPackages().stream())
                .mapToInt(itemPackage -> itemPackage.getLines() == null ? 0 : itemPackage.getLines().size())
                .sum();
    }

    private int countNodes(PluginSyncCommand command) {
        if (command == null || command.getBatches() == null) return 0;
        return command.getBatches().stream().mapToInt(batch -> batch.getNodes() == null ? 0 : batch.getNodes().size()).sum();
    }

    private static final class ViewContext {
        private String destination;
        private List<JsonNode> boxRows = Collections.emptyList();
        private List<PluginSyncNode> nodes = Collections.emptyList();
        private Schedule schedule = new Schedule();
        private String status;
        private String transportMode;
    }

    private static final class PackingContext {
        private List<JsonNode> rows = Collections.emptyList();
        private Integer totalQuantity;
    }

    private static final class Schedule {
        private String estimatedArrivalAt;
        private String estimatedDepartureAt;
        private String officialEtaDate;
    }

    private static final class ScheduleCandidate {
        private final LocalDate departureDate;
        private final LocalDate arrivalDate;
        private final long score;

        private ScheduleCandidate(LocalDate departureDate, LocalDate arrivalDate, long score) {
            this.departureDate = departureDate;
            this.arrivalDate = arrivalDate;
            this.score = score;
        }
    }

    private static final class FetchSession {
        private final String baseUrl;
        private final HttpClient httpClient;
        private final CookieManager cookieManager;
        private final String token;

        private FetchSession(String baseUrl, HttpClient httpClient, CookieManager cookieManager) {
            this(baseUrl, httpClient, cookieManager, null);
        }

        private FetchSession(String baseUrl, HttpClient httpClient, CookieManager cookieManager, String token) {
            this.baseUrl = baseUrl;
            this.httpClient = httpClient;
            this.cookieManager = cookieManager;
            this.token = token;
        }
    }

    private static final class ProviderAuthException extends RuntimeException {
        private final String failureCode;

        private ProviderAuthException(String failureCode, String message) {
            super(message);
            this.failureCode = failureCode;
        }
    }
}
