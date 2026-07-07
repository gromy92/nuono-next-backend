package com.nuono.next.intransit.autosync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncBatch;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncLine;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncNode;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncPackage;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncSourceBatchExpectation;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EtLogisticsProviderAdapter implements LogisticsProviderAdapter {
    static final int DEFAULT_SHIP_ORDER_LIMIT = 10;
    static final int DEFAULT_BOX_DETAIL_LIMIT = 1000;

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

    private final LogisticsAutoSyncProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EtLogisticsProviderAdapter(LogisticsAutoSyncProperties properties) {
        this.properties = properties == null ? new LogisticsAutoSyncProperties() : properties;
    }

    @Override
    public String sourceSystem() {
        return "ET";
    }

    @Override
    public LogisticsProviderFetchResult fetch(LogisticsProviderFetchRequest request) {
        LogisticsAutoSyncProperties.Et et = properties.getEt() == null ? new LogisticsAutoSyncProperties.Et() : properties.getEt();
        if (!et.isEnabled()) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.CONFIGURATION_ERROR,
                    "ET 自动同步 HTTP 拉取未启用。"
            );
        }
        if (!StringUtils.hasText(et.getBaseUrl()) || !StringUtils.hasText(et.getLoginPath())) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.CONFIGURATION_ERROR,
                    "ET 自动同步缺少 baseUrl 或 loginPath 配置。"
            );
        }
        if (request == null || !StringUtils.hasText(request.getLoginAccount()) || !StringUtils.hasText(request.getPassword())) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.INVALID_CREDENTIAL,
                    "ET 自动同步账号或密码为空。"
            );
        }

        try {
            FetchSession session = login(et, request);
            int limit = Math.max(1, Math.min(DEFAULT_SHIP_ORDER_LIMIT, request.getRecentLimit() <= 0 ? DEFAULT_SHIP_ORDER_LIMIT : request.getRecentLimit()));
            String listBody = get(session, buildShipOrderListPath(limit, "0"));
            List<JsonNode> listRows = extractRows(parseJson(listBody), limit);
            Map<String, String> boxDetailJsonByShipOrderId = new LinkedHashMap<>();
            Map<String, String> boxModifyJsonByBoxId = new LinkedHashMap<>();
            Map<String, String> boxListDetailJsonByBoxId = new LinkedHashMap<>();
            Set<String> boxIds = new LinkedHashSet<>();
            for (JsonNode row : listRows) {
                String shipOrderId = pickBatchNo(row);
                if (!StringUtils.hasText(shipOrderId)) {
                    continue;
                }
                String detailBody = get(session, buildShipOrderBoxDetailPath(shipOrderId, 1, DEFAULT_BOX_DETAIL_LIMIT, "0"));
                boxDetailJsonByShipOrderId.put(shipOrderId, detailBody);
                for (JsonNode detailRow : extractRows(parseJson(detailBody), Integer.MAX_VALUE)) {
                    String boxId = pickExternalBoxNo(detailRow);
                    if (StringUtils.hasText(boxId)) {
                        boxIds.add(boxId);
                    }
                }
            }
            for (String boxId : boxIds) {
                boxModifyJsonByBoxId.put(boxId, postForm(session, buildBoxModifyPath(), Collections.singletonMap("boxId", boxId)));
                boxListDetailJsonByBoxId.put(boxId, get(session, buildBoxListDetailPath(boxId, 1, DEFAULT_BOX_DETAIL_LIMIT, "0")));
            }
            PluginSyncCommand command = normalize(listBody, boxDetailJsonByShipOrderId, boxModifyJsonByBoxId, boxListDetailJsonByBoxId);
            LogisticsProviderFetchResult result = LogisticsProviderFetchResult.success(command);
            result.setPackageCount(countPackages(command));
            result.setLineCount(countLines(command));
            result.setNodeCount(countNodes(command));
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LogisticsProviderFetchResult.failure(LogisticsProviderFailureCode.NETWORK_ERROR, "ET 自动同步请求被中断。");
        } catch (IOException exception) {
            return LogisticsProviderFetchResult.failure(LogisticsProviderFailureCode.NETWORK_ERROR, "ET 自动同步网络请求失败。");
        } catch (ProviderAuthException exception) {
            return LogisticsProviderFetchResult.failure(exception.failureCode, exception.getMessage());
        } catch (RuntimeException exception) {
            return LogisticsProviderFetchResult.failure(LogisticsProviderFailureCode.PROVIDER_ERROR, "ET 自动同步返回数据解析失败。");
        }
    }

    private FetchSession login(LogisticsAutoSyncProperties.Et et, LogisticsProviderFetchRequest request)
            throws IOException, InterruptedException {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds(et.getTimeoutSeconds())))
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        FetchSession session = new FetchSession(et.getBaseUrl(), httpClient);
        Map<String, String> loginPayload = new LinkedHashMap<>();
        loginPayload.put(defaultText(et.getLoginAccountField(), "username"), request.getLoginAccount());
        loginPayload.put(defaultText(et.getLoginPasswordField(), "password"), request.getPassword());
        HttpRequest.Builder builder = requestBuilder(session, et.getLoginPath())
                .header("accept", "application/json, text/javascript, */*; q=0.01");
        String payloadType = defaultText(et.getLoginPayloadType(), "form").trim().toLowerCase(Locale.ROOT);
        HttpRequest requestMessage;
        if ("json".equals(payloadType)) {
            requestMessage = builder
                    .header("content-type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(loginPayload)))
                    .build();
        } else {
            requestMessage = builder
                    .header("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(toForm(loginPayload)))
                    .build();
        }
        requireJsonBody(send(session, requestMessage), "ET 登录接口返回异常。");
        return session;
    }

    private String get(FetchSession session, String path) throws IOException, InterruptedException {
        return requireJsonBody(send(session, requestBuilder(session, path)
                .header("accept", "application/json, text/javascript, */*; q=0.01")
                .header("x-requested-with", "XMLHttpRequest")
                .GET()
                .build()), "ET 接口返回异常。");
    }

    private String postForm(FetchSession session, String path, Map<String, String> fields) throws IOException, InterruptedException {
        return requireJsonBody(send(session, requestBuilder(session, path)
                .header("accept", "*/*")
                .header("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("x-requested-with", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(toForm(fields)))
                .build()), "ET 接口返回异常。");
    }

    private HttpResponse<String> send(FetchSession session, HttpRequest request) throws IOException, InterruptedException {
        return session.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder requestBuilder(FetchSession session, String path) {
        return HttpRequest.newBuilder(uri(session.baseUrl, path))
                .timeout(Duration.ofSeconds(timeoutSeconds((properties.getEt() == null ? new LogisticsAutoSyncProperties.Et() : properties.getEt()).getTimeoutSeconds())));
    }

    private String requireJsonBody(HttpResponse<String> response, String errorMessage) {
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ProviderAuthException(LogisticsProviderFailureCode.INVALID_CREDENTIAL, "ET 登录失败或账号无权限。");
        }
        if (response.statusCode() >= 400) {
            throw new ProviderAuthException(LogisticsProviderFailureCode.PROVIDER_ERROR, errorMessage);
        }
        String body = response.body() == null ? "" : response.body();
        if (looksLikeCaptchaChallenge(body)) {
            throw new ProviderAuthException(LogisticsProviderFailureCode.CAPTCHA_REQUIRED, "ET 接口提示需要验证码或风控验证。");
        }
        if (looksLikeHtml(body)) {
            throw new ProviderAuthException(LogisticsProviderFailureCode.CAPTCHA_REQUIRED, "ET 接口返回登录页面，可能需要验证码或风控验证。");
        }
        rejectExplicitFailure(body, errorMessage);
        return body;
    }

    private void rejectExplicitFailure(String body, String errorMessage) {
        JsonNode root = parseJson(body);
        JsonNode success = readValue(root, "success");
        if (!isExplicitFailure(success)) {
            return;
        }
        String message = firstText(
                pickText(root, "info", "message", "msg", "error"),
                pickText(readValue(root, "data"), "info", "message", "msg", "error"),
                "ET 接口返回失败。"
        );
        boolean loginContext = StringUtils.hasText(errorMessage) && errorMessage.contains("登录");
        String prefix = loginContext ? "ET 登录失败：" : "ET 接口返回业务错误：";
        throw new ProviderAuthException(businessFailureCode(message, loginContext), prefix + message);
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

    private String businessFailureCode(String message, boolean loginContext) {
        String normalized = StringUtils.hasText(message) ? message.toLowerCase(Locale.ROOT) : "";
        if (normalized.contains("captcha") || normalized.contains("验证码") || normalized.contains("风控")) {
            return LogisticsProviderFailureCode.CAPTCHA_REQUIRED;
        }
        if (loginContext
                || normalized.contains("账号")
                || normalized.contains("密码")
                || normalized.contains("登录")
                || normalized.contains("权限")
                || normalized.contains("unauthorized")
                || normalized.contains("forbidden")) {
            return LogisticsProviderFailureCode.INVALID_CREDENTIAL;
        }
        return LogisticsProviderFailureCode.PROVIDER_ERROR;
    }

    public String buildShipOrderListPath(int limit, String cacheBuster) {
        int rows = Math.max(1, Math.min(DEFAULT_SHIP_ORDER_LIMIT, limit <= 0 ? DEFAULT_SHIP_ORDER_LIMIT : limit));
        return "/Delivery/ShipOrder/GetGridJson?t=" + defaultText(cacheBuster, "0")
                + "&page=1&limit=" + rows
                + "&storeroomId=&transportId=&status=&shipOrderId=&startTime=&endTime=&sType=&keyWords=&barcode=&skuCode=&doSort=&boxId=&overDifference=-1&cod=-1";
    }

    public String buildShipOrderBoxDetailPath(String shipOrderId, int page, int limit, String cacheBuster) {
        return "/Delivery/ShipOrder/GetBoxDetailForm?t=" + defaultText(cacheBuster, "0")
                + "&page=" + Math.max(1, page)
                + "&limit=" + Math.max(1, limit)
                + "&shipOrderId=" + defaultText(shipOrderId, "");
    }

    public String buildBoxModifyPath() {
        return "/Delivery/BoxList/GetModifyBox";
    }

    public String buildBoxListDetailPath(String boxId, int page, int limit, String cacheBuster) {
        return "/Delivery/BoxList/GetDetailsGridJson?t=" + defaultText(cacheBuster, "0")
                + "&page=" + Math.max(1, page)
                + "&limit=" + Math.max(1, limit)
                + "&boxId=" + defaultText(boxId, "");
    }

    public PluginSyncCommand normalize(
            String shipOrderListJson,
            Map<String, String> boxDetailJsonByShipOrderId,
            Map<String, String> boxModifyJsonByBoxId,
            Map<String, String> boxListDetailJsonByBoxId
    ) {
        List<JsonNode> listRows = extractRows(parseJson(shipOrderListJson), DEFAULT_SHIP_ORDER_LIMIT);
        List<JsonNode> boxDetailRows = new ArrayList<>();
        if (boxDetailJsonByShipOrderId != null) {
            for (String json : boxDetailJsonByShipOrderId.values()) {
                boxDetailRows.addAll(extractRows(parseJson(json), Integer.MAX_VALUE));
            }
        }
        List<JsonNode> boxModifyRows = new ArrayList<>();
        if (boxModifyJsonByBoxId != null) {
            for (String json : boxModifyJsonByBoxId.values()) {
                boxModifyRows.addAll(extractBoxModifyRows(parseJson(json)));
            }
        }
        List<JsonNode> boxListRows = new ArrayList<>();
        if (boxListDetailJsonByBoxId != null) {
            for (String json : boxListDetailJsonByBoxId.values()) {
                boxListRows.addAll(extractRows(parseJson(json), Integer.MAX_VALUE));
            }
        }

        List<JsonNode> combinedRows = new ArrayList<>();
        Set<String> boxesWithLineDetails = new HashSet<>();
        for (JsonNode row : boxListRows) {
            String boxId = pickExternalBoxNo(row);
            if (StringUtils.hasText(boxId)) {
                boxesWithLineDetails.add(boxId);
            }
        }
        combinedRows.addAll(stripListLineRowsWhenBoxListPresent(listRows, boxesWithLineDetails));
        Map<String, JsonNode> contextByBox = buildBoxContextByExternalBox(boxDetailRows, boxModifyRows);
        for (JsonNode row : boxDetailRows) {
            String boxId = pickExternalBoxNo(row);
            if (!StringUtils.hasText(boxId) || !boxesWithLineDetails.contains(boxId)) {
                combinedRows.add(sanitizeBoxDetailRow(row, false));
            }
        }
        for (JsonNode row : boxListRows) {
            String boxId = pickExternalBoxNo(row);
            combinedRows.add(sanitizeBoxListRow(row, StringUtils.hasText(boxId) ? contextByBox.get(boxId) : MissingNode.getInstance()));
        }

        PluginSyncCommand command = new PluginSyncCommand();
        command.setSourceSystem("ET");
        command.setForwarderName("易通");
        command.setSourceBatchExpectations(buildSourceBatchExpectations(listRows));
        command.setBatches(mergeBatches(buildBatches(combinedRows)));
        return command;
    }

    private List<JsonNode> stripListLineRowsWhenBoxListPresent(List<JsonNode> listRows, Set<String> boxesWithLineDetails) {
        if (boxesWithLineDetails.isEmpty()) {
            return listRows;
        }
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode row : listRows) {
            String boxId = pickExternalBoxNo(row);
            if (!StringUtils.hasText(boxId) || !boxesWithLineDetails.contains(boxId)) {
                result.add(row);
                continue;
            }
            ObjectNode copy = objectMapper.createObjectNode();
            row.fields().forEachRemaining(entry -> {
                if (!Set.of("barcode", "boxid", "boxno", "clientboxid", "goodsname", "goodstitle", "modelnumber", "msku", "quantity", "qty", "realquantity", "skucode", "sku", "title", "titlecn", "titleen").contains(normalizeRecordKey(entry.getKey()))) {
                    copy.set(entry.getKey(), entry.getValue());
                }
            });
            result.add(copy);
        }
        return result;
    }

    private Map<String, JsonNode> buildBoxContextByExternalBox(List<JsonNode> boxDetailRows, List<JsonNode> boxModifyRows) {
        Map<String, ObjectNode> contexts = new LinkedHashMap<>();
        for (JsonNode row : boxDetailRows) {
            mergeContext(contexts, sanitizeBoxDetailRow(row, false));
        }
        for (JsonNode row : boxModifyRows) {
            mergeContext(contexts, sanitizeBoxDetailRow(row, false));
        }
        return new LinkedHashMap<>(contexts);
    }

    private void mergeContext(Map<String, ObjectNode> contexts, JsonNode row) {
        String boxId = pickExternalBoxNo(row);
        if (!StringUtils.hasText(boxId)) {
            return;
        }
        ObjectNode target = contexts.computeIfAbsent(boxId, ignored -> objectMapper.createObjectNode());
        row.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isNull() && !entry.getValue().isMissingNode() && StringUtils.hasText(text(entry.getValue()))) {
                target.set(entry.getKey(), entry.getValue());
            }
        });
    }

    private JsonNode sanitizeBoxDetailRow(JsonNode row, boolean includeMeasurements) {
        ObjectNode sanitized = objectMapper.createObjectNode();
        copy(row, sanitized, "Barcode", "BoxId", "CaseQuantity", "ClientBoxId", "CountryId", "ETBarcode",
                "GoodsTitle", "ModelNumber", "ShipOrderId", "SkuCode", "StatusName", "StoreroomName",
                "TargetStore", "TitleCn", "TitleEn", "TransportName");
        JsonNode boxId = readValue(row, "BoxId");
        if (!boxId.isMissingNode()) {
            sanitized.set("externalBoxNo", boxId);
        }
        if (includeMeasurements) {
            copy(row, sanitized, "Height", "Length", "Volume", "Weight", "Width");
        }
        return sanitized;
    }

    private JsonNode sanitizeBoxListRow(JsonNode row, JsonNode context) {
        ObjectNode sanitized = objectMapper.createObjectNode();
        copy(row, sanitized, "Barcode", "BoxId", "CaseQuantity", "GoodsTitle", "ModelNumber", "RealQuantity", "SkuCode", "TitleCn", "TitleEn");
        setFirst(sanitized, "BoxId", readValue(row, "BoxId"), readValue(context, "BoxId"));
        setFirst(sanitized, "externalBoxNo", readValue(row, "BoxId"), readValue(context, "BoxId"));
        setFirst(sanitized, "CaseQuantity", readValue(row, "CaseQuantity"), readValue(row, "RealQuantity"), readValue(row, "quantity"));
        setFirst(sanitized, "ClientBoxId", readValue(context, "ClientBoxId"), readValue(row, "ClientBoxId"));
        setFirst(sanitized, "CountryId", readValue(context, "CountryId"), readValue(row, "CountryId"));
        setFirst(sanitized, "GoodsTitle", readValue(row, "GoodsTitle"), readValue(row, "goodsName"), readValue(row, "productName"), readValue(row, "TitleCn"), readValue(row, "TitleEn"));
        setFirst(sanitized, "ShipOrderId", readValue(context, "ShipOrderId"), readValue(row, "ShipOrderId"));
        setFirst(sanitized, "StatusName", readValue(context, "StatusName"), readValue(row, "StatusName"));
        setFirst(sanitized, "StoreroomName", readValue(context, "StoreroomName"), readValue(row, "StoreroomName"));
        setFirst(sanitized, "TransportName", readValue(context, "TransportName"), readValue(row, "TransportName"));
        return sanitized;
    }

    private void copy(JsonNode source, ObjectNode target, String... keys) {
        for (String key : keys) {
            JsonNode value = readValue(source, key);
            if (!value.isMissingNode() && !value.isNull()) {
                target.set(key, value);
            }
        }
    }

    private void setFirst(ObjectNode target, String key, JsonNode... values) {
        for (JsonNode value : values) {
            if (value != null && !value.isMissingNode() && !value.isNull() && StringUtils.hasText(text(value))) {
                target.set(key, value);
                return;
            }
        }
    }

    private List<PluginSyncBatch> buildBatches(List<JsonNode> rows) {
        List<PluginSyncBatch> batches = new ArrayList<>();
        for (int index = 0; index < rows.size(); index += 1) {
            PluginSyncBatch batch = buildBatch(rows.get(index), index);
            if (batch != null) {
                batches.add(batch);
            }
        }
        return batches;
    }

    private PluginSyncBatch buildBatch(JsonNode row, int index) {
        String batchNo = pickBatchNo(row);
        if (!StringUtils.hasText(batchNo)) {
            return null;
        }
        PluginSyncBatch batch = new PluginSyncBatch();
        batch.setBatchNo(batchNo);
        batch.setBatchStatus(normalizeBatchStatus(row));
        batch.setContainerNo(defaultText(pickText(row, "containerNo", "cabinetNo"), ""));
        batch.setTrackingNo(defaultText(pickText(row, "waybillNo", "trackingNo", "shippingNo", "transportNo", "expressNo", "logisticsNo"), ""));
        String sourceStatus = pickMeaningfulStatusText(row, "batchStatus", "statusName", "shipOrderStatusName", "packageStatusName", "logisticsStatusName", "state", "StatusName");
        batch.setSourceStatus(sourceStatus);
        batch.setRawStatus(sourceStatus);
        batch.setDestination(normalizeDestination(
                pickText(row, "destination", "destinationCode", "country", "countryCode", "CountryId", "CityId", "siteCode"),
                pickText(row, "warehouseName", "targetWarehouseName", "storeroomTitle", "StoreroomTitle", "StoreroomName"),
                batchNo
        ));
        batch.setTargetWarehouseName(pickText(row, "warehouseName", "targetWarehouseName", "storeroomName", "storeroomTitle", "StoreroomTitle", "StoreroomName", "depotName"));
        batch.setTransportMode(normalizeTransportMode(pickText(row, "transportName", "transportTitle", "TransportTitle", "TransportName", "transportMode", "shippingMode", "logisticsMode", "transportId", "TransportId")));
        batch.setSourceCreatedAt(normalizeDateTime(pickText(row, "sourceCreatedAt", "createTime", "CreateTime", "createdAt", "orderTime")));
        DateRange range = parseEtDateRange(pickText(row, "ETD_ETA", "etdEta"));
        batch.setEstimatedDepartureAt(firstText(normalizeDateTime(pickText(row, "estimatedDepartureAt", "estimatedDepartureTime", "estimateDepartureTime", "estimateShipTime")), range == null ? null : range.startDateTime));
        batch.setEstimatedArrivalAt(firstText(normalizeDateTime(pickText(row, "estimatedArrivalAt", "estimatedArrivalTime", "estimateArrivalTime", "etaTime")), range == null ? null : range.endDateTime));
        batch.setOfficialEtaDate(parseLocalDate(firstText(normalizeDate(pickText(row, "officialEtaDate", "etaDate", "estimatedArrivalDate", "arrivalDate", "deliveryDate")), range == null ? null : range.endDate)));
        PluginSyncNode node = buildNode(row);
        batch.setNodes(node == null ? Collections.emptyList() : List.of(node));
        PluginSyncPackage itemPackage = buildPackage(row, batchNo, index);
        batch.setPackages(itemPackage == null ? Collections.emptyList() : List.of(itemPackage));
        return batch;
    }

    private PluginSyncPackage buildPackage(JsonNode row, String batchNo, int index) {
        PluginSyncLine line = buildLine(row);
        String boxNo = pickBoxNo(row);
        if (!StringUtils.hasText(boxNo) && !StringUtils.hasText(line.getPsku())) {
            return null;
        }
        PluginSyncPackage itemPackage = new PluginSyncPackage();
        itemPackage.setBoxNo(defaultText(boxNo, batchNo + "-" + (index + 1)));
        itemPackage.setExternalBoxNo(pickExternalBoxNo(row));
        itemPackage.setTrackingNo(defaultText(pickText(row, "waybillNo", "trackingNo", "shippingNo", "transportNo", "expressNo", "logisticsNo"), ""));
        itemPackage.setPackageStatus(pickMeaningfulStatusText(row, "packageStatusName", "packageStatus", "boxStatusName", "boxStatus", "StatusName", "statusName"));
        itemPackage.setLogisticsStatus(pickMeaningfulStatusText(row, "logisticsStatusName", "logisticsStatus", "transportStatusName", "transportStatus"));
        itemPackage.setLines(StringUtils.hasText(line.getPsku()) ? List.of(line) : Collections.emptyList());
        return itemPackage;
    }

    private PluginSyncLine buildLine(JsonNode row) {
        String psku = defaultText(pickText(row, "psku", "pSku", "pskuCode", "skuCode", "SkuCode", "sku", "goodsSku", "productSku", "Barcode", "barcode"), "");
        PluginSyncLine line = new PluginSyncLine();
        line.setPsku(psku);
        line.setSku(defaultText(pickText(row, "sku", "skuCode", "SkuCode", "goodsSku", "sellerSku", "Barcode", "barcode"), psku));
        line.setMsku(defaultText(pickText(row, "msku", "mSku", "platformSku", "modelNumber", "ModelNumber"), ""));
        line.setProductName(defaultText(pickText(row, "productName", "goodsName", "GoodsTitle", "goodsTitle", "titleCn", "TitleCn", "titleEn", "TitleEn", "skuName", "itemName", "name", "title"), ""));
        String storeCode = defaultText(pickText(row, "storeCode", "shopCode", "storeNo"), "");
        if (storeCode.trim().matches("(?i)^STR[A-Z0-9-]+$")) {
            line.setStoreCode(storeCode.trim());
            line.setSiteCode(defaultText(normalizeSite(firstText(pickText(row, "siteCode", "site", "countryCode"), inferSiteFromStoreCode(storeCode))), ""));
        } else {
            line.setStoreCode("");
            line.setSiteCode("");
        }
        line.setShippedQuantity(defaultInteger(pickInteger(row, "caseQuantity", "CaseQuantity", "realQuantity", "RealQuantity", "shippedQuantity", "shipQuantity", "quantity", "qty", "num", "skuNum"), 0));
        line.setReceivedQuantity(defaultInteger(pickInteger(row, "receivedQuantity", "receivedQty", "inboundQuantity"), 0));
        return line;
    }

    private PluginSyncNode buildNode(JsonNode row) {
        String nodeTime = normalizeDateTime(firstText(
                pickText(row, "nodeTime", "statusTime", "updateTime", "createTime", "CreateTime", "createdAt", "shipTime", "deliveryTime", "time")
        ));
        String description = firstText(
                pickMeaningfulStatusText(row, "shipOrderStatusName", "statusName", "StatusName", "packageStatusName", "logisticsStatusName", "state", "packageStatus", "logisticsStatus", "boxStatus", "transportStatus"),
                pickText(row, "description", "content", "desc", "remark")
        );
        if (!StringUtils.hasText(description) || !StringUtils.hasText(nodeTime)) {
            return null;
        }
        PluginSyncNode node = new PluginSyncNode();
        node.setDescription(description);
        node.setNodeTime(nodeTime);
        node.setNodeStatus(normalizeNodeStatus(row));
        return node;
    }

    private List<PluginSyncBatch> mergeBatches(List<PluginSyncBatch> batches) {
        List<PluginSyncBatch> merged = new ArrayList<>();
        for (PluginSyncBatch batch : batches) {
            PluginSyncBatch target = merged.stream()
                    .filter(candidate -> Objects.equals(candidate.getBatchNo(), batch.getBatchNo()))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                merged.add(batch);
                continue;
            }
            if (!StringUtils.hasText(target.getTrackingNo())) target.setTrackingNo(batch.getTrackingNo());
            if (!StringUtils.hasText(target.getTransportMode())) target.setTransportMode(batch.getTransportMode());
            if (!StringUtils.hasText(target.getDestination())) target.setDestination(batch.getDestination());
            if (!StringUtils.hasText(target.getTargetWarehouseName())) target.setTargetWarehouseName(batch.getTargetWarehouseName());
            if (!StringUtils.hasText(target.getSourceCreatedAt())) target.setSourceCreatedAt(batch.getSourceCreatedAt());
            if (!StringUtils.hasText(target.getEstimatedDepartureAt())) target.setEstimatedDepartureAt(batch.getEstimatedDepartureAt());
            if (!StringUtils.hasText(target.getEstimatedArrivalAt())) target.setEstimatedArrivalAt(batch.getEstimatedArrivalAt());
            if (target.getOfficialEtaDate() == null) target.setOfficialEtaDate(batch.getOfficialEtaDate());
            mergeNodes(target, batch);
            mergePackages(target, batch);
        }
        return merged;
    }

    private void mergeNodes(PluginSyncBatch target, PluginSyncBatch source) {
        List<PluginSyncNode> nodes = new ArrayList<>(target.getNodes());
        Set<String> keys = new HashSet<>();
        for (PluginSyncNode node : nodes) {
            keys.add(node.getNodeStatus() + "|" + node.getNodeTime() + "|" + node.getDescription());
        }
        for (PluginSyncNode node : source.getNodes()) {
            String key = node.getNodeStatus() + "|" + node.getNodeTime() + "|" + node.getDescription();
            if (keys.add(key)) {
                nodes.add(node);
            }
        }
        target.setNodes(nodes);
    }

    private void mergePackages(PluginSyncBatch target, PluginSyncBatch source) {
        List<PluginSyncPackage> packages = new ArrayList<>(target.getPackages());
        for (PluginSyncPackage itemPackage : source.getPackages()) {
            PluginSyncPackage targetPackage = packages.stream()
                    .filter(candidate -> Objects.equals(candidate.getBoxNo(), itemPackage.getBoxNo()))
                    .findFirst()
                    .orElse(null);
            if (targetPackage == null) {
                packages.add(itemPackage);
                continue;
            }
            List<PluginSyncLine> lines = new ArrayList<>(targetPackage.getLines());
            lines.addAll(itemPackage.getLines());
            targetPackage.setLines(lines);
            if (!StringUtils.hasText(targetPackage.getTrackingNo())) targetPackage.setTrackingNo(itemPackage.getTrackingNo());
            if (!StringUtils.hasText(targetPackage.getPackageStatus())) targetPackage.setPackageStatus(itemPackage.getPackageStatus());
            if (!StringUtils.hasText(targetPackage.getExternalBoxNo())) targetPackage.setExternalBoxNo(itemPackage.getExternalBoxNo());
        }
        target.setPackages(packages);
    }

    private List<PluginSyncSourceBatchExpectation> buildSourceBatchExpectations(List<JsonNode> rows) {
        Map<String, ExpectationAccumulator> byBatch = new LinkedHashMap<>();
        for (int index = 0; index < rows.size(); index += 1) {
            JsonNode row = rows.get(index);
            String batchNo = pickBatchNo(row);
            if (!StringUtils.hasText(batchNo)) {
                continue;
            }
            ExpectationAccumulator item = byBatch.computeIfAbsent(batchNo, ignored -> new ExpectationAccumulator());
            String boxNo = pickBoxNo(row);
            Integer rawBoxNum = pickInteger(row, "sendBoxCount", "SendBoxCount", "allBoxNumber", "AllBoxNumber", "caseNumber", "realNumber", "boxCount", "packageCount", "cartonCount");
            Integer rawQuantity = pickInteger(row, "sendQuantity", "SendQuantity", "inlandQuantity", "InlandQuantity", "shippedQuantity", "shipQuantity", "quantity", "qty", "num", "skuNum");
            if (StringUtils.hasText(boxNo)) {
                item.boxes.add(boxNo);
            } else if (rawBoxNum == null) {
                item.boxes.add(batchNo + "-" + (index + 1));
            }
            if (rawBoxNum != null && rawBoxNum > 0) {
                item.explicitBoxNum = Math.max(item.explicitBoxNum == null ? 0 : item.explicitBoxNum, rawBoxNum);
            }
            if (rawQuantity != null && rawQuantity > 0) {
                item.totalQuantity += rawQuantity;
            }
        }
        List<PluginSyncSourceBatchExpectation> expectations = new ArrayList<>();
        for (Map.Entry<String, ExpectationAccumulator> entry : byBatch.entrySet()) {
            PluginSyncSourceBatchExpectation expectation = new PluginSyncSourceBatchExpectation();
            expectation.setBatchNo(entry.getKey());
            Integer boxNum = entry.getValue().explicitBoxNum != null
                    ? entry.getValue().explicitBoxNum
                    : entry.getValue().boxes.isEmpty() ? null : entry.getValue().boxes.size();
            expectation.setBoxNum(boxNum);
            expectation.setTotalQuantity(entry.getValue().totalQuantity > 0 ? entry.getValue().totalQuantity : null);
            expectations.add(expectation);
        }
        return expectations;
    }

    private List<JsonNode> extractRows(JsonNode root, int limit) {
        List<JsonNode> records = firstArrayAt(root, new String[][]{
                {"rows"}, {"data"}, {"data", "rows"}, {"data", "list"}, {"data", "items"},
                {"result", "rows"}, {"result", "list"}, {"list"}, {"items"}
        });
        if (records.isEmpty()) {
            records = firstObjectArrayDeep(root);
        }
        int max = limit == Integer.MAX_VALUE ? limit : Math.max(1, Math.min(DEFAULT_SHIP_ORDER_LIMIT, limit));
        return records.subList(0, Math.min(max, records.size()));
    }

    private List<JsonNode> extractBoxModifyRows(JsonNode root) {
        List<JsonNode> records = extractRows(root, Integer.MAX_VALUE);
        if (!records.isEmpty()) {
            return records;
        }
        for (String[] path : new String[][]{{"data"}, {"result"}, {"item"}, {"model"}, {"box"}}) {
            JsonNode node = atPath(root, path);
            if (node.isObject() && StringUtils.hasText(pickExternalBoxNo(node))) {
                return List.of(node);
            }
        }
        return root.isObject() && StringUtils.hasText(pickExternalBoxNo(root)) ? List.of(root) : Collections.emptyList();
    }

    private List<JsonNode> firstArrayAt(JsonNode source, String[][] paths) {
        for (String[] path : paths) {
            JsonNode current = atPath(source, path);
            if (current.isArray()) {
                List<JsonNode> result = new ArrayList<>();
                current.forEach(row -> {
                    if (row.isObject()) {
                        result.add(row);
                    }
                });
                return result;
            }
        }
        return Collections.emptyList();
    }

    private JsonNode atPath(JsonNode source, String[] path) {
        JsonNode current = source == null ? MissingNode.getInstance() : source;
        for (String key : path) {
            current = readValue(current, key);
            if (current.isMissingNode()) {
                return current;
            }
        }
        return current;
    }

    private List<JsonNode> firstObjectArrayDeep(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Collections.emptyList();
        }
        if (node.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            node.forEach(row -> {
                if (row.isObject()) {
                    result.add(row);
                }
            });
            return result;
        }
        if (node.isObject()) {
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                List<JsonNode> result = firstObjectArrayDeep(children.next());
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return Collections.emptyList();
    }

    private JsonNode parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("ET JSON 解析失败。", exception);
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
            String value = text(readValue(source, key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String pickMeaningfulStatusText(JsonNode source, String... keys) {
        String value = pickText(source, keys);
        return StringUtils.hasText(value) && !value.trim().matches("^\\d+$") ? value : null;
    }

    private Integer pickInteger(JsonNode source, String... keys) {
        BigDecimal value = pickDecimal(source, keys);
        return value == null ? null : value.intValue();
    }

    private BigDecimal pickDecimal(JsonNode source, String... keys) {
        for (String key : keys) {
            BigDecimal value = decimal(readValue(source, key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().replace(",", "").trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        if (node.isIntegralNumber()) {
            return String.valueOf(node.asLong());
        }
        if (node.isNumber()) {
            return node.decimalValue().stripTrailingZeros().toPlainString();
        }
        return null;
    }

    private String normalizeRecordKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "");
    }

    private String pickBatchNo(JsonNode row) {
        return pickText(row, "shipOrderId", "ShipOrderId", "shipOrderNo", "shipOrderCode", "orderNo", "orderSn", "batchNo", "batchSn", "id");
    }

    private String pickBoxNo(JsonNode row) {
        return pickText(row, "clientBoxId", "ClientBoxId", "boxNo", "boxCode", "cartonNo", "packageNo", "boxId", "BoxId", "barcode", "Barcode");
    }

    private String pickExternalBoxNo(JsonNode row) {
        return pickText(row, "externalBoxNo", "BoxId", "boxId", "platformBoxNo", "forwarderBoxNo", "thirdBoxNo");
    }

    private String normalizeBatchStatus(JsonNode source) {
        String status = defaultText(pickMeaningfulStatusText(source, "batchStatus", "statusName", "StatusName", "shipOrderStatusName", "packageStatusName", "logisticsStatusName", "state"), "");
        if (status.matches("(?i).*(取消|作废|CANCEL).*")) return "cancelled";
        if (status.matches("(?i).*(异常|EXCEPTION|ERROR|失败).*")) return "exception";
        if (status.matches("(?i).*(入仓|签收|完成|RECEIVED|WAREHOUSE).*")) return "warehouse_received";
        return "in_transit";
    }

    private String normalizeNodeStatus(JsonNode source) {
        String text = String.join(" ",
                defaultText(pickMeaningfulStatusText(source, "nodeStatus", "statusName", "StatusName", "shipOrderStatusName", "packageStatusName", "logisticsStatusName", "state"), ""),
                defaultText(pickText(source, "content", "description", "desc", "remark"), "")
        );
        if (text.matches("(?i).*(取消|作废|CANCEL).*")) return "cancelled";
        if (text.matches("(?i).*(异常|EXCEPTION|ERROR|失败).*")) return "exception";
        if (text.matches("(?i).*(入仓|签收|妥投|完成|WAREHOUSE|RECEIVED|DELIVERED).*")) return "warehouse_received";
        if (text.matches("(?i).*(派送|派件|DELIVERING|OUT FOR DELIVERY).*")) return "delivering";
        if (text.matches("(?i).*(放行|RELEASED).*")) return "customs_released";
        if (text.matches("(?i).*(清关|CUSTOMS).*")) return "customs_clearance";
        if (text.matches("(?i).*(到港|抵达|ARRIVED|ARRIVAL).*")) return "arrived_port";
        if (text.matches("(?i).*(已出库|出库|发出|起飞|离港|DEPART|DEPARTED|ON THE WAY).*")) return "departed_origin";
        if (text.matches("(?i).*(收货|揽收|交货代|HAND|PICKED).*")) return "handed_to_forwarder";
        if (text.matches("(?i).*(创建|CREATED).*")) return "created";
        return "in_transit";
    }

    private String normalizeTransportMode(String value) {
        if (!StringUtils.hasText(value)) return null;
        if (value.matches("(?i).*(AIR|空运|航空|航班|飞机).*")) return "AIR";
        if (value.matches("(?i).*(SEA|海运|海派|船).*")) return "SEA";
        return null;
    }

    private String normalizeDestination(String... values) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    if (builder.length() > 0) {
                        builder.append(' ');
                    }
                    builder.append(value);
                }
            }
        }
        String text = builder.toString().toUpperCase(Locale.ROOT);
        if (text.matches(".*(\\bRUH\\b|FBN-RUH|KSA|SAUDI|沙特|利雅得).*")) return "RUH";
        if (text.matches(".*(\\bDB\\b|DUBAI|DXB|UAE|迪拜).*")) return "DB";
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

    private String normalizeDate(String value) {
        String dateTime = normalizeDateTime(value);
        return StringUtils.hasText(dateTime) ? dateTime.substring(0, 10) : null;
    }

    private LocalDate parseLocalDate(String value) {
        return StringUtils.hasText(value) ? LocalDate.parse(value) : null;
    }

    private DateRange parseEtDateRange(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}").matcher(value);
        List<String> dates = new ArrayList<>();
        while (matcher.find()) {
            dates.add(matcher.group());
        }
        if (dates.isEmpty()) {
            return null;
        }
        String start = normalizeDateTime(dates.get(0));
        String end = normalizeDateTime(dates.get(dates.size() - 1));
        if (!StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return null;
        }
        return new DateRange(start, end, end.substring(0, 10));
    }

    private String normalizeSite(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("SA") || normalized.equals("AE") || normalized.equals("EG") ? normalized : null;
    }

    private String inferSiteFromStoreCode(String storeCode) {
        Matcher matcher = Pattern.compile("(?i)-N([A-Z]{2})$").matcher(storeCode.trim());
        return matcher.find() ? normalizeSite(matcher.group(1)) : null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
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

    private URI uri(String baseUrl, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return URI.create(path);
        }
        return URI.create(defaultText(baseUrl, "https://wl.et-global.cn").replaceAll("/+$", "")
                + "/" + path.replaceAll("^/+", ""));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("ET 自动同步请求序列化失败。", exception);
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

    private boolean looksLikeHtml(String body) {
        return StringUtils.hasText(body) && body.trim().matches("(?is)^\\s*<!doctype.*|^\\s*<html\\b.*");
    }

    private boolean looksLikeCaptchaChallenge(String body) {
        if (!StringUtils.hasText(body)) {
            return false;
        }
        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("captcha")
                || normalized.contains("validatecode")
                || normalized.contains("verifycode")
                || body.contains("验证码");
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

    private static final class ExpectationAccumulator {
        private final Set<String> boxes = new HashSet<>();
        private Integer explicitBoxNum;
        private int totalQuantity;
    }

    private static final class DateRange {
        private final String startDateTime;
        private final String endDateTime;
        private final String endDate;

        private DateRange(String startDateTime, String endDateTime, String endDate) {
            this.startDateTime = startDateTime;
            this.endDateTime = endDateTime;
            this.endDate = endDate;
        }
    }

    private static final class FetchSession {
        private final String baseUrl;
        private final HttpClient httpClient;

        private FetchSession(String baseUrl, HttpClient httpClient) {
            this.baseUrl = baseUrl;
            this.httpClient = httpClient;
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
