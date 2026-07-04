package com.nuono.next.noonpull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonads.NoonAdvertisingReportProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
@ConditionalOnProperty(prefix = "nuono.noon.pull.real-provider", name = "enabled", havingValue = "true")
public class RealNoonAdvertisingReportProvider implements NoonAdvertisingReportProvider {
    private static final String READY_URL_PREFIX = "memory://noon-ads-report/";
    private static final String DEFAULT_ADMANAGER_BASE_URL = "https://admanager.noon.partners";
    private static final String[] CAMPAIGN_STATUSES = {
            "archived",
            "completed",
            "budget_exhausted",
            "draft",
            "paused",
            "live",
            "scheduled"
    };

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;
    private final String campaignMetricsUrl;
    private final String queryReportUrl;
    private final int campaignPageSize;
    private final int maxCampaignPages;
    private final int maxQueryCampaigns;

    public RealNoonAdvertisingReportProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            @Value("${nuono.noon.pull.real-provider.advertising-report.base-url:" + DEFAULT_ADMANAGER_BASE_URL + "}")
            String baseUrl,
            @Value("${nuono.noon.pull.real-provider.advertising-report.campaign-page-size:200}")
            int campaignPageSize,
            @Value("${nuono.noon.pull.real-provider.advertising-report.max-campaign-pages:50}")
            int maxCampaignPages,
            @Value("${nuono.noon.pull.real-provider.advertising-report.max-query-campaigns:0}")
            int maxQueryCampaigns
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
        String normalizedBaseUrl = trimTrailingSlash(
                StringUtils.hasText(baseUrl) ? baseUrl : DEFAULT_ADMANAGER_BASE_URL
        );
        this.campaignMetricsUrl = normalizedBaseUrl + "/_svc/productads/v2/noon/metrics/campaigns";
        this.queryReportUrl = normalizedBaseUrl + "/_svc/productads/v2/noon/product/reports/queries";
        this.campaignPageSize = Math.max(1, campaignPageSize);
        this.maxCampaignPages = Math.max(1, maxCampaignPages);
        this.maxQueryCampaigns = Math.max(0, maxQueryCampaigns);
    }

    @Override
    public String createExport(NoonReportPullRequest request) {
        try {
            requireDateWindow(request);
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            return "noon-ads:" + binding.getProjectCode() + ":" + request.getDateFrom() + ".." + request.getDateTo();
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map("noon ads report create " + safeRequestContext(request), exception);
        }
    }

    @Override
    public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
        try {
            requireDateWindow(request);
            return NoonReportExportStatus.ready(READY_URL_PREFIX + safeExportId(exportId));
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map("noon ads report status " + safeRequestContext(request), exception);
        }
    }

    @Override
    public byte[] download(NoonReportPullRequest request, String downloadUrl) {
        try {
            requireDateWindow(request);
            if (!StringUtils.hasText(downloadUrl) || !downloadUrl.startsWith(READY_URL_PREFIX)) {
                throw new NoonInterfacePullException("mapping failed: invalid Noon Ads report download url");
            }
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            NoonPullGatewaySession session = sessionFactory.login(binding);
            List<CampaignRow> campaigns = fetchCampaignRows(session, binding, request);
            List<QueryRow> queries = fetchQueryRows(session, binding, request, campaigns);
            return toCsv(binding, campaigns, queries);
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map("noon ads report download " + safeRequestContext(request), exception);
        }
    }

    private List<CampaignRow> fetchCampaignRows(
            NoonPullGatewaySession session,
            NoonPullStoreBinding binding,
            NoonReportPullRequest request
    ) {
        Map<String, CampaignRow> campaignsByCode = new LinkedHashMap<>();
        int pageNo = 1;
        while (pageNo <= maxCampaignPages) {
            JsonNode page = session.postJson(
                    campaignMetricsUrl,
                    campaignMetricsBody(binding, request, pageNo),
                    false,
                    admanagerHeaders(binding, "application/json, text/plain, */*")
            );
            String providerError = providerError(page);
            if (StringUtils.hasText(providerError)) {
                throw NoonPullProviderFailureMapper.explicit("noon ads campaign metrics", providerError);
            }
            Map<String, JsonNode> metricsByCode = metricsByCode(page.path("campaignMetrics"));
            for (JsonNode campaign : page.path("campaigns")) {
                String code = text(campaign, "campaignCode");
                if (!StringUtils.hasText(code)) {
                    continue;
                }
                campaignsByCode.put(code, campaignRow(campaign, metricsByCode.get(code)));
            }
            int nbPages = Math.max(1, page.path("paginationMetadata").path("nbPages").asInt(1));
            if (pageNo >= nbPages) {
                break;
            }
            if (pageNo >= maxCampaignPages) {
                throw new NoonInterfacePullException(
                        "provider unavailable: Noon Ads campaign metrics exceeded max pages " + maxCampaignPages
                );
            }
            pageNo++;
        }
        return campaignsByCode.values().stream()
                .sorted(Comparator.comparing(CampaignRow::spendAmount).reversed())
                .collect(Collectors.toList());
    }

    private List<QueryRow> fetchQueryRows(
            NoonPullGatewaySession session,
            NoonPullStoreBinding binding,
            NoonReportPullRequest request,
            List<CampaignRow> campaigns
    ) {
        List<QueryRow> rows = new ArrayList<>();
        int processedCampaigns = 0;
        for (CampaignRow campaign : campaigns) {
            if (!campaign.hasActivity()) {
                continue;
            }
            if (maxQueryCampaigns > 0 && processedCampaigns >= maxQueryCampaigns) {
                break;
            }
            processedCampaigns++;
            byte[] workbook = session.postBytes(
                    queryReportUrl,
                    queryReportBody(request, campaign.campaignCode()),
                    false,
                    admanagerHeaders(binding, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,*/*")
            );
            if (!looksLikeXlsx(workbook)) {
                throw new NoonInterfacePullException("mapping failed: Noon Ads query report did not return xlsx");
            }
            rows.addAll(parseQueryWorkbook(workbook, campaign));
        }
        return rows;
    }

    private ObjectNode campaignMetricsBody(NoonPullStoreBinding binding, NoonReportPullRequest request, int pageNo) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("campaignCodes", objectMapper.createArrayNode());
        ArrayNode campaignType = objectMapper.createArrayNode();
        campaignType.add("product");
        body.set("campaignType", campaignType);
        ArrayNode statuses = objectMapper.createArrayNode();
        for (String status : CAMPAIGN_STATUSES) {
            statuses.add(status);
        }
        body.set("campaignStatus", statuses);
        body.putNull("isAudience");
        ArrayNode pricingModel = objectMapper.createArrayNode();
        pricingModel.add("cpc");
        body.set("pricingModel", pricingModel);
        body.putNull("isGuaranteed");
        body.put("startDate", request.getDateFrom().toString());
        body.put("endDate", request.getDateTo().toString());
        ArrayNode marketplace = objectMapper.createArrayNode();
        marketplace.add(marketplaceId(binding.getSiteCode()));
        body.set("marketplace", marketplace);
        body.put("pageNo", pageNo);
        body.put("pageSize", campaignPageSize);
        return body;
    }

    private ObjectNode queryReportBody(NoonReportPullRequest request, String campaignCode) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("campaignCode", campaignCode);
        body.put("campaignType", "product");
        body.put("startDate", request.getDateFrom().toString());
        body.put("endDate", request.getDateTo().toString());
        return body;
    }

    private Map<String, String> admanagerHeaders(NoonPullStoreBinding binding, String accept) {
        String site = binding.getSiteCode() == null ? "ae" : binding.getSiteCode().toLowerCase(Locale.ROOT);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", accept);
        headers.put("X-Project", binding.getProjectCode());
        headers.put("x-content", "desktop");
        headers.put("x-locale", "en-" + site);
        headers.put("x-cms", "v3");
        headers.put("x-platform", "web");
        headers.put("x-mp", "noon");
        headers.put("x-border-enabled", "true");
        headers.put("x-seller-view", "owner");
        headers.put("x-id-advertiser", binding.getPartnerId());
        return headers;
    }

    private CampaignRow campaignRow(JsonNode campaign, JsonNode metrics) {
        JsonNode safeMetrics = metrics == null ? objectMapper.createObjectNode() : metrics;
        return new CampaignRow(
                text(campaign, "campaignCode"),
                firstText(campaign, "name", "campaignName"),
                text(campaign, "status"),
                text(campaign, "qcStatus"),
                text(campaign, "adgroupCode"),
                parseDate(text(campaign, "startDate")),
                parseDate(text(campaign, "endDate")),
                longValue(safeMetrics, "views"),
                longValue(safeMetrics, "clicks"),
                longValue(safeMetrics, "orders"),
                longValue(safeMetrics, "assistedOrders"),
                longValue(safeMetrics, "atc"),
                decimalValue(safeMetrics, "spends"),
                decimalValue(safeMetrics, "revenue"),
                percentFraction(safeMetrics, "ctr"),
                decimalValue(safeMetrics, "roas"),
                decimalValue(safeMetrics, "cpc"),
                decimalValue(safeMetrics, "cps"),
                percentFraction(safeMetrics, "cvr"),
                rawJson(campaign)
        );
    }

    private List<QueryRow> parseQueryWorkbook(byte[] content, CampaignRow campaign) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheet("(Product) Queries");
            if (sheet == null) {
                sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            }
            if (sheet == null || sheet.getPhysicalNumberOfRows() <= 1) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Map<String, Integer> headers = sheetHeaders(sheet.getRow(sheet.getFirstRowNum()), formatter);
            List<QueryRow> rows = new ArrayList<>();
            for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                QueryRow parsed = queryRow(row, formatter, headers, campaign);
                if (parsed.hasActivity()) {
                    rows.add(parsed);
                }
            }
            return rows;
        } catch (Exception exception) {
            throw new NoonInterfacePullException("mapping failed: unable to parse Noon Ads query xlsx", exception);
        }
    }

    private Map<String, Integer> sheetHeaders(Row row, DataFormatter formatter) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        if (row == null) {
            return headers;
        }
        for (int index = row.getFirstCellNum(); index < row.getLastCellNum(); index++) {
            String value = formatter.formatCellValue(row.getCell(index));
            if (StringUtils.hasText(value)) {
                headers.put(normalizeHeader(value), index);
            }
        }
        return headers;
    }

    private QueryRow queryRow(
            Row row,
            DataFormatter formatter,
            Map<String, Integer> headers,
            CampaignRow campaign
    ) {
        String query = sheetText(row, formatter, headers, "query");
        return new QueryRow(
                campaign.campaignCode(),
                firstNonBlank(sheetText(row, formatter, headers, "campaign_name"), campaign.campaignName()),
                sheetText(row, formatter, headers, "sku"),
                query,
                classifyQuery(query),
                longValue(sheetText(row, formatter, headers, "views")),
                longValue(sheetText(row, formatter, headers, "clicks")),
                longValue(sheetText(row, formatter, headers, "orders")),
                longValue(sheetText(row, formatter, headers, "assisted_orders")),
                longValue(sheetText(row, formatter, headers, "atc")),
                decimalValue(sheetText(row, formatter, headers, "spends")),
                decimalValue(sheetText(row, formatter, headers, "revenue")),
                percentFraction(sheetText(row, formatter, headers, "ctr")),
                decimalValue(sheetText(row, formatter, headers, "roas")),
                decimalValue(sheetText(row, formatter, headers, "cpc")),
                decimalValue(sheetText(row, formatter, headers, "cps")),
                percentFraction(sheetText(row, formatter, headers, "cvr"))
        );
    }

    private String sheetText(Row row, DataFormatter formatter, Map<String, Integer> headers, String key) {
        Integer index = headers.get(normalizeHeader(key));
        if (index == null || row == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(index)).trim();
    }

    private byte[] toCsv(NoonPullStoreBinding binding, List<CampaignRow> campaigns, List<QueryRow> queries) {
        Map<String, ZeroOrderSummary> zeroOrderByCampaign = summarizeZeroOrder(queries);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StringBuilder builder = new StringBuilder();
        writeRow(builder, List.of(
                "row_type",
                "project_code",
                "campaign_code",
                "campaign_name",
                "ad_sku_code",
                "partner_sku",
                "query_text",
                "query_kind",
                "campaign_status",
                "qc_status",
                "adgroup_code",
                "campaign_start_date",
                "campaign_end_date",
                "views",
                "clicks",
                "orders_count",
                "assisted_orders",
                "atc_count",
                "spend_amount",
                "ad_revenue",
                "ctr_percentage",
                "roas",
                "cpc",
                "cps",
                "cvr_percentage",
                "zero_order_spend_amount",
                "zero_order_spend_share",
                "raw_payload_json"
        ));
        for (CampaignRow campaign : campaigns) {
            ZeroOrderSummary zero = zeroOrderByCampaign.getOrDefault(campaign.campaignCode(), ZeroOrderSummary.empty());
            writeRow(builder, List.of(
                    "campaign",
                    binding.getProjectCode(),
                    campaign.campaignCode(),
                    campaign.campaignName(),
                    "",
                    "",
                    "",
                    "",
                    campaign.status(),
                    campaign.qcStatus(),
                    campaign.adgroupCode(),
                    text(campaign.startDate()),
                    text(campaign.endDate()),
                    text(campaign.views()),
                    text(campaign.clicks()),
                    text(campaign.orders()),
                    text(campaign.assistedOrders()),
                    text(campaign.atc()),
                    text(campaign.spendAmount()),
                    text(campaign.revenue()),
                    text(campaign.ctr()),
                    text(campaign.roas()),
                    text(campaign.cpc()),
                    text(campaign.cps()),
                    text(campaign.cvr()),
                    text(zero.spend()),
                    text(zero.share(campaign.spendAmount())),
                    campaign.rawJson()
            ));
        }
        for (QueryRow query : queries) {
            writeRow(builder, List.of(
                    "query",
                    binding.getProjectCode(),
                    query.campaignCode(),
                    query.campaignName(),
                    query.sku(),
                    "",
                    query.queryText(),
                    query.queryKind(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    text(query.views()),
                    text(query.clicks()),
                    text(query.orders()),
                    text(query.assistedOrders()),
                    text(query.atc()),
                    text(query.spend()),
                    text(query.revenue()),
                    text(query.ctr()),
                    text(query.roas()),
                    text(query.cpc()),
                    text(query.cps()),
                    text(query.cvr()),
                    "",
                    "",
                    ""
            ));
        }
        output.writeBytes(builder.toString().getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private Map<String, ZeroOrderSummary> summarizeZeroOrder(List<QueryRow> queries) {
        Map<String, ZeroOrderSummary> result = new LinkedHashMap<>();
        for (QueryRow query : queries) {
            if (query.spend().compareTo(BigDecimal.ZERO) > 0 && query.orders() <= 0L) {
                result.computeIfAbsent(query.campaignCode(), ignored -> ZeroOrderSummary.emptyMutable())
                        .add(query.spend());
            }
        }
        return result;
    }

    private Map<String, JsonNode> metricsByCode(JsonNode metricsNode) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        if (metricsNode != null && metricsNode.isObject()) {
            metricsNode.fields().forEachRemaining((entry) -> result.put(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private String providerError(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "provider unavailable: empty Noon Ads provider response";
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull() && StringUtils.hasText(error.asText())) {
            return error.asText();
        }
        return null;
    }

    private void requireDateWindow(NoonReportPullRequest request) {
        if (request == null || request.getDateFrom() == null || request.getDateTo() == null) {
            throw new IllegalArgumentException("missing Noon Ads report date window");
        }
        if (request.getDateTo().isBefore(request.getDateFrom())) {
            throw new IllegalArgumentException("invalid Noon Ads report date window");
        }
    }

    private int marketplaceId(String siteCode) {
        String normalized = siteCode == null ? "" : siteCode.trim().toUpperCase(Locale.ROOT);
        if ("SA".equals(normalized)) {
            return 1;
        }
        if ("AE".equals(normalized)) {
            return 2;
        }
        if ("EG".equals(normalized)) {
            return 3;
        }
        throw new NoonInterfacePullException("provider not configured: unsupported Noon Ads site code " + siteCode);
    }

    private boolean looksLikeXlsx(byte[] content) {
        return content != null && content.length >= 2 && content[0] == 'P' && content[1] == 'K';
    }

    private String classifyQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return "missing_query";
        }
        String normalized = query.trim();
        if (normalized.contains("/") || normalized.startsWith("supermall-")
                || "CART".equals(normalized) || "SEARCH".equals(normalized) || "PDP".equals(normalized)) {
            return "surface_or_category";
        }
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            if (codePoint >= 0x0600 && codePoint <= 0x06ff) {
                return "arabic_search_term";
            }
            offset += Character.charCount(codePoint);
        }
        return "search_term";
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private long longValue(JsonNode node, String field) {
        return longValue(text(node, field));
    }

    private long longValue(String value) {
        BigDecimal parsed = decimalValue(value);
        return parsed == null ? 0L : parsed.setScale(0, RoundingMode.DOWN).longValue();
    }

    private BigDecimal decimalValue(JsonNode node, String field) {
        return decimalValue(text(node, field));
    }

    private BigDecimal decimalValue(String value) {
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        String cleaned = value.trim().replace(",", "").replace("%", "");
        if (!StringUtils.hasText(cleaned) || "-".equals(cleaned)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal percentFraction(JsonNode node, String field) {
        return percentFraction(text(node, field));
    }

    private BigDecimal percentFraction(String value) {
        return decimalValue(value).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String rawJson(JsonNode value) {
        try {
            return value == null ? "" : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private String normalizeHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String camelSeparated = value.trim().replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        return camelSeparated.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : (StringUtils.hasText(second) ? second : "");
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String safeExportId(String value) {
        return StringUtils.hasText(value) ? value.trim() : "unknown";
    }

    private String safeRequestContext(NoonReportPullRequest request) {
        if (request == null) {
            return "ownerUserId=null storeCode=null siteCode=null";
        }
        return "ownerUserId=" + request.getOwnerUserId()
                + " storeCode=" + request.getStoreCode()
                + " siteCode=" + request.getSiteCode();
    }

    private void writeRow(StringBuilder builder, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csv(fields.get(i)));
        }
        builder.append('\n');
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static final class CampaignRow {
        private final String campaignCode;
        private final String campaignName;
        private final String status;
        private final String qcStatus;
        private final String adgroupCode;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final long views;
        private final long clicks;
        private final long orders;
        private final long assistedOrders;
        private final long atc;
        private final BigDecimal spendAmount;
        private final BigDecimal revenue;
        private final BigDecimal ctr;
        private final BigDecimal roas;
        private final BigDecimal cpc;
        private final BigDecimal cps;
        private final BigDecimal cvr;
        private final String rawJson;

        private CampaignRow(
                String campaignCode,
                String campaignName,
                String status,
                String qcStatus,
                String adgroupCode,
                LocalDate startDate,
                LocalDate endDate,
                long views,
                long clicks,
                long orders,
                long assistedOrders,
                long atc,
                BigDecimal spendAmount,
                BigDecimal revenue,
                BigDecimal ctr,
                BigDecimal roas,
                BigDecimal cpc,
                BigDecimal cps,
                BigDecimal cvr,
                String rawJson
        ) {
            this.campaignCode = campaignCode;
            this.campaignName = campaignName;
            this.status = status;
            this.qcStatus = qcStatus;
            this.adgroupCode = adgroupCode;
            this.startDate = startDate;
            this.endDate = endDate;
            this.views = views;
            this.clicks = clicks;
            this.orders = orders;
            this.assistedOrders = assistedOrders;
            this.atc = atc;
            this.spendAmount = spendAmount;
            this.revenue = revenue;
            this.ctr = ctr;
            this.roas = roas;
            this.cpc = cpc;
            this.cps = cps;
            this.cvr = cvr;
            this.rawJson = rawJson;
        }

        private String campaignCode() { return campaignCode; }
        private String campaignName() { return campaignName; }
        private String status() { return status; }
        private String qcStatus() { return qcStatus; }
        private String adgroupCode() { return adgroupCode; }
        private LocalDate startDate() { return startDate; }
        private LocalDate endDate() { return endDate; }
        private long views() { return views; }
        private long clicks() { return clicks; }
        private long orders() { return orders; }
        private long assistedOrders() { return assistedOrders; }
        private long atc() { return atc; }
        private BigDecimal spendAmount() { return spendAmount; }
        private BigDecimal revenue() { return revenue; }
        private BigDecimal ctr() { return ctr; }
        private BigDecimal roas() { return roas; }
        private BigDecimal cpc() { return cpc; }
        private BigDecimal cps() { return cps; }
        private BigDecimal cvr() { return cvr; }
        private String rawJson() { return rawJson; }

        private boolean hasActivity() {
            return spendAmount.compareTo(BigDecimal.ZERO) > 0 || clicks > 0 || orders > 0;
        }
    }

    private static final class QueryRow {
        private final String campaignCode;
        private final String campaignName;
        private final String sku;
        private final String queryText;
        private final String queryKind;
        private final long views;
        private final long clicks;
        private final long orders;
        private final long assistedOrders;
        private final long atc;
        private final BigDecimal spend;
        private final BigDecimal revenue;
        private final BigDecimal ctr;
        private final BigDecimal roas;
        private final BigDecimal cpc;
        private final BigDecimal cps;
        private final BigDecimal cvr;

        private QueryRow(
                String campaignCode,
                String campaignName,
                String sku,
                String queryText,
                String queryKind,
                long views,
                long clicks,
                long orders,
                long assistedOrders,
                long atc,
                BigDecimal spend,
                BigDecimal revenue,
                BigDecimal ctr,
                BigDecimal roas,
                BigDecimal cpc,
                BigDecimal cps,
                BigDecimal cvr
        ) {
            this.campaignCode = campaignCode;
            this.campaignName = campaignName;
            this.sku = sku;
            this.queryText = queryText;
            this.queryKind = queryKind;
            this.views = views;
            this.clicks = clicks;
            this.orders = orders;
            this.assistedOrders = assistedOrders;
            this.atc = atc;
            this.spend = spend;
            this.revenue = revenue;
            this.ctr = ctr;
            this.roas = roas;
            this.cpc = cpc;
            this.cps = cps;
            this.cvr = cvr;
        }

        private String campaignCode() { return campaignCode; }
        private String campaignName() { return campaignName; }
        private String sku() { return sku; }
        private String queryText() { return queryText; }
        private String queryKind() { return queryKind; }
        private long views() { return views; }
        private long clicks() { return clicks; }
        private long orders() { return orders; }
        private long assistedOrders() { return assistedOrders; }
        private long atc() { return atc; }
        private BigDecimal spend() { return spend; }
        private BigDecimal revenue() { return revenue; }
        private BigDecimal ctr() { return ctr; }
        private BigDecimal roas() { return roas; }
        private BigDecimal cpc() { return cpc; }
        private BigDecimal cps() { return cps; }
        private BigDecimal cvr() { return cvr; }

        private boolean hasActivity() {
            return spend.compareTo(BigDecimal.ZERO) > 0 || clicks > 0 || orders > 0 || revenue.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    private static final class ZeroOrderSummary {
        private BigDecimal spend = BigDecimal.ZERO;

        private static ZeroOrderSummary empty() {
            return new ZeroOrderSummary();
        }

        private static ZeroOrderSummary emptyMutable() {
            return new ZeroOrderSummary();
        }

        private void add(BigDecimal value) {
            spend = spend.add(value == null ? BigDecimal.ZERO : value);
        }

        private BigDecimal spend() {
            return spend;
        }

        private BigDecimal share(BigDecimal campaignSpend) {
            if (campaignSpend == null || campaignSpend.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            return spend.divide(campaignSpend, 8, RoundingMode.HALF_UP).stripTrailingZeros();
        }
    }
}
