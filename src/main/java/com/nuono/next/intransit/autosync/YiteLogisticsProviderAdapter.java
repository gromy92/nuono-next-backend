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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        return LogisticsProviderFetchResult.failure(
                LogisticsProviderFailureCode.CONFIGURATION_ERROR,
                "义特自动同步登录接口尚未配置。"
        );
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
}
