package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonOperationException;
import com.nuono.next.noon.NoonResponseClassifier;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.noonlog.NoonHttpCallLogContext;
import com.nuono.next.noonlog.NoonHttpCallLogContextHolder;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AsnDetail;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.NoonAppointmentClient;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.SlotCapacity;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.sales.NoonSalesReportBinding;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class OfficialWarehouseNoonInboundClient {

    private static final String CREATE_ASN_URL = "https://fbn.noon.partners/_svc/inbound-partners/asn/create";
    private static final String ROUTING_WAREHOUSE_URL = "https://fbn.noon.partners/_svc/inbound-partners/routing/warehouse";
    private static final String CREATE_LINES_URL = "https://fbn.noon.partners/_svc/inbound-partners/asn/lines/create-batch";
    private static final String QUERY_ASN_DETAIL_URL = "https://fbn.noon.partners/_svc/inbound-partners/asn/partner_asn_details";
    private static final String QUERY_DAY_CAPACITY_URL = "https://fbn.noon.partners/_svc/inbound-scheduler/day/fbn/v1/capacity";
    private static final String QUERY_SLOT_CAPACITY_URL = "https://fbn.noon.partners/_svc/inbound-scheduler/slot/fbn/v1/capacity";
    private static final String SET_WAREHOUSES_URL = "https://fbn.noon.partners/_svc/inbound-partners/routing/set-warehouses";
    private static final String RESCHEDULE_ASN_URL = "https://fbn.noon.partners/_svc/inbound-partners/asn/re-schedule";
    private static final String SEAL_ASN_URL = "https://fbn.noon.partners/_svc/inbound-partners/asn/seal";
    private static final String SCHEDULE_APPOINTMENT_URL = "https://fbn.noon.partners/_svc/inbound-scheduler/slot/fbn/v1/schedule";

    private final ObjectMapper objectMapper;
    private final NoonResponseClassifier responseClassifier;

    public OfficialWarehouseNoonInboundClient(
            ObjectMapper objectMapper,
            NoonResponseClassifier responseClassifier
    ) {
        this.objectMapper = objectMapper;
        this.responseClassifier = responseClassifier;
    }

    JsonNode createAsn(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            int totalQuantity
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("totalQty", totalQuantity);
        return postNoonJson(session, binding, context.withOperation("CREATE_ASN"), CREATE_ASN_URL, body);
    }

    JsonNode routeWarehouse(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            String asnNr,
            List<AsnLineInsertRecord> lineRows
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("asnNr", asnNr);
        ArrayNode lines = body.putArray("lines");
        for (AsnLineInsertRecord line : lineRows) {
            ObjectNode lineNode = lines.addObject();
            lineNode.put("sku", line.noonSku);
            lineNode.put("qty", line.quantity == null ? 0 : line.quantity);
            lineNode.put("storage_type_code", firstNonBlank(line.storageTypeCode, "standard"));
        }
        return postNoonJson(session, binding, context.withOperation("ROUTE_WAREHOUSE"), ROUTING_WAREHOUSE_URL, body);
    }

    JsonNode createLines(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            String asnNr,
            List<AsnLineInsertRecord> lineRows
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("asnNr", asnNr);
        ArrayNode lines = body.putArray("partnerAsnLineList");
        for (AsnLineInsertRecord line : lineRows) {
            ObjectNode lineNode = lines.addObject();
            lineNode.put("psku_code", line.pskuCode);
            lineNode.put("qty", line.quantity == null ? 0 : line.quantity);
            lineNode.put("cubic_feet", line.cubicFeet == null ? 0 : line.cubicFeet.doubleValue());
            lineNode.put("storage_type_code", firstNonBlank(line.storageTypeCode, "standard"));
            lineNode.put("sku", line.noonSku);
        }
        return postNoonJson(session, binding, context.withOperation("CREATE_ASN_LINES"), CREATE_LINES_URL, body);
    }

    AsnDetail queryAsnDetail(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            String asnNr
    ) {
        return parseAsnDetail(queryAsnDetailRow(session, binding, context, asnNr));
    }

    JsonNode queryAsnDetailRow(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            String asnNr
    ) {
        JsonNode response = postNoonJson(
                session,
                binding,
                context.withOperation("QUERY_ASN_DETAIL"),
                QUERY_ASN_DETAIL_URL,
                asnDetailBody(binding, asnNr)
        );
        return firstDataNode(response);
    }

    void setWarehouses(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            String asnNr,
            String warehouseTo
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("asnNr", asnNr);
        body.put("warehouseTo", warehouseTo);
        JsonNode response = postNoonJson(session, binding, context.withOperation("SET_WAREHOUSES"), SET_WAREHOUSES_URL, body);
        Integer status = intValue(response, "status");
        boolean ok = "ok".equalsIgnoreCase(text(response, "data")) || status != null && status == 200;
        if (!ok) {
            throw new IllegalStateException("Noon 设置 ASN 仓库失败。");
        }
    }

    AsnDetail sealAsn(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            String asnNr
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("asnNr", asnNr);
        JsonNode response = postNoonJson(session, binding, context.withOperation("SEAL_ASN"), SEAL_ASN_URL, body);
        return parseAsnDetail(firstDataNode(response));
    }

    JsonNode syncAsnList(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            JsonNode body
    ) {
        return postNoonJson(session, binding, context.withOperation("SYNC_ASN_LIST"), QUERY_ASN_DETAIL_URL, body);
    }

    NoonAppointmentClient appointmentClient(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            Consumer<OfficialWarehouseAppointmentRunner.AppointmentTask> onWarehousesSet
    ) {
        return new RealNoonAppointmentClient(session, binding, context, onWarehousesSet);
    }

    private class RealNoonAppointmentClient implements NoonAppointmentClient {
        private final NoonSession session;
        private final NoonSalesReportBinding binding;
        private final NoonCallContext context;
        private final Consumer<OfficialWarehouseAppointmentRunner.AppointmentTask> onWarehousesSet;

        private RealNoonAppointmentClient(
                NoonSession session,
                NoonSalesReportBinding binding,
                NoonCallContext context,
                Consumer<OfficialWarehouseAppointmentRunner.AppointmentTask> onWarehousesSet
        ) {
            this.session = session;
            this.binding = binding;
            this.context = context;
            this.onWarehousesSet = onWarehousesSet;
        }

        @Override
        public AsnDetail queryAsnDetail(OfficialWarehouseAppointmentRunner.AppointmentTask task) {
            return OfficialWarehouseNoonInboundClient.this.queryAsnDetail(session, binding, context, task.noonAsnNr);
        }

        @Override
        public List<String> queryDayCapacity(OfficialWarehouseAppointmentRunner.AppointmentTask task) {
            JsonNode response = postNoonJson(
                    session,
                    binding,
                    context.withOperation("QUERY_DAY_CAPACITY"),
                    QUERY_DAY_CAPACITY_URL,
                    capacityBody(binding, task, null)
            );
            return readStringArray(response);
        }

        @Override
        public List<SlotCapacity> querySlotCapacity(OfficialWarehouseAppointmentRunner.AppointmentTask task, LocalDate capacityDate) {
            JsonNode response = postNoonJson(
                    session,
                    binding,
                    context.withOperation("QUERY_SLOT_CAPACITY"),
                    QUERY_SLOT_CAPACITY_URL,
                    capacityBody(binding, task, capacityDate)
            );
            JsonNode data = arrayData(response);
            List<SlotCapacity> slots = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    slots.add(new SlotCapacity(intValue(item, "id_slot"), text(item, "name")));
                }
            }
            return slots;
        }

        @Override
        public boolean setWarehouses(OfficialWarehouseAppointmentRunner.AppointmentTask task) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("asnNr", task.noonAsnNr);
            body.put("warehouseTo", task.warehouseTo);
            JsonNode response = postNoonJson(session, binding, context.withOperation("SET_WAREHOUSES"), SET_WAREHOUSES_URL, body);
            Integer status = intValue(response, "status");
            return "ok".equalsIgnoreCase(text(response, "data")) || status != null && status == 200;
        }

        @Override
        public void onWarehousesSet(OfficialWarehouseAppointmentRunner.AppointmentTask task) {
            if (onWarehousesSet != null) {
                onWarehousesSet.accept(task);
            }
        }

        @Override
        public boolean reschedule(OfficialWarehouseAppointmentRunner.AppointmentTask task) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("asnNr", task.noonAsnNr);
            JsonNode response = postNoonJson(session, binding, context.withOperation("RESCHEDULE_ASN"), RESCHEDULE_ASN_URL, body);
            JsonNode detail = firstDataNode(response);
            String status = OfficialWarehouseStatusPolicy.normalizeNoonAsnStatus(firstText(detail, "asn_status", "status"));
            return "SEALED".equals(status) || StringUtils.hasText(text(detail, "asn_nr")) || StringUtils.hasText(text(detail, "asnNr"));
        }

        @Override
        public boolean schedule(OfficialWarehouseAppointmentRunner.AppointmentTask task, LocalDate capacityDate, SlotCapacity slot) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("asn_nr", task.noonAsnNr);
            body.put("capacity_date", capacityDate.toString());
            body.put("id_slot", slot.idSlot == null ? 0 : slot.idSlot);
            body.put("total_units", task.totalUnits == null ? 0 : task.totalUnits);
            JsonNode response = postNoonJson(session, binding, context.withOperation("SCHEDULE_APPOINTMENT"), SCHEDULE_APPOINTMENT_URL, body);
            Integer status = intValue(response, "status");
            return status != null && status == 200 || "success".equalsIgnoreCase(text(response, "msg"));
        }
    }

    private ObjectNode asnDetailBody(NoonSalesReportBinding binding, String asnNr) {
        ObjectNode body = objectMapper.createObjectNode();
        putPartnerId(body, "idPartnerSource", binding.getPartnerId());
        body.put("asnNr", asnNr);
        ObjectNode pagination = body.putObject("pagination");
        pagination.put("page", 1);
        pagination.put("perPage", 10);
        pagination.put("totalPages", 10);
        body.set("filters", objectMapper.createObjectNode());
        return body;
    }

    private ObjectNode capacityBody(
            NoonSalesReportBinding binding,
            OfficialWarehouseAppointmentRunner.AppointmentTask task,
            LocalDate capacityDate
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        putPartnerId(body, "id_partner", binding.getPartnerId());
        body.put("asn_nr", task.noonAsnNr);
        body.put("warehouse_to", task.warehouseTo);
        if (capacityDate != null) {
            body.put("capacity_date", capacityDate.toString());
        }
        body.put("total_units", task.totalUnits == null ? 0 : task.totalUnits);
        return body;
    }

    private AsnDetail parseAsnDetail(JsonNode detail) {
        return new AsnDetail(firstText(detail, "asn_status", "status"));
    }

    static List<AsnLineInsertRecord> routingLineRowsFromAsnDetail(JsonNode detail) {
        if (detail == null) {
            return List.of();
        }
        JsonNode lines = detail.path("lines");
        if (!lines.isArray()) {
            lines = detail.path("partnerAsnLineList");
        }
        if (!lines.isArray()) {
            return List.of();
        }
        List<AsnLineInsertRecord> result = new ArrayList<>();
        for (JsonNode line : lines) {
            String noonSku = firstText(line, "sku", "noon_sku", "noonSku");
            Integer quantity = firstPositiveInt(
                    line,
                    "qty",
                    "quantity",
                    "total_qty",
                    "totalQty",
                    "expected_qty",
                    "expectedQty",
                    "qty_expected",
                    "qtyExpected"
            );
            if (!StringUtils.hasText(noonSku) || quantity == null) {
                continue;
            }
            AsnLineInsertRecord record = new AsnLineInsertRecord();
            record.noonSku = noonSku;
            record.quantity = quantity;
            record.storageTypeCode = firstNonBlank(
                    firstText(line, "storage_type_code", "storageTypeCode"),
                    "standard"
            );
            result.add(record);
        }
        return result;
    }

    private JsonNode postNoonJson(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            String url,
            JsonNode body
    ) {
        NoonHttpCallLogContext logContext = NoonHttpCallLogContext.builder()
                .sourceModule("OFFICIAL_WAREHOUSE")
                .operation(context.operation)
                .ownerUserId(binding.getOwnerUserId())
                .storeCode(binding.getStoreCode())
                .siteCode(binding.getSiteCode())
                .projectCode(binding.getProjectCode())
                .partnerId(binding.getPartnerId())
                .businessType(context.businessType)
                .businessId(context.businessId)
                .businessRef(context.businessRef)
                .requestSummaryJson(writeJson(body))
                .build();
        try {
            return NoonHttpCallLogContextHolder.with(
                    logContext,
                    () -> isReadOperation(context.operation)
                            ? session.postJson(url, body, true, noonHeaders(binding))
                            : session.postWriteJson(url, body, true, noonHeaders(binding))
            );
        } catch (NoonHttpException exception) {
            throw new NoonOperationException(responseClassifier.classify(context.operation, exception), exception);
        }
    }

    private static boolean isReadOperation(String operation) {
        return "QUERY_ASN_DETAIL".equals(operation)
                || "QUERY_DAY_CAPACITY".equals(operation)
                || "QUERY_SLOT_CAPACITY".equals(operation)
                || "SYNC_ASN_LIST".equals(operation);
    }

    private Map<String, String> noonHeaders(NoonSalesReportBinding binding) {
        Map<String, String> headers = new LinkedHashMap<>();
        String site = firstNonBlank(binding.getSiteCode(), "SA").toLowerCase(Locale.ROOT);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Country-Code", site);
        headers.put("Id-Partner", binding.getPartnerId());
        headers.put("X-Locale", "en-" + site);
        headers.put("X-Project", binding.getProjectCode());
        headers.put("X-Platform", "web");
        return headers;
    }

    private JsonNode firstDataNode(JsonNode response) {
        JsonNode data = arrayData(response);
        if (data.isArray() && data.size() > 0) {
            return data.get(0);
        }
        if (response != null && response.path("data").isObject()) {
            JsonNode objectData = response.path("data");
            JsonNode rows = objectData.path("rows");
            if (rows.isArray() && rows.size() > 0) {
                return rows.get(0);
            }
            return objectData;
        }
        return response == null ? objectMapper.createObjectNode() : response;
    }

    private JsonNode arrayData(JsonNode response) {
        if (response == null) {
            return objectMapper.createArrayNode();
        }
        if (response.isArray()) {
            return response;
        }
        JsonNode data = response.path("data");
        return data.isArray() ? data : objectMapper.createArrayNode();
    }

    private List<String> readStringArray(JsonNode response) {
        JsonNode data = arrayData(response);
        List<String> result = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode item : data) {
                String value = trimToNull(item.asText(null));
                if (value != null) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private void putPartnerId(ObjectNode body, String fieldName, String partnerId) {
        Long parsed = parseLongOrNull(partnerId);
        if (parsed == null) {
            body.put(fieldName, partnerId);
        } else {
            body.put(fieldName, parsed);
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return trimToNull(value.asText(null));
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static Integer intValue(JsonNode node, String fieldName) {
        Long value = longValue(node, fieldName);
        return value == null ? null : value.intValue();
    }

    private static Integer firstPositiveInt(JsonNode node, String... fieldNames) {
        if (fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            Integer value = intValue(node, fieldName);
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private static Long longValue(JsonNode node, String fieldName) {
        return parseLongOrNull(text(node, fieldName));
    }

    private static Long parseLongOrNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static class NoonCallContext {
        final String operation;
        final String businessType;
        final String businessId;
        final String businessRef;

        NoonCallContext(String operation, String businessType, String businessId, String businessRef) {
            this.operation = operation;
            this.businessType = businessType;
            this.businessId = businessId;
            this.businessRef = businessRef;
        }

        static NoonCallContext asn(Long asnId, String localAsnNo) {
            return new NoonCallContext(null, "OFFICIAL_WAREHOUSE_ASN", String.valueOf(asnId), localAsnNo);
        }

        static NoonCallContext appointment(String businessType, String businessId, String businessRef) {
            return new NoonCallContext(null, businessType, businessId, businessRef);
        }

        NoonCallContext withOperation(String operation) {
            return new NoonCallContext(operation, businessType, businessId, businessRef);
        }
    }
}
