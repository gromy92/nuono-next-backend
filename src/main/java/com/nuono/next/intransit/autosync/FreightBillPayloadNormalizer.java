package com.nuono.next.intransit.autosync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightBillCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightComponentCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class FreightBillPayloadNormalizer {
    private static final ObjectMapper OBJECT_MAPPER = objectMapper();
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final String[] BILL_NO_KEYS = {
            "billNo", "bill_no", "invoiceNo", "invoice_no", "invoiceNumber", "invoice_number",
            "statementNo", "statement_no", "账单号", "发票号"
    };
    private static final String[] BATCH_KEYS = {
            "batchReferenceNo", "batch_reference_no", "shipmentNo", "shipment_no", "shipmentNumber",
            "shipment_number", "itemNumber", "item_number", "purchaseBatchSn", "purchaseBatchNo",
            "warehousingSn", "warehousingNo", "inboundNo", "inbound_no", "关联单号", "运单号", "入仓号"
    };
    private static final String[] FEE_KEYS = {
            "rawFeeName", "raw_fee_name", "feeName", "fee_name", "expenseName", "expense_name",
            "expenseType", "expense_type", "chargeName", "charge_name", "费用类型", "费用名称", "项目"
    };
    private static final String[] AMOUNT_KEYS = {
            "amountCny", "amount_cny", "cnyAmount", "cny_amount", "amount", "feeAmount", "fee_amount",
            "expenseAmount", "expense_amount", "receivableAmount", "receivable_amount", "金额", "费用金额", "应收金额"
    };
    private static final String[] DECLARED_TOTAL_KEYS = {
            "invoiceTotal", "invoice_total", "billTotal", "bill_total", "statementTotal", "statement_total",
            "invoiceAmount", "invoice_amount", "billAmount", "bill_amount", "账单金额", "应付金额", "合计金额"
    };

    private FreightBillPayloadNormalizer() {
    }

    static FreightBillFetchResult normalize(String sourceSystem, String payload) {
        ActualFreightSyncCommand command = new ActualFreightSyncCommand();
        command.setSourceSystem(normalizeCode(sourceSystem));
        if (!StringUtils.hasText(payload)) {
            List<String> issues = List.of("EMPTY_PROVIDER_PAYLOAD");
            return FreightBillFetchResult.success(command, false, 0, normalizedDigest(command, issues), issues);
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(payload);
        } catch (Exception exception) {
            List<String> issues = List.of("INVALID_PROVIDER_JSON");
            return FreightBillFetchResult.success(command, false, 0, sha256(payload), issues);
        }

        List<CandidateRow> candidates = new ArrayList<>();
        collectCandidateRows(root, candidates, null, null, null, null);
        int sourceRowCount = countSourceRows(root);
        if (candidates.isEmpty()) {
            boolean emptyResponse = sourceRowCount == 0 && !hasAnyCostSignal(root);
            List<String> issues = emptyResponse
                    ? Collections.emptyList()
                    : List.of("MISSING_ACTUAL_COST_FIELDS");
            FreightBillFetchResult result = FreightBillFetchResult.success(
                    command, emptyResponse, sourceRowCount, normalizedDigest(command, issues), issues
            );
            result.setSourceUpdatedAt(sourceUpdatedAt(root));
            return result;
        }

        Map<String, BillAccumulator> groups = new LinkedHashMap<>();
        Map<String, BigDecimal> declaredTotals = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();
        int invalidRows = 0;
        for (CandidateRow candidate : candidates) {
            JsonNode row = candidate.row;
            String billNo = firstText(pickText(row, BILL_NO_KEYS), candidate.billNo);
            String batchReferenceNo = pickText(row, BATCH_KEYS);
            String feeName = pickText(row, FEE_KEYS);
            BigDecimal amount = pickDecimal(row, AMOUNT_KEYS);
            if (!StringUtils.hasText(billNo) || !StringUtils.hasText(batchReferenceNo)
                    || !StringUtils.hasText(feeName) || amount == null) {
                invalidRows += 1;
                continue;
            }
            String key = billNo + "\u0000" + batchReferenceNo;
            BillAccumulator accumulator = groups.computeIfAbsent(
                    key,
                    ignored -> new BillAccumulator(billNo, batchReferenceNo, candidate.billStatus, candidate.billDate)
            );
            accumulator.add(row, feeName, amount);
            if (candidate.declaredTotal != null) {
                declaredTotals.putIfAbsent(billNo, candidate.declaredTotal);
            }
        }
        if (invalidRows > 0) {
            issues.add("INCOMPLETE_COST_ROWS:" + invalidRows);
        }
        if (groups.isEmpty()) {
            issues.add("NO_COMPLETE_COST_ROW");
        }

        List<ActualFreightBillCommand> bills = new ArrayList<>();
        Map<String, BigDecimal> normalizedTotals = new LinkedHashMap<>();
        for (BillAccumulator accumulator : groups.values()) {
            ActualFreightBillCommand bill = accumulator.toCommand();
            bills.add(bill);
            normalizedTotals.merge(bill.getBillNo(), bill.getCnyTotalAmount(), BigDecimal::add);
        }
        bills.sort(Comparator
                .comparing(ActualFreightBillCommand::getBillNo)
                .thenComparing(ActualFreightBillCommand::getBatchReferenceNo));
        command.setBills(bills);
        boolean totalsComplete = true;
        for (Map.Entry<String, BigDecimal> entry : normalizedTotals.entrySet()) {
            BigDecimal declared = declaredTotals.get(entry.getKey());
            if (declared == null) {
                issues.add("DECLARED_BILL_TOTAL_MISSING:" + entry.getKey());
                totalsComplete = false;
            } else if (declared.subtract(entry.getValue()).abs().compareTo(new BigDecimal("0.01")) > 0) {
                issues.add("DECLARED_BILL_TOTAL_MISMATCH:" + entry.getKey());
                totalsComplete = false;
            }
        }
        boolean complete = invalidRows == 0 && !bills.isEmpty() && sourceRowCount <= candidates.size() && totalsComplete;
        if (sourceRowCount > candidates.size()) {
            issues.add("SOURCE_ROWS_WITHOUT_COST_DETAIL:" + (sourceRowCount - candidates.size()));
        }
        FreightBillFetchResult result = FreightBillFetchResult.success(
                command, complete, sourceRowCount, normalizedDigest(command, issues), issues
        );
        result.setSourceUpdatedAt(sourceUpdatedAt(root));
        return result;
    }

    private static void collectCandidateRows(
            JsonNode node,
            List<CandidateRow> rows,
            String inheritedBillNo,
            String inheritedStatus,
            LocalDateTime inheritedDate,
            BigDecimal inheritedTotal
    ) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectCandidateRows(
                    item, rows, inheritedBillNo, inheritedStatus, inheritedDate, inheritedTotal
            ));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String billNo = firstText(pickText(node, BILL_NO_KEYS), inheritedBillNo);
        String status = firstText(
                pickText(node, "billStatus", "bill_status", "invoiceStatus", "invoice_status", "status", "账单状态"),
                inheritedStatus
        );
        LocalDateTime billDate = parseDateTime(firstText(
                pickText(node, "billDate", "bill_date", "invoiceDate", "invoice_date", "date", "账单日期"),
                inheritedDate == null ? null : inheritedDate.toString()
        ));
        BigDecimal declaredTotal = firstDecimal(pickDecimal(node, DECLARED_TOTAL_KEYS), inheritedTotal);
        if (StringUtils.hasText(pickText(node, BATCH_KEYS))
                && (StringUtils.hasText(pickText(node, FEE_KEYS)) || pickDecimal(node, AMOUNT_KEYS) != null)) {
            rows.add(new CandidateRow(node, billNo, status, billDate, declaredTotal));
            return;
        }
        node.elements().forEachRemaining(item -> collectCandidateRows(
                item, rows, billNo, status, billDate, declaredTotal
        ));
    }

    private static int countSourceRows(JsonNode root) {
        int[] maximum = new int[]{0};
        countSourceRows(root, maximum);
        return maximum[0];
    }

    private static void countSourceRows(JsonNode node, int[] maximum) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> countSourceRows(item, maximum));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = normalizeKey(field.getKey());
            JsonNode value = field.getValue();
            if (value.isArray() && ("records".equals(key) || "rows".equals(key) || "list".equals(key)
                    || "items".equals(key) || "datasource".equals(key))) {
                maximum[0] = Math.max(maximum[0], value.size());
            }
            countSourceRows(value, maximum);
        }
    }

    private static boolean hasAnyCostSignal(JsonNode root) {
        return StringUtils.hasText(findFirstTextDeep(root, FEE_KEYS))
                || StringUtils.hasText(findFirstTextDeep(root, AMOUNT_KEYS))
                || StringUtils.hasText(findFirstTextDeep(root, BILL_NO_KEYS));
    }

    private static String sourceUpdatedAt(JsonNode root) {
        return findFirstTextDeep(
                root,
                "sourceUpdatedAt", "source_updated_at", "updatedAt", "updated_at", "updateTime", "update_time",
                "modifiedAt", "modified_at", "更新时间"
        );
    }

    private static String findFirstTextDeep(JsonNode node, String... keys) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            String value = pickText(node, keys);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        Iterator<JsonNode> elements = node.elements();
        while (elements.hasNext()) {
            String value = findFirstTextDeep(elements.next(), keys);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String pickText(JsonNode row, String... keys) {
        if (row == null || !row.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = getIgnoreCase(row, key);
            String text = cleanText(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private static BigDecimal pickDecimal(JsonNode row, String... keys) {
        String text = pickText(row, keys);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.replace(",", "").replaceAll("[^0-9.+-]", "");
        if (!StringUtils.hasText(normalized) || "+".equals(normalized) || "-".equals(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static JsonNode getIgnoreCase(JsonNode row, String requestedKey) {
        JsonNode direct = row.get(requestedKey);
        if (direct != null) {
            return direct;
        }
        String normalizedRequested = normalizeKey(requestedKey);
        Iterator<Map.Entry<String, JsonNode>> fields = row.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (normalizeKey(field.getKey()).equals(normalizedRequested)) {
                return field.getValue();
            }
        }
        return null;
    }

    private static String cleanText(JsonNode value) {
        if (value == null || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return HTML_TAG.matcher(text).replaceAll(" ").replace("&nbsp;", " ").trim();
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static BigDecimal firstDecimal(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim().replace('/', '-');
        if (text.matches("\\d{10,13}")) {
            long epoch = Long.parseLong(text);
            if (text.length() == 13) {
                epoch /= 1000;
            }
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), SHANGHAI);
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported provider format.
            }
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-M-d")).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("物流费用快照摘要生成失败。", exception);
        }
    }

    private static String normalizedDigest(ActualFreightSyncCommand command, List<String> issues) {
        try {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("command", command);
            normalized.put("issues", issues == null ? Collections.emptyList() : issues);
            return sha256(OBJECT_MAPPER.writeValueAsString(normalized));
        } catch (Exception exception) {
            throw new IllegalStateException("物流费用标准快照摘要生成失败。", exception);
        }
    }

    private static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    private static final class BillAccumulator {
        private final ActualFreightBillCommand bill = new ActualFreightBillCommand();
        private final List<ActualFreightComponentCommand> components = new ArrayList<>();
        private BigDecimal total = BigDecimal.ZERO;
        private BigDecimal freight = BigDecimal.ZERO;
        private BigDecimal delivery = BigDecimal.ZERO;
        private BigDecimal handling = BigDecimal.ZERO;
        private BigDecimal storage = BigDecimal.ZERO;
        private BigDecimal customs = BigDecimal.ZERO;

        private BillAccumulator(String billNo, String batchReferenceNo, String status, LocalDateTime billDate) {
            bill.setBillNo(billNo);
            bill.setBatchReferenceNo(batchReferenceNo);
            bill.setBillStatus(status);
            bill.setBillDate(billDate);
            bill.setBusinessOccurredAt(billDate);
            bill.setCurrencyCode("CNY");
            bill.setExchangeRateToCny(BigDecimal.ONE);
        }

        private void add(JsonNode row, String feeName, BigDecimal amount) {
            String standardFeeType = standardFeeType(feeName);
            ActualFreightComponentCommand component = new ActualFreightComponentCommand();
            component.setBoxNo(pickText(row, "boxNo", "box_no", "cartonNo", "carton_no", "箱号"));
            component.setExternalBoxNo(pickText(row, "externalBoxNo", "external_box_no", "warehousingSn", "warehousingNo", "入仓号"));
            component.setPsku(pickText(row, "psku", "partnerSku", "partner_sku", "sku"));
            component.setRawFeeName(feeName);
            component.setStandardFeeType(standardFeeType);
            component.setChargeQuantity(pickDecimal(row, "chargeQuantity", "charge_quantity", "quantity", "qty", "计费数量", "数量"));
            component.setChargeUnit(pickText(row, "chargeUnit", "charge_unit", "unit", "计费单位", "单位"));
            component.setUnitPrice(pickDecimal(row, "unitPrice", "unit_price", "price", "单价"));
            component.setCurrencyCode(firstText(pickText(row, "currencyCode", "currency_code", "currency", "币种"), "CNY"));
            component.setExchangeRateToCny(BigDecimal.ONE);
            component.setOriginalAmount(amount);
            component.setCnyAmount(amount);
            component.setQuantity(pickDecimal(row, "ctns", "cartonCount", "carton_count", "件数", "箱数"));
            component.setMeasuredWeightKg(pickDecimal(row, "kg", "weightKg", "weight_kg", "actualWeight", "actual_weight", "重量"));
            component.setMeasuredVolumeCbm(pickDecimal(row, "cbm", "volumeCbm", "volume_cbm", "体积"));
            component.setChargeableWeightKg(pickDecimal(row, "chargeableWeightKg", "chargeable_weight_kg", "chargeableWeight", "计费重量"));
            component.setAllocationBasis("provider_bill_row_batch_header");
            components.add(component);
            total = total.add(amount);
            switch (standardFeeType) {
                case "DELIVERY": delivery = delivery.add(amount); break;
                case "INBOUND_HANDLING": handling = handling.add(amount); break;
                case "STORAGE": storage = storage.add(amount); break;
                case "CUSTOMS": customs = customs.add(amount); break;
                default: freight = freight.add(amount); break;
            }
            if (bill.getBillDate() == null) {
                LocalDateTime date = parseDateTime(pickText(row, "billDate", "bill_date", "invoiceDate", "invoice_date", "date", "账单日期"));
                bill.setBillDate(date);
                bill.setBusinessOccurredAt(date);
            }
            if (!StringUtils.hasText(bill.getBillStatus())) {
                bill.setBillStatus(pickText(row, "billStatus", "bill_status", "invoiceStatus", "invoice_status", "status", "账单状态"));
            }
        }

        private ActualFreightBillCommand toCommand() {
            BigDecimal rounded = total.setScale(6, RoundingMode.HALF_UP);
            bill.setOriginalTotalAmount(rounded);
            bill.setCnyTotalAmount(rounded);
            bill.setFreightAmountCny(freight.setScale(6, RoundingMode.HALF_UP));
            bill.setDeliveryAmountCny(delivery.setScale(6, RoundingMode.HALF_UP));
            bill.setHandlingAmountCny(handling.setScale(6, RoundingMode.HALF_UP));
            bill.setStorageAmountCny(storage.setScale(6, RoundingMode.HALF_UP));
            bill.setCustomsAmountCny(customs.setScale(6, RoundingMode.HALF_UP));
            components.sort(Comparator.comparing(component -> String.join(
                    "|",
                    valueOrEmpty(component.getRawFeeName()),
                    valueOrEmpty(component.getBoxNo()),
                    valueOrEmpty(component.getExternalBoxNo()),
                    valueOrEmpty(component.getPsku()),
                    component.getCnyAmount() == null ? "" : component.getCnyAmount().stripTrailingZeros().toPlainString(),
                    component.getChargeQuantity() == null ? "" : component.getChargeQuantity().stripTrailingZeros().toPlainString()
            )));
            bill.setComponents(components);
            return bill;
        }
    }

    private static final class CandidateRow {
        private final JsonNode row;
        private final String billNo;
        private final String billStatus;
        private final LocalDateTime billDate;
        private final BigDecimal declaredTotal;

        private CandidateRow(
                JsonNode row,
                String billNo,
                String billStatus,
                LocalDateTime billDate,
                BigDecimal declaredTotal
        ) {
            this.row = row;
            this.billNo = billNo;
            this.billStatus = billStatus;
            this.billDate = billDate;
            this.declaredTotal = declaredTotal;
        }
    }

    private static String standardFeeType(String feeName) {
        String value = feeName == null ? "" : feeName.toLowerCase(Locale.ROOT);
        if (value.contains("派送") || value.contains("delivery") || value.contains("末端")) {
            return "DELIVERY";
        }
        if (value.contains("卸货") || value.contains("入库") || value.contains("handling")) {
            return "INBOUND_HANDLING";
        }
        if (value.contains("仓储") || value.contains("storage")) {
            return "STORAGE";
        }
        if (value.contains("关税") || value.contains("清关") || value.contains("customs")) {
            return "CUSTOMS";
        }
        return "HEADHAUL";
    }
}
