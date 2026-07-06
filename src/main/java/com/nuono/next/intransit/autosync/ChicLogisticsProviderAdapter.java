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
import java.math.RoundingMode;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
public class ChicLogisticsProviderAdapter implements LogisticsProviderAdapter {
    static final int DEFAULT_DETAIL_LIMIT = 10;
    static final int ORDER_REPORT_LIMIT = 50;

    private static final String BASE_URL = "https://erp.chicexpressglobal.com";
    private static final String PURCHASE_BATCH_LIST_PATH = "/api/purchase/purchase-order/purchaseBatch/query";
    private static final String PURCHASE_BATCH_DETAIL_PATH = "/api/purchase/purchase-order/purchaseBatch/detail";
    private static final String ORDER_REPORT_LIST_PATH = "/api/order/report/list?customerName=&orderSn=&warehousingSn=&shippingNo=&status=&country=&transportMode=&statusTime=&page=1&rows=50";
    private static final DateTimeFormatter NODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FLEXIBLE_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-M-d H:mm")
            .optionalStart()
            .appendPattern(":ss")
            .optionalEnd()
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter();
    private static final Pattern DIMENSION_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private final LogisticsAutoSyncProperties properties;
    private final ObjectMapper objectMapper;

    public ChicLogisticsProviderAdapter(LogisticsAutoSyncProperties properties) {
        this.properties = properties == null ? new LogisticsAutoSyncProperties() : properties;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String sourceSystem() {
        return "CHIC";
    }

    @Override
    public LogisticsProviderFetchResult fetch(LogisticsProviderFetchRequest request) {
        LogisticsAutoSyncProperties.Chic chic = chicProperties();
        if (!chic.isEnabled()) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.CONFIGURATION_ERROR,
                    "Chic 自动同步 HTTP 拉取未启用。"
            );
        }
        if (!StringUtils.hasText(chic.getBaseUrl()) || !StringUtils.hasText(chic.getLoginPath())) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.CONFIGURATION_ERROR,
                    "Chic 自动同步缺少 baseUrl 或 loginPath 配置。"
            );
        }
        if (request == null || !StringUtils.hasText(request.getLoginAccount()) || !StringUtils.hasText(request.getPassword())) {
            return LogisticsProviderFetchResult.failure(
                    LogisticsProviderFailureCode.INVALID_CREDENTIAL,
                    "Chic 自动同步账号或密码为空。"
            );
        }

        try {
            FetchSession session = login(chic, request);
            int detailLimit = Math.max(1, Math.min(DEFAULT_DETAIL_LIMIT, request.getRecentLimit() <= 0 ? DEFAULT_DETAIL_LIMIT : request.getRecentLimit()));
            String listBody = postJson(
                    session,
                    PURCHASE_BATCH_LIST_PATH,
                    buildRecentBatchListPayload(detailLimit)
            );
            List<ChicBatchListItem> items = extractBatchListItems(parseJson(listBody), detailLimit);
            if (items.isEmpty()) {
                return LogisticsProviderFetchResult.success(emptyCommand());
            }

            Map<String, String> detailJsonByBatchId = new LinkedHashMap<>();
            for (ChicBatchListItem item : items) {
                detailJsonByBatchId.put(item.purchaseBatchId, postJson(
                        session,
                        PURCHASE_BATCH_DETAIL_PATH,
                        Collections.singletonMap("purchaseBatchId", item.purchaseBatchId)
                ));
            }
            String orderReportBody = get(session, ORDER_REPORT_LIST_PATH);
            PluginSyncCommand command = normalize(listBody, detailJsonByBatchId, orderReportBody, detailLimit);
            LogisticsProviderFetchResult result = LogisticsProviderFetchResult.success(command);
            result.setPackageCount(countPackages(command));
            result.setLineCount(countLines(command));
            result.setNodeCount(countNodes(command));
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LogisticsProviderFetchResult.failure(LogisticsProviderFailureCode.NETWORK_ERROR, "Chic 自动同步请求被中断。");
        } catch (IOException exception) {
            return LogisticsProviderFetchResult.failure(LogisticsProviderFailureCode.NETWORK_ERROR, "Chic 自动同步网络请求失败。");
        } catch (ChicAuthenticationException exception) {
            return LogisticsProviderFetchResult.failure(exception.failureCode, exception.getMessage());
        } catch (RuntimeException exception) {
            return LogisticsProviderFetchResult.failure(LogisticsProviderFailureCode.PROVIDER_ERROR, "Chic 自动同步返回数据解析失败。");
        }
    }

    public PluginSyncCommand normalize(String listJson, Map<String, String> detailJsonByBatchId, String orderReportJson) {
        return normalize(listJson, detailJsonByBatchId, orderReportJson, DEFAULT_DETAIL_LIMIT);
    }

    PluginSyncCommand normalize(String listJson, Map<String, String> detailJsonByBatchId, String orderReportJson, int detailLimit) {
        int limit = Math.max(1, Math.min(DEFAULT_DETAIL_LIMIT, detailLimit));
        JsonNode listRoot = parseJson(listJson);
        List<ChicBatchListItem> items = extractBatchListItems(listRoot, limit);
        PluginSyncCommand command = emptyCommand();
        command.setSourceBatchExpectations(buildSourceBatchExpectations(items));

        List<PluginSyncBatch> batches = new ArrayList<>();
        for (ChicBatchListItem item : items) {
            String detailJson = findDetailJson(detailJsonByBatchId, item);
            if (!StringUtils.hasText(detailJson)) {
                continue;
            }
            PluginSyncBatch batch = normalizeDetailToBatch(parseJson(detailJson), item);
            if (batch != null && StringUtils.hasText(batch.getBatchNo())) {
                batches.add(batch);
            }
        }
        command.setBatches(batches);
        if (StringUtils.hasText(orderReportJson)) {
            mergeOrderReports(command, extractOrderReportItems(parseJson(orderReportJson), ORDER_REPORT_LIMIT));
        }
        return command;
    }

    private FetchSession login(LogisticsAutoSyncProperties.Chic chic, LogisticsProviderFetchRequest request)
            throws IOException, InterruptedException {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds(chic)))
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        FetchSession session = new FetchSession(chic, httpClient, null);
        Map<String, String> loginPayload = new LinkedHashMap<>();
        loginPayload.put(defaultText(chic.getLoginAccountField(), "username"), request.getLoginAccount());
        loginPayload.put(defaultText(chic.getLoginPasswordField(), "password"), request.getPassword());
        HttpResponse<String> response = send(session, requestBuilder(chic.getLoginPath(), session.token)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(loginPayload)))
                .build());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ChicAuthenticationException(LogisticsProviderFailureCode.INVALID_CREDENTIAL, "Chic 登录失败，请检查账号或权限。");
        }
        if (response.statusCode() >= 400) {
            throw new ChicAuthenticationException(LogisticsProviderFailureCode.PROVIDER_ERROR, "Chic 登录接口返回异常状态。");
        }
        if (looksLikeHtml(response.body())) {
            throw new ChicAuthenticationException(LogisticsProviderFailureCode.CAPTCHA_REQUIRED, "Chic 登录返回页面内容，可能需要验证码或风控验证。");
        }
        return new FetchSession(chic, httpClient, extractToken(response.body()));
    }

    private String postJson(FetchSession session, String path, Object payload) throws IOException, InterruptedException {
        HttpResponse<String> response = send(session, requestBuilder(path, session.token)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                .build());
        return requireJsonBody(response);
    }

    private String get(FetchSession session, String path) throws IOException, InterruptedException {
        HttpResponse<String> response = send(session, requestBuilder(path, session.token)
                .GET()
                .build());
        return requireJsonBody(response);
    }

    private HttpResponse<String> send(FetchSession session, HttpRequest request) throws IOException, InterruptedException {
        return session.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder requestBuilder(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(timeoutSeconds(chicProperties())))
                .header("accept", "application/json, text/plain, */*")
                .header("content-type", "application/json;charset=UTF-8")
                .header("token-type", "web");
        if (StringUtils.hasText(token)) {
            builder.header("token", token);
        }
        return builder;
    }

    private String requireJsonBody(HttpResponse<String> response) {
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ChicAuthenticationException(LogisticsProviderFailureCode.INVALID_CREDENTIAL, "Chic 登录态失效或账号无权限。");
        }
        if (response.statusCode() >= 400) {
            throw new ChicAuthenticationException(LogisticsProviderFailureCode.PROVIDER_ERROR, "Chic 接口返回异常状态。");
        }
        String body = response.body() == null ? "" : response.body();
        if (looksLikeHtml(body)) {
            throw new ChicAuthenticationException(LogisticsProviderFailureCode.CAPTCHA_REQUIRED, "Chic 接口返回登录页面，可能需要验证码或风控验证。");
        }
        return body;
    }

    private URI uri(String path) {
        String baseUrl = defaultText(chicProperties().getBaseUrl(), BASE_URL);
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return URI.create(path);
        }
        return URI.create(baseUrl.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", ""));
    }

    private LogisticsAutoSyncProperties.Chic chicProperties() {
        return properties.getChic() == null ? new LogisticsAutoSyncProperties.Chic() : properties.getChic();
    }

    private int timeoutSeconds(LogisticsAutoSyncProperties.Chic chic) {
        return Math.max(5, Math.min(120, chic.getTimeoutSeconds()));
    }

    private Map<String, Object> buildRecentBatchListPayload(int detailLimit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", "");
        payload.put("customerCode", "");
        payload.put("orderEndTime", "");
        payload.put("orderStartTime", "");
        payload.put("orderTime", Collections.emptyList());
        payload.put("page", 1);
        payload.put("purchaseBatchSn", "");
        payload.put("purchaseSn", "");
        payload.put("rows", Math.max(1, Math.min(DEFAULT_DETAIL_LIMIT, detailLimit)));
        payload.put("serviceItem", "");
        payload.put("status", "");
        payload.put("stockDepot", "");
        payload.put("stockEndTime", "");
        payload.put("stockStartTime", "");
        payload.put("stockTime", Collections.emptyList());
        payload.put("transportMode", "");
        payload.put("warehouseState", Collections.emptyList());
        payload.put("warehousingSn", "");
        payload.put("waybillType", "");
        return payload;
    }

    private PluginSyncCommand emptyCommand() {
        PluginSyncCommand command = new PluginSyncCommand();
        command.setSourceSystem("CHIC");
        command.setForwarderName("启客");
        command.setBatches(Collections.emptyList());
        command.setSourceBatchExpectations(Collections.emptyList());
        return command;
    }

    private PluginSyncBatch normalizeDetailToBatch(JsonNode responseBody, ChicBatchListItem listItem) {
        JsonNode detail = unwrapApiData(responseBody);
        if (!detail.isObject()) {
            return null;
        }
        String batchNo = firstText(
                pickText(detail, "purchaseBatchSn", "purchaseBatchNo", "batchNo", "batchSn", "batchCode", "batchNumber", "sn"),
                listItem.batchNo,
                listItem.purchaseBatchId
        );
        if (!StringUtils.hasText(batchNo)) {
            return null;
        }
        JsonNode batchInfo = objectNode(getValueByKey(detail, "batchInfo"));
        List<PluginSyncPackage> packages = buildPackages(detail, batchNo);
        List<JsonNode> goodsRows = extractGoodsRows(detail);
        if (goodsRows.isEmpty()) {
            goodsRows = collectNestedPackageGoodsRows(extractPackageRows(detail), batchNo);
        }
        attachGoodsToPackages(packages, goodsRows, batchNo);

        PluginSyncBatch batch = new PluginSyncBatch();
        batch.setBatchNo(batchNo);
        batch.setBatchStatus(normalizeBatchStatus(detail));
        batch.setContainerNo(defaultText(pickText(detail, "containerNo", "cabinetNo"), ""));
        batch.setDestination(normalizeDestination(
                pickText(detail, "destination", "destinationCode", "countryCode", "siteCode"),
                pickText(detail, "targetWarehouseName", "warehouseName", "destinationWarehouseName", "targetWarehouse", "warehouse"),
                batchNo
        ));
        batch.setTargetWarehouseName(pickText(detail, "targetWarehouseName", "warehouseName", "destinationWarehouseName", "targetWarehouse", "warehouse"));
        batch.setTransportMode(normalizeTransportMode(pickText(detail, "transportMode", "shippingMode", "logisticsMode", "mode", "shippingType")));
        batch.setDepartureDate(parseLocalDate(firstText(
                text(getValueByKey(detail, "departureDate")),
                text(getValueByKey(detail, "departureTime")),
                text(getValueByKey(detail, "shippedAt")),
                text(getValueByKey(batchInfo, "departureDate")),
                text(getValueByKey(batchInfo, "departureTime")),
                text(getValueByKey(batchInfo, "shippedAt"))
        )));
        batch.setOfficialEtaDate(parseLocalDate(firstNormalizedDate(detail, batchInfo, listItem.raw)));
        batch.setDeliveryAppointmentText(pickText(detail, DELIVERY_APPOINTMENT_TEXT_KEYS));
        batch.setExternalShipmentNo(pickText(detail, EXTERNAL_SHIPMENT_NO_KEYS));
        batch.setSourceCreatedAt(firstNormalizedDateTime(detail, batchInfo, listItem.raw, SOURCE_CREATED_AT_KEYS));
        batch.setEstimatedDepartureAt(dropIfBefore(firstNormalizedDateTime(detail, batchInfo, listItem.raw, ESTIMATED_DEPARTURE_AT_KEYS), batch.getSourceCreatedAt()));
        batch.setEstimatedArrivalAt(dropIfBefore(firstNormalizedDateTime(detail, batchInfo, listItem.raw, ESTIMATED_ARRIVAL_AT_KEYS), batch.getSourceCreatedAt()));
        String status = pickMeaningfulStatusText(detail, "batchStatus", "status", "state", "purchaseBatchStatus");
        if (StringUtils.hasText(status)) {
            batch.setSourceStatus(status);
            batch.setRawStatus(status);
        }
        batch.setPackages(packages);
        batch.setNodes(buildNodes(detail));
        batch.setTrackingNo(firstText(extractTrackingNo(detail), packages.stream()
                .map(PluginSyncPackage::getTrackingNo)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("")));
        return batch;
    }

    private List<PluginSyncPackage> buildPackages(JsonNode detail, String batchNo) {
        List<JsonNode> packageRows = extractPackageRows(detail);
        List<PluginSyncPackage> packages = new ArrayList<>();
        for (int index = 0; index < packageRows.size(); index += 1) {
            JsonNode row = packageRows.get(index);
            PluginSyncPackage itemPackage = new PluginSyncPackage();
            itemPackage.setBoxNo(defaultText(extractBoxNoForBatch(row, batchNo), batchNo + "-" + (index + 1)));
            itemPackage.setTrackingNo(defaultText(extractTrackingNo(row), ""));
            mergePackageFields(itemPackage, row);
            packages.add(itemPackage);
        }
        return packages;
    }

    private void attachGoodsToPackages(List<PluginSyncPackage> packages, List<JsonNode> goodsRows, String batchNo) {
        if (packages.isEmpty()) {
            PluginSyncPackage fallback = new PluginSyncPackage();
            fallback.setBoxNo(batchNo + "-1");
            fallback.setTrackingNo("");
            packages.add(fallback);
        }
        for (int index = 0; index < goodsRows.size(); index += 1) {
            JsonNode row = goodsRows.get(index);
            String boxNo = extractBoxNoForBatch(row, batchNo);
            PluginSyncPackage itemPackage = StringUtils.hasText(boxNo)
                    ? packages.stream().filter(candidate -> Objects.equals(candidate.getBoxNo(), boxNo)).findFirst().orElse(null)
                    : null;
            if (itemPackage == null && StringUtils.hasText(boxNo)) {
                itemPackage = new PluginSyncPackage();
                itemPackage.setBoxNo(boxNo);
                itemPackage.setTrackingNo(defaultText(extractTrackingNo(row), ""));
                packages.add(itemPackage);
            }
            PluginSyncPackage target = itemPackage == null ? packages.get(Math.min(index, packages.size() - 1)) : itemPackage;
            mergePackageFields(target, row);
            List<PluginSyncLine> lines = new ArrayList<>(target.getLines());
            lines.add(buildLine(row));
            target.setLines(lines);
        }
    }

    private PluginSyncLine buildLine(JsonNode row) {
        String psku = defaultText(pickText(row, "psku", "pSku", "pskuCode", "productSku", "productCode", "sku", "skuCode", "goodsSku", "itemSku"), "");
        PluginSyncLine line = new PluginSyncLine();
        line.setPsku(psku);
        line.setSku(defaultText(pickText(row, "sku", "skuCode", "goodsSku", "itemSku", "sellerSku"), psku));
        line.setMsku(defaultText(pickText(row, "msku", "mSku", "storeSku", "platformSku"), ""));
        line.setProductName(defaultText(pickText(row, "productName", "goodsName", "skuName", "itemName", "name", "title"), ""));
        String storeCode = defaultText(pickText(row, "storeCode", "shopCode", "storeNo", "sellerStoreCode"), "");
        if (!storeCode.trim().matches("(?i)^STR[A-Z0-9-]+$")) {
            line.setStoreCode("");
            line.setSiteCode("");
        } else {
            line.setStoreCode(storeCode.trim());
            line.setSiteCode(defaultText(normalizeSite(firstText(
                    pickText(row, "siteCode", "site", "countryCode", "destinationSite"),
                    inferSiteFromStoreCode(storeCode)
            )), ""));
        }
        line.setShippedQuantity(defaultInteger(pickInteger(row, "shippedQuantity", "shipQuantity", "quantity", "qty", "num", "goodsNum", "skuNum", "purchaseNum"), 0));
        line.setReceivedQuantity(defaultInteger(pickInteger(row, "receivedQuantity", "receivedQty", "warehouseQuantity", "inboundQuantity"), 0));
        line.setCartonCount(pickInteger(row, "cartonCount", "boxCount", "packageCount", "cartonNum", "boxNum", "packages"));
        line.setUnitsPerCarton(pickInteger(row, "unitsPerCarton", "qtyPerCarton", "quantityPerCarton", "pcsPerCarton", "singleBoxQuantity", "boxQuantity", "cartonQuantity"));
        line.setCartonWeightKg(pickDecimal(row, "lineCartonWeightKg", "skuCartonWeightKg", "goodsCartonWeightKg", "lineCartonWeight", "skuCartonWeight", "goodsCartonWeight"));
        line.setCartonVolumeCbm(pickDecimal(row, "lineCartonVolumeCbm", "skuCartonVolumeCbm", "goodsCartonVolumeCbm", "lineCartonVolume", "skuCartonVolume", "goodsCartonVolume"));
        return line;
    }

    private List<PluginSyncNode> buildNodes(JsonNode detail) {
        List<PluginSyncNode> nodes = new ArrayList<>();
        for (JsonNode row : firstArrayAt(detail, new String[][]{
                {"trackingList"}, {"trackList"}, {"logisticsList"}, {"trajectoryList"}, {"nodeList"}
        })) {
            PluginSyncNode node = buildNode(row);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private PluginSyncNode buildNode(JsonNode row) {
        String nodeTime = normalizeDateTime(firstText(
                text(getValueByKey(row, "nodeTime")),
                text(getValueByKey(row, "trackTime")),
                text(getValueByKey(row, "trackingTime")),
                text(getValueByKey(row, "time")),
                text(getValueByKey(row, "createTime")),
                text(getValueByKey(row, "createdAt")),
                text(getValueByKey(row, "operateTime"))
        ));
        String description = defaultText(pickText(row, "description", "content", "desc", "remark", "status", "nodeName", "title"), "");
        if (!StringUtils.hasText(nodeTime) && !StringUtils.hasText(description)) {
            return null;
        }
        PluginSyncNode node = new PluginSyncNode();
        node.setNodeStatus(normalizeNodeStatus(row));
        node.setNodeTime(defaultText(nodeTime, ""));
        node.setDescription(description);
        node.setOperatorName(pickText(row, "operatorName", "operator", "handlerName"));
        return node;
    }

    private void mergeOrderReports(PluginSyncCommand command, List<ChicOrderReportItem> reports) {
        if (reports.isEmpty() || command.getBatches().isEmpty()) {
            return;
        }
        for (ChicOrderReportItem report : reports) {
            PluginSyncBatch batch = command.getBatches().stream()
                    .filter(candidate -> reportMatchesBatch(report, candidate))
                    .findFirst()
                    .orElse(null);
            if (batch == null) {
                continue;
            }
            if (!StringUtils.hasText(batch.getTransportMode()) && StringUtils.hasText(report.transportMode)) {
                batch.setTransportMode(report.transportMode);
            }
            if (!StringUtils.hasText(batch.getDestination()) && StringUtils.hasText(report.destination)) {
                batch.setDestination(report.destination);
            }
            if (batch.getOfficialEtaDate() == null && StringUtils.hasText(report.officialEtaDate)) {
                batch.setOfficialEtaDate(parseLocalDate(report.officialEtaDate));
            }
            if (!StringUtils.hasText(batch.getDeliveryAppointmentText()) && StringUtils.hasText(report.deliveryAppointmentText)) {
                batch.setDeliveryAppointmentText(report.deliveryAppointmentText);
            }
            if (!StringUtils.hasText(batch.getExternalShipmentNo()) && StringUtils.hasText(report.externalShipmentNo)) {
                batch.setExternalShipmentNo(report.externalShipmentNo);
            }
            if (!StringUtils.hasText(batch.getSourceCreatedAt()) && StringUtils.hasText(report.sourceCreatedAt)) {
                batch.setSourceCreatedAt(report.sourceCreatedAt);
            }
            if (!StringUtils.hasText(batch.getEstimatedDepartureAt()) && StringUtils.hasText(report.estimatedDepartureAt)) {
                batch.setEstimatedDepartureAt(report.estimatedDepartureAt);
            }
            if (!StringUtils.hasText(batch.getEstimatedArrivalAt()) && StringUtils.hasText(report.estimatedArrivalAt)) {
                batch.setEstimatedArrivalAt(report.estimatedArrivalAt);
            }
            if (StringUtils.hasText(report.statusText)) {
                batch.setSourceStatus(report.statusText);
                batch.setRawStatus(report.statusText);
            }
            if (StringUtils.hasText(report.trackingNo) && !StringUtils.hasText(batch.getTrackingNo())) {
                batch.setTrackingNo(report.trackingNo);
            }
            PluginSyncPackage itemPackage = findReportPackageMatch(report, batch);
            if (itemPackage != null) {
                if (StringUtils.hasText(report.trackingNo) && !StringUtils.hasText(itemPackage.getTrackingNo())) {
                    itemPackage.setTrackingNo(report.trackingNo);
                }
                mergePackageFields(itemPackage, report.raw);
            }
            PluginSyncNode node = buildOrderReportNode(report);
            if (node != null) {
                List<PluginSyncNode> nodes = new ArrayList<>(batch.getNodes());
                Set<String> nodeKeys = new HashSet<>();
                for (PluginSyncNode existing : nodes) {
                    nodeKeys.add(nodeKey(existing));
                }
                if (!nodeKeys.contains(nodeKey(node))) {
                    nodes.add(node);
                    batch.setNodes(nodes);
                }
            }
        }
    }

    private PluginSyncNode buildOrderReportNode(ChicOrderReportItem report) {
        String nodeTime = firstText(report.statusTime, normalizeDateTime(firstText(
                text(getValueByKey(report.raw, "statusTime")),
                text(getValueByKey(report.raw, "status_time")),
                text(getValueByKey(report.raw, "trackingTime")),
                text(getValueByKey(report.raw, "trackTime")),
                text(getValueByKey(report.raw, "updateTime")),
                text(getValueByKey(report.raw, "updatedAt")),
                text(getValueByKey(report.raw, "createTime")),
                text(getValueByKey(report.raw, "createdAt")),
                text(getValueByKey(report.raw, "operateTime")),
                text(getValueByKey(report.raw, "time"))
        )));
        String statusText = firstText(report.statusText, pickMeaningfulStatusText(report.raw, "statusName", "status", "logisticsStatus", "shippingStatus", "statusText", "remark", "description", "content", "nodeName", "title"));
        String externalShipmentNo = firstText(report.externalShipmentNo, report.shippingNo);
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(statusText)) {
            parts.add(statusText);
        }
        if (StringUtils.hasText(report.trackingNo)) {
            parts.add("物流单号 " + report.trackingNo);
        } else if (StringUtils.hasText(externalShipmentNo)) {
            parts.add("外部出货编号 " + externalShipmentNo);
        }
        String description = String.join(" ", parts);
        if (!StringUtils.hasText(nodeTime) && !StringUtils.hasText(description)) {
            return null;
        }
        PluginSyncNode node = new PluginSyncNode();
        node.setNodeTime(defaultText(nodeTime, ""));
        node.setDescription(description);
        node.setNodeStatus(normalizeNodeStatus(report.raw, statusText));
        return node;
    }

    private void mergePackageFields(PluginSyncPackage itemPackage, JsonNode row) {
        setIfNull(itemPackage::getLengthCm, itemPackage::setLengthCm, firstNonNull(pickDecimal(row, PACKAGE_LENGTH_KEYS), parseDimensionText(row).length));
        setIfNull(itemPackage::getWidthCm, itemPackage::setWidthCm, firstNonNull(pickDecimal(row, PACKAGE_WIDTH_KEYS), parseDimensionText(row).width));
        setIfNull(itemPackage::getHeightCm, itemPackage::setHeightCm, firstNonNull(pickDecimal(row, PACKAGE_HEIGHT_KEYS), parseDimensionText(row).height));
        setIfNull(itemPackage::getWeightKg, itemPackage::setWeightKg, pickDecimal(row, PACKAGE_WEIGHT_KEYS));
        setIfNull(itemPackage::getVolumeCbm, itemPackage::setVolumeCbm, pickDecimal(row, PACKAGE_VOLUME_KEYS));
        setIfNull(itemPackage::getVolumeWeightKg, itemPackage::setVolumeWeightKg, pickDecimal(row, PACKAGE_VOLUME_WEIGHT_KEYS));
        setIfNull(itemPackage::getChargeableWeightKg, itemPackage::setChargeableWeightKg, pickDecimal(row, PACKAGE_CHARGEABLE_WEIGHT_KEYS));
        if (itemPackage.getVolumeCbm() == null
                && itemPackage.getLengthCm() != null
                && itemPackage.getWidthCm() != null
                && itemPackage.getHeightCm() != null) {
            itemPackage.setVolumeCbm(itemPackage.getLengthCm()
                    .multiply(itemPackage.getWidthCm())
                    .multiply(itemPackage.getHeightCm())
                    .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP));
        }
        if (!StringUtils.hasText(itemPackage.getExternalBoxNo())) {
            itemPackage.setExternalBoxNo(pickText(row, PACKAGE_EXTERNAL_BOX_NO_KEYS));
        }
        if (!StringUtils.hasText(itemPackage.getPackageStatus())) {
            itemPackage.setPackageStatus(pickMeaningfulStatusText(row, PACKAGE_STATUS_KEYS));
        }
        if (!StringUtils.hasText(itemPackage.getLogisticsStatus())) {
            itemPackage.setLogisticsStatus(pickMeaningfulStatusText(row, PACKAGE_LOGISTICS_STATUS_KEYS));
        }
    }

    private List<ChicBatchListItem> extractBatchListItems(JsonNode responseBody, int detailLimit) {
        List<ChicBatchListItem> rawItems = new ArrayList<>();
        for (JsonNode row : getBatchListCandidateRecords(responseBody)) {
            if (!row.isObject()) {
                continue;
            }
            String purchaseBatchId = pickText(row, "purchaseBatchId", "purchaseBatchID", "purchase_batch_id", "batchId", "batchID", "batch_id", "id");
            if (!StringUtils.hasText(purchaseBatchId)) {
                continue;
            }
            ChicBatchListItem item = new ChicBatchListItem(purchaseBatchId, pickText(row, "purchaseBatchSn", "purchaseBatchNo", "purchase_batch_sn", "purchase_batch_no", "batchNo", "batchSn", "batchCode", "batch_no", "batch_sn", "batch_code", "sn"), row);
            rawItems.add(item);
        }
        List<ChicBatchListItem> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ChicBatchListItem item : rawItems) {
            String key = itemKey(item);
            if (seen.add(key)) {
                unique.add(item);
            }
            if (unique.size() >= Math.max(1, Math.min(DEFAULT_DETAIL_LIMIT, detailLimit))) {
                break;
            }
        }
        return unique;
    }

    private List<PluginSyncSourceBatchExpectation> buildSourceBatchExpectations(List<ChicBatchListItem> items) {
        List<PluginSyncSourceBatchExpectation> expectations = new ArrayList<>();
        for (ChicBatchListItem item : items) {
            if (!StringUtils.hasText(item.batchNo)) {
                continue;
            }
            PluginSyncSourceBatchExpectation expectation = new PluginSyncSourceBatchExpectation();
            expectation.setBatchNo(item.batchNo);
            expectation.setBoxNum(pickInteger(item.raw, "boxNum", "boxCount", "packageCount", "cartonCount"));
            expectation.setTotalQuantity(pickInteger(item.raw, "totalQuantity", "totalQty", "quantity", "goodsQuantity"));
            expectations.add(expectation);
        }
        return expectations;
    }

    private List<ChicOrderReportItem> extractOrderReportItems(JsonNode responseBody, int limit) {
        List<ChicOrderReportItem> items = new ArrayList<>();
        for (JsonNode row : getOrderReportCandidateRecords(responseBody)) {
            if (!row.isObject()) {
                continue;
            }
            ChicOrderReportItem item = new ChicOrderReportItem();
            item.raw = row;
            item.batchNo = pickText(row, "purchaseBatchSn", "purchaseBatchNo", "purchase_batch_sn", "purchase_batch_no", "batchNo", "batchSn", "batchCode", "batch_no", "batch_sn", "采购批次", "批次号", "批次编号", "批次");
            item.destination = normalizeDestination(pickText(row, "destination", "destinationCode", "country", "countryCode", "siteCode"), pickText(row, "targetWarehouseName", "warehouseName"));
            item.deliveryAppointmentText = pickText(row, DELIVERY_APPOINTMENT_TEXT_KEYS);
            item.estimatedArrivalAt = normalizeDateTime(pickText(row, ESTIMATED_ARRIVAL_AT_KEYS));
            item.estimatedDepartureAt = normalizeDateTime(pickText(row, ESTIMATED_DEPARTURE_AT_KEYS));
            item.externalShipmentNo = pickText(row, EXTERNAL_SHIPMENT_NO_KEYS);
            item.officialEtaDate = normalizeDate(pickText(row, OFFICIAL_ETA_DATE_KEYS));
            item.orderSn = pickText(row, "orderSn", "orderNo", "order_sn", "order_no");
            item.shippingNo = pickText(row, "shippingNo", "shipping_no", "waybillNo", "waybillSn", "出库批次", "出货编号", "外部出货编号", "出库单号", "运单号", "trackingNo", "trackNo", "logisticsNo", "expressNo");
            item.sourceCreatedAt = normalizeDateTime(pickText(row, SOURCE_CREATED_AT_KEYS));
            item.statusText = pickMeaningfulStatusText(row, "statusName", "status", "logisticsStatus", "shippingStatus", "statusText", "remark", "description", "content");
            item.statusTime = normalizeDateTime(firstText(
                    text(getValueByKey(row, "statusTime")),
                    text(getValueByKey(row, "status_time")),
                    text(getValueByKey(row, "updateTime")),
                    text(getValueByKey(row, "trackingTime")),
                    text(getValueByKey(row, "trackTime")),
                    text(getValueByKey(row, "time"))
            ));
            item.trackingNo = extractTrackingNo(row);
            item.transportMode = normalizeTransportMode(pickText(row, "transportMode", "shippingMode", "logisticsMode", "mode"));
            item.warehousingSn = pickText(row, "warehousingSn", "warehousingNo", "warehousing_sn", "warehouseSn", "入仓号", "入仓编号", "入仓单号", "箱号");
            items.add(item);
            if (items.size() >= Math.max(1, Math.min(ORDER_REPORT_LIMIT, limit))) {
                break;
            }
        }
        return items;
    }

    private List<JsonNode> getBatchListCandidateRecords(JsonNode responseBody) {
        List<JsonNode> records = firstArrayAt(responseBody, new String[][]{
                {"data", "records"}, {"data", "list"}, {"data", "rows"}, {"data", "items"}, {"data", "content"},
                {"data", "result"}, {"data", "result", "records"}, {"data", "result", "list"}, {"data", "result", "rows"},
                {"data", "result", "items"}, {"data", "result", "content"}, {"data", "result", "page", "records"},
                {"data", "result", "page", "list"}, {"data", "result", "page", "rows"}, {"data", "page", "records"},
                {"data", "page", "list"}, {"data", "page", "rows"}, {"result", "records"}, {"result", "list"},
                {"result", "rows"}, {"result", "items"}, {"page", "records"}, {"page", "list"}, {"page", "rows"},
                {"records"}, {"list"}, {"items"}, {"content"}, {"rows"}, {"data"}
        });
        return records.isEmpty() ? firstObjectArrayDeep(responseBody) : records;
    }

    private List<JsonNode> getOrderReportCandidateRecords(JsonNode responseBody) {
        List<JsonNode> records = firstArrayAt(responseBody, new String[][]{
                {"data", "records"}, {"data", "list"}, {"data", "rows"}, {"data", "items"}, {"data", "content"},
                {"data", "result"}, {"data", "result", "records"}, {"data", "result", "list"}, {"data", "result", "rows"},
                {"data", "page", "records"}, {"data", "page", "list"}, {"data", "page", "rows"},
                {"result", "records"}, {"result", "list"}, {"result", "rows"}, {"records"}, {"list"}, {"rows"}, {"data"}
        });
        return records.isEmpty() ? firstObjectArrayDeep(responseBody) : records;
    }

    private List<JsonNode> extractPackageRows(JsonNode detail) {
        return firstArrayAt(detail, new String[][]{
                {"packages"}, {"packageList"}, {"boxList"}, {"cartonList"}, {"shippingBoxList"}, {"purchaseOrderList"}
        });
    }

    private List<JsonNode> extractGoodsRows(JsonNode detail) {
        return firstArrayAt(detail, new String[][]{
                {"goodsList"}, {"skuList"}, {"productList"}, {"itemList"}, {"purchaseGoodsList"}, {"purchaseOrderGoodsList"}, {"orderGoodsList"}
        });
    }

    private List<JsonNode> collectNestedPackageGoodsRows(List<JsonNode> packageRows, String batchNo) {
        List<JsonNode> rows = new ArrayList<>();
        for (JsonNode packageRow : packageRows) {
            String parentBoxNo = extractBoxNoForBatch(packageRow, batchNo);
            String parentTrackingNo = extractTrackingNo(packageRow);
            for (JsonNode goodsRow : extractGoodsRows(packageRow)) {
                ObjectNode copied = objectMapper.createObjectNode();
                if (goodsRow.isObject()) {
                    copied.setAll((ObjectNode) goodsRow);
                }
                if (StringUtils.hasText(parentBoxNo) && !StringUtils.hasText(extractBoxNoForBatch(copied, batchNo))) {
                    copied.put("boxNo", parentBoxNo);
                }
                if (StringUtils.hasText(parentTrackingNo) && !StringUtils.hasText(extractTrackingNo(copied))) {
                    copied.put("trackingNo", parentTrackingNo);
                }
                rows.add(copied);
            }
        }
        return rows;
    }

    private List<JsonNode> firstArrayAt(JsonNode source, String[][] paths) {
        for (String[] path : paths) {
            JsonNode current = source;
            for (String key : path) {
                current = getValueByKey(current, key);
                if (current.isMissingNode()) {
                    break;
                }
            }
            if (current.isArray()) {
                List<JsonNode> rows = new ArrayList<>();
                current.forEach(rows::add);
                return rows;
            }
        }
        return Collections.emptyList();
    }

    private List<JsonNode> firstObjectArrayDeep(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Collections.emptyList();
        }
        if (node.isArray()) {
            List<JsonNode> rows = new ArrayList<>();
            node.forEach(rows::add);
            return rows.stream().anyMatch(JsonNode::isObject) ? rows : Collections.emptyList();
        }
        if (node.isObject()) {
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                List<JsonNode> rows = firstObjectArrayDeep(children.next());
                if (!rows.isEmpty()) {
                    return rows;
                }
            }
        }
        return Collections.emptyList();
    }

    private JsonNode unwrapApiData(JsonNode value) {
        JsonNode data = getValueByKey(value, "data");
        if (data.isObject()) {
            return data;
        }
        return value == null ? MissingNode.getInstance() : value;
    }

    private JsonNode parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Chic JSON 解析失败。", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Chic 请求序列化失败。", exception);
        }
    }

    private JsonNode getValueByKey(JsonNode source, String key) {
        if (source == null || !source.isObject()) {
            return MissingNode.getInstance();
        }
        if (source.has(key)) {
            return source.get(key);
        }
        String normalizedKey = normalizeLookupKey(key);
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (normalizeLookupKey(entry.getKey()).equals(normalizedKey)) {
                return entry.getValue();
            }
        }
        return MissingNode.getInstance();
    }

    private JsonNode objectNode(JsonNode node) {
        return node != null && node.isObject() ? node : MissingNode.getInstance();
    }

    private String pickText(JsonNode source, String... keys) {
        for (String key : keys) {
            String value = text(getValueByKey(source, key));
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

    private BigDecimal pickDecimal(JsonNode source, String... keys) {
        for (String key : keys) {
            BigDecimal value = decimal(getValueByKey(source, key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer pickInteger(JsonNode source, String... keys) {
        BigDecimal value = pickDecimal(source, keys);
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
            String text = node.asText().replace(",", "").trim();
            if (StringUtils.hasText(text)) {
                try {
                    return new BigDecimal(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            return StringUtils.hasText(text) ? text : null;
        }
        if (node.isIntegralNumber()) {
            return String.valueOf(node.asLong());
        }
        if (node.isNumber()) {
            return node.decimalValue().stripTrailingZeros().toPlainString();
        }
        return null;
    }

    private String normalizeLookupKey(String key) {
        return key == null ? "" : key
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[（(][^）)]*[）)]", "")
                .replaceAll("[\\s_\\-/:：.,，。;；]", "")
                .replaceAll("(?:kgs?|公斤|千克|cm|厘米|cbm|m3|立方米|立方)$", "");
    }

    private String extractBoxNo(JsonNode source) {
        return pickText(source, "boxNo", "boxSn", "boxNumber", "warehousingSn", "warehousingNo", "warehousing_sn", "warehouseSn", "packageNo", "packageSn", "cartonNo", "cartonSn", "caseNo");
    }

    private String extractBoxNoForBatch(JsonNode source, String batchNo) {
        String boxNo = extractBoxNo(source);
        return StringUtils.hasText(boxNo) && !boxNo.trim().equalsIgnoreCase(batchNo) ? boxNo : null;
    }

    private String extractTrackingNo(JsonNode source) {
        return defaultText(pickText(source, "trackingNo", "tracking_no", "trackNo", "track_no", "logisticsNo", "logistics_no", "expressNo", "express_no", "carrierTrackingNo"), "");
    }

    private PackageDimensions parseDimensionText(JsonNode row) {
        String value = pickText(row, "boxSpec", "boxSpecification", "boxSize", "cartonSpec", "cartonSpecification", "cartonSize", "packageSpec", "packageSpecification", "packageSize");
        if (!StringUtils.hasText(value)) {
            return new PackageDimensions(null, null, null);
        }
        Matcher matcher = DIMENSION_PATTERN.matcher(value.replace(",", ""));
        List<BigDecimal> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(new BigDecimal(matcher.group()));
        }
        if (numbers.size() < 3) {
            return new PackageDimensions(null, null, null);
        }
        return new PackageDimensions(numbers.get(0), numbers.get(1), numbers.get(2));
    }

    private String normalizeDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.matches("^\\d{10,13}$")) {
                long epoch = Long.parseLong(text);
                if (text.length() == 13) {
                    epoch = epoch / 1000;
                }
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), SHANGHAI_ZONE).format(NODE_TIME_FORMATTER);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        String normalized = text.replace('T', ' ')
                .replaceAll("\\.\\d{3}Z?$", "")
                .replace('/', '-')
                .trim();
        Matcher matcher = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?").matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDateTime.parse(matcher.group(), FLEXIBLE_DATE_TIME_FORMATTER).format(NODE_TIME_FORMATTER);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String normalizeDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String dateTime = normalizeDateTime(value);
        if (StringUtils.hasText(dateTime)) {
            return dateTime.substring(0, 10);
        }
        String normalized = value.trim().replace('/', '-');
        Matcher matcher = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}").matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        String[] parts = matcher.group().split("-");
        return String.format("%04d-%02d-%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private LocalDate parseLocalDate(String value) {
        String normalized = normalizeDate(value);
        return StringUtils.hasText(normalized) ? LocalDate.parse(normalized) : null;
    }

    private String firstNormalizedDate(JsonNode... sources) {
        for (JsonNode source : sources) {
            String value = normalizeDate(pickText(source, OFFICIAL_ETA_DATE_KEYS));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String firstNormalizedDateTime(JsonNode first, JsonNode second, JsonNode third, String... keys) {
        for (JsonNode source : new JsonNode[]{first, second, third}) {
            String value = normalizeDateTime(pickText(source, keys));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String dropIfBefore(String value, String floor) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(floor)) {
            return value;
        }
        return value.compareTo(floor) < 0 ? null : value;
    }

    private String normalizeBatchStatus(JsonNode source) {
        String status = defaultText(pickMeaningfulStatusText(source, "batchStatus", "status", "state", "purchaseBatchStatus"), "");
        if (status.matches("(?i).*取消|.*CANCEL.*")) {
            return "cancelled";
        }
        if (status.matches("(?i).*异常|.*EXCEPTION.*|.*ERROR.*")) {
            return "exception";
        }
        if (status.matches("(?i).*(海外仓|ET仓|ETRUH).*") && status.matches("(?i).*(入仓|入库|到达|抵达|签收|WAREHOUSE|RECEIVED|DELIVERED).*")) {
            return "in_transit";
        }
        if (status.matches("(?i).*(入仓|签收|完成|RECEIVED|WAREHOUSE).*")) {
            return "warehouse_received";
        }
        return "in_transit";
    }

    private String normalizeNodeStatus(JsonNode source) {
        return normalizeNodeStatus(source, null);
    }

    private String normalizeNodeStatus(JsonNode source, String statusOverride) {
        String explicit = pickText(source, "nodeStatus", "statusCode", "trackingStatusCode");
        if (StringUtils.hasText(explicit) && Set.of("created", "handed_to_forwarder", "departed_origin", "in_transit", "arrived_port", "customs_clearance", "customs_released", "delivering", "warehouse_received", "exception", "cancelled").contains(explicit)) {
            return explicit;
        }
        String text = String.join(" ",
                defaultText(statusOverride, ""),
                defaultText(pickText(source, "status", "nodeName", "trackingStatus", "title"), ""),
                defaultText(pickText(source, "content", "description", "desc", "remark"), "")
        );
        if (text.matches("(?i).*(取消|CANCEL).*")) return "cancelled";
        if (text.matches("(?i).*(异常|EXCEPTION|ERROR|失败).*")) return "exception";
        if (text.matches("(?i).*(海外仓|ET仓|ETRUH).*") && text.matches("(?i).*(入仓|入库|到达|抵达|签收|WAREHOUSE|RECEIVED|DELIVERED).*")) return "in_transit";
        if (text.matches("(?i).*(入仓|签收|妥投|WAREHOUSE|RECEIVED|DELIVERED).*")) return "warehouse_received";
        if (text.matches("(?i).*(派送|派件|DELIVERING|OUT FOR DELIVERY).*")) return "delivering";
        if (text.matches("(?i).*(放行|RELEASED).*")) return "customs_released";
        if (text.matches("(?i).*(清关|CUSTOMS).*")) return "customs_clearance";
        if (text.matches("(?i).*(到港|抵达|ARRIVED|ARRIVAL).*")) return "arrived_port";
        if (text.matches("(?i).*(发往海外|起飞|离港|发出|DEPART|DEPARTED|ON THE WAY).*")) return "departed_origin";
        if (text.matches("(?i).*(国内收货|收货|揽收|交货代|HAND|PICKED).*")) return "handed_to_forwarder";
        if (text.matches("(?i).*(创建|CREATED).*")) return "created";
        return "in_transit";
    }

    private String normalizeDestination(String... values) {
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains("SAUDI") || normalized.contains("KSA") || normalized.equals("SA")) {
                return "SA";
            }
            if (normalized.contains("UAE") || normalized.contains("UNITED ARAB") || normalized.equals("AE")) {
                return "AE";
            }
            if (normalized.contains("EGYPT") || normalized.equals("EG")) {
                return "EG";
            }
        }
        return null;
    }

    private String normalizeTransportMode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.matches("(?i).*(AIR|空|航|3).*")) {
            return "AIR";
        }
        if (value.matches("(?i).*(SEA|海|船).*")) {
            return "SEA";
        }
        return null;
    }

    private String normalizeSite(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("SA") || normalized.equals("AE") || normalized.equals("EG") ? normalized : null;
    }

    private String inferSiteFromStoreCode(String storeCode) {
        Matcher matcher = Pattern.compile("(?i)-N([A-Z]{2})$").matcher(storeCode.trim());
        return matcher.find() ? normalizeSite(matcher.group(1)) : null;
    }

    private String findDetailJson(Map<String, String> detailJsonByBatchId, ChicBatchListItem item) {
        if (detailJsonByBatchId == null || detailJsonByBatchId.isEmpty()) {
            return null;
        }
        String byId = detailJsonByBatchId.get(item.purchaseBatchId);
        if (StringUtils.hasText(byId)) {
            return byId;
        }
        return StringUtils.hasText(item.batchNo) ? detailJsonByBatchId.get(item.batchNo) : null;
    }

    private String itemKey(ChicBatchListItem item) {
        return StringUtils.hasText(item.batchNo)
                ? "batch:" + item.batchNo.trim().toUpperCase(Locale.ROOT)
                : "id:" + item.purchaseBatchId.trim().toUpperCase(Locale.ROOT);
    }

    private boolean reportMatchesBatch(ChicOrderReportItem report, PluginSyncBatch batch) {
        List<String> reportTexts = uniqueMatchTexts(report.batchNo, report.warehousingSn, report.orderSn, report.shippingNo, report.trackingNo);
        reportTexts.addAll(uniqueMatchTexts(pickText(report.raw, "purchaseBatchSn", "purchaseBatchNo", "purchase_batch_sn", "purchase_batch_no", "batchNo", "batchSn", "batchCode", "batch_no", "batch_sn", "warehousingSn", "warehousingNo", "warehousing_sn", "orderSn", "orderNo", "order_sn", "shippingNo", "shipping_no", "waybillNo", "waybillSn", "logisticsNo", "expressNo")));
        List<String> batchTexts = uniqueMatchTexts(batch.getBatchNo(), batch.getTrackingNo(), batch.getContainerNo());
        for (PluginSyncPackage itemPackage : batch.getPackages()) {
            batchTexts.addAll(uniqueMatchTexts(itemPackage.getBoxNo(), itemPackage.getTrackingNo()));
        }
        for (String reportText : reportTexts) {
            for (String batchText : batchTexts) {
                if (textTokensMatch(reportText, batchText)) {
                    return true;
                }
            }
        }
        return false;
    }

    private PluginSyncPackage findReportPackageMatch(ChicOrderReportItem report, PluginSyncBatch batch) {
        List<String> reportTexts = uniqueMatchTexts(report.batchNo, report.warehousingSn, report.orderSn, report.shippingNo, report.trackingNo);
        for (PluginSyncPackage itemPackage : batch.getPackages()) {
            List<String> packageTexts = uniqueMatchTexts(itemPackage.getBoxNo(), itemPackage.getTrackingNo());
            for (String packageText : packageTexts) {
                for (String reportText : reportTexts) {
                    if (textTokensMatch(reportText, packageText)) {
                        return itemPackage;
                    }
                }
            }
        }
        return batch.getPackages().size() == 1 ? batch.getPackages().get(0) : null;
    }

    private List<String> uniqueMatchTexts(String... values) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String text = value.trim().toUpperCase(Locale.ROOT);
            if (text.length() >= 4 && seen.add(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private boolean textTokensMatch(String left, String right) {
        return Objects.equals(left, right)
                || (left.length() >= 8 && right.contains(left))
                || (right.length() >= 8 && left.contains(right));
    }

    private String nodeKey(PluginSyncNode node) {
        return node.getNodeStatus() + "|" + node.getNodeTime() + "|" + node.getDescription();
    }

    private String extractToken(String body) {
        JsonNode root = parseJson(body);
        for (String key : new String[]{"token", "accessToken", "access_token"}) {
            String value = pickText(root, key);
            if (StringUtils.hasText(value)) {
                return value.replaceFirst("(?i)^Bearer\\s+", "").trim();
            }
        }
        JsonNode data = getValueByKey(root, "data");
        for (String key : new String[]{"token", "accessToken", "access_token"}) {
            String value = pickText(data, key);
            if (StringUtils.hasText(value)) {
                return value.replaceFirst("(?i)^Bearer\\s+", "").trim();
            }
        }
        return null;
    }

    private boolean looksLikeHtml(String body) {
        return body != null && body.matches("(?is).*<(html|!doctype)\\b.*");
    }

    private int countPackages(PluginSyncCommand command) {
        return command.getBatches().stream().mapToInt(batch -> batch.getPackages().size()).sum();
    }

    private int countLines(PluginSyncCommand command) {
        return command.getBatches().stream()
                .flatMap(batch -> batch.getPackages().stream())
                .mapToInt(itemPackage -> itemPackage.getLines().size())
                .sum();
    }

    private int countNodes(PluginSyncCommand command) {
        return command.getBatches().stream().mapToInt(batch -> batch.getNodes().size()).sum();
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

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first == null ? second : first;
    }

    private <T> void setIfNull(java.util.function.Supplier<T> getter, java.util.function.Consumer<T> setter, T value) {
        if (getter.get() == null && value != null) {
            setter.accept(value);
        }
    }

    private static final class ChicBatchListItem {
        private final String purchaseBatchId;
        private final String batchNo;
        private final JsonNode raw;

        private ChicBatchListItem(String purchaseBatchId, String batchNo, JsonNode raw) {
            this.purchaseBatchId = purchaseBatchId;
            this.batchNo = batchNo;
            this.raw = raw;
        }
    }

    private static final class ChicOrderReportItem {
        private String batchNo;
        private String deliveryAppointmentText;
        private String estimatedArrivalAt;
        private String estimatedDepartureAt;
        private String externalShipmentNo;
        private String officialEtaDate;
        private String orderSn;
        private JsonNode raw;
        private String shippingNo;
        private String sourceCreatedAt;
        private String statusText;
        private String statusTime;
        private String trackingNo;
        private String transportMode;
        private String destination;
        private String warehousingSn;
    }

    private static final class FetchSession {
        private final LogisticsAutoSyncProperties.Chic chic;
        private final HttpClient httpClient;
        private final String token;

        private FetchSession(LogisticsAutoSyncProperties.Chic chic, HttpClient httpClient, String token) {
            this.chic = chic;
            this.httpClient = httpClient;
            this.token = token;
        }
    }

    private static final class ChicAuthenticationException extends RuntimeException {
        private final String failureCode;

        private ChicAuthenticationException(String failureCode, String message) {
            super(message);
            this.failureCode = failureCode;
        }
    }

    private static final class PackageDimensions {
        private final BigDecimal length;
        private final BigDecimal width;
        private final BigDecimal height;

        private PackageDimensions(BigDecimal length, BigDecimal width, BigDecimal height) {
            this.length = length;
            this.width = width;
            this.height = height;
        }
    }

    private static final String[] PACKAGE_LENGTH_KEYS = {"lengthCm", "length_cm", "boxLengthCm", "box_length_cm", "cartonLengthCm", "carton_length_cm", "packageLengthCm", "package_length_cm", "长", "箱长", "外箱长", "length", "boxLength", "box_length", "cartonLength", "carton_length", "packageLength"};
    private static final String[] PACKAGE_WIDTH_KEYS = {"widthCm", "width_cm", "boxWidthCm", "box_width_cm", "cartonWidthCm", "carton_width_cm", "packageWidthCm", "package_width_cm", "宽", "箱宽", "外箱宽", "width", "boxWidth", "box_width", "cartonWidth", "carton_width", "packageWidth"};
    private static final String[] PACKAGE_HEIGHT_KEYS = {"heightCm", "height_cm", "boxHeightCm", "box_height_cm", "cartonHeightCm", "carton_height_cm", "packageHeightCm", "package_height_cm", "高", "箱高", "外箱高", "height", "boxHeight", "box_height", "cartonHeight", "carton_height", "packageHeight"};
    private static final String[] PACKAGE_WEIGHT_KEYS = {"weightKg", "weight_kg", "boxWeightKg", "box_weight_kg", "cartonWeightKg", "carton_weight_kg", "packageWeightKg", "package_weight_kg", "grossWeightKg", "gross_weight_kg", "重量", "箱重", "外箱重量", "weight", "boxWeight", "box_weight", "cartonWeight", "carton_weight", "packageWeight", "package_weight", "grossWeight"};
    private static final String[] PACKAGE_VOLUME_KEYS = {"volumeCbm", "volume_cbm", "boxVolumeCbm", "box_volume_cbm", "cartonVolumeCbm", "carton_volume_cbm", "packageVolumeCbm", "package_volume_cbm", "体积", "箱体积", "volume", "cbm", "boxVolume", "box_volume", "cartonVolume", "carton_volume", "packageVolume"};
    private static final String[] PACKAGE_VOLUME_WEIGHT_KEYS = {"volumeWeightKg", "volume_weight_kg", "volumetricWeightKg", "volumetric_weight_kg", "boxVolumeWeightKg", "box_volume_weight_kg", "cartonVolumeWeightKg", "carton_volume_weight_kg", "体积重量", "材积重", "泡重", "volumeWeight", "volume_weight", "volumetricWeight", "volumetric_weight", "boxVolumeWeight", "box_volume_weight", "cartonVolumeWeight"};
    private static final String[] PACKAGE_CHARGEABLE_WEIGHT_KEYS = {"chargeableWeightKg", "chargeable_weight_kg", "chargeWeightKg", "charge_weight_kg", "billingWeightKg", "billing_weight_kg", "chargedWeightKg", "charged_weight_kg", "计费重量", "计费重", "计重", "实际计费重量", "结算重量", "chargeableWeight", "chargeable_weight", "chargeWeight", "charge_weight", "billingWeight", "billing_weight", "chargedWeight", "charged_weight", "feeWeight", "fee_weight", "settlementWeight"};
    private static final String[] PACKAGE_EXTERNAL_BOX_NO_KEYS = {"externalBoxNo", "external_box_no", "forwarderBoxNo", "forwarder_box_no", "platformBoxNo", "platform_box_no", "externalCartonNo", "external_carton_no", "forwarderCartonNo", "forwarder_carton_no", "platformCartonNo", "platform_carton_no", "carrierBoxNo", "carrierCartonNo", "外部箱号", "货代箱号", "平台箱号"};
    private static final String[] PACKAGE_STATUS_KEYS = {"packageStatus", "package_status", "boxStatus", "box_status", "cartonStatus", "carton_status", "packageState", "package_state", "boxState", "cartonState", "箱子状态", "箱状态"};
    private static final String[] PACKAGE_LOGISTICS_STATUS_KEYS = {"logisticsStatus", "logistics_status", "shippingStatus", "shipping_status", "transportStatus", "transport_status", "statusName", "status_name", "statusText", "status_text", "status", "物流状态", "运输状态"};
    private static final String[] EXTERNAL_SHIPMENT_NO_KEYS = {"externalShipmentNo", "external_shipment_no", "externalShipmentSn", "external_shipment_sn", "shipmentNo", "shipment_no", "shipmentSn", "shipment_sn", "shippingNo", "shipping_no", "出库批次", "出货编号", "外部出货编号", "出库单号", "运单号", "shippingSn", "shipping_sn", "shipOrderNo", "ship_order_no", "deliveryOrderNo", "delivery_order_no", "outboundNo", "outbound_no", "outboundSn", "outbound_sn", "outboundBatchNo", "outbound_batch_no", "outboundBatchSn", "outbound_batch_sn", "waybillNo", "waybill_no", "waybillSn"};
    private static final String[] DELIVERY_APPOINTMENT_TEXT_KEYS = {"deliveryAppointmentText", "delivery_appointment_text", "deliveryAppointment", "delivery_appointment", "appointmentText", "appointment_text", "appointmentTime", "appointment_time", "appointmentWindow", "appointment_window", "reservationTime", "reservation_time", "reservedTime", "reserved_time", "deliveryTimeText", "delivery_time_text", "arrivalTimeText", "到货时间点", "到货时间", "到仓时间点", "预约时间"};
    private static final String[] SOURCE_CREATED_AT_KEYS = {"stockTime", "stock_time", "stockAt", "stock_at", "inboundTime", "inbound_time", "warehouseInboundTime", "warehouse_inbound_time", "warehousingTime", "warehousing_time", "入库时间", "入仓时间", "入库日期", "入仓日期", "sourceCreatedAt", "source_created_at", "createTime", "create_time", "createdAt", "created_at", "createdTime", "created_time", "orderTime", "创建时间", "批次创建时间", "订单创建时间", "下单时间"};
    private static final String[] ESTIMATED_DEPARTURE_AT_KEYS = {"estimatedDepartureAt", "estimated_departure_at", "estimatedDepartureTime", "estimated_departure_time", "departurePreTime", "departure_pre_time", "departurePlanTime", "departure_plan_time", "plannedDepartureTime", "planned_departure_time", "estimateDepartureAt", "estimate_departure_at", "estimateDepartureTime", "estimate_departure_time", "expectedDepartureAt", "expected_departure_at", "expectedDepartureTime", "expected_departure_time", "departureTime", "预计出发时间", "预计发出时间", "预计起飞时间", "预计出库时间"};
    private static final String[] ESTIMATED_ARRIVAL_AT_KEYS = {"estimatedArrivalAt", "estimated_arrival_at", "estimatedArrivalTime", "estimated_arrival_time", "arrivalPreTime", "arrival_pre_time", "arrivalPlanTime", "arrival_plan_time", "plannedArrivalTime", "planned_arrival_time", "estimateArrivalAt", "estimate_arrival_at", "estimateArrivalTime", "estimate_arrival_time", "expectedArrivalAt", "expected_arrival_at", "expectedArrivalTime", "expected_arrival_time", "etaTime", "eta_time", "arrivalTime", "预计到达时间", "预计抵达时间", "预计落地时间", "预计到仓时间"};
    private static final String[] OFFICIAL_ETA_DATE_KEYS = {"officialEtaDate", "official_eta_date", "officialEtaTime", "official_eta_time", "arrivalPreTime", "arrival_pre_time", "etaDate", "eta_date", "eta", "expectedArrivalDate", "expected_arrival_date", "estimatedArrivalDate", "到货日期", "到仓日期", "预计到货日期", "预计到仓日期", "官方预计到仓日期"};
}
