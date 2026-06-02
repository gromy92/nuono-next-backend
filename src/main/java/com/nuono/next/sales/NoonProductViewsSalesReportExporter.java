package com.nuono.next.sales;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "nuono.sales.noon.report-provider", name = "enabled", havingValue = "true")
public class NoonProductViewsSalesReportExporter {

    static final String EXPORT_CATEGORY_CODE = "noon_catalog_reports_productviewsandsalesdata";

    private static final Set<String> FAILED_STATUS_CODES = Set.of(
            "FAILED",
            "FAILURE",
            "ERROR",
            "CANCELLED",
            "CANCELED"
    );

    private final ObjectMapper objectMapper;
    private final NoonSalesReportSessionFactory sessionFactory;
    private final String createUrl;
    private final String statusUrl;
    private final int maxStatusPolls;
    private final long statusPollIntervalMillis;

    public NoonProductViewsSalesReportExporter(
            ObjectMapper objectMapper,
            NoonSalesReportSessionFactory sessionFactory,
            @Value("${nuono.sales.noon.report-provider.export-create-url:https://noon-catalog.noon.partners/_svc/mp-partner-impex-api/export/create}") String createUrl,
            @Value("${nuono.sales.noon.report-provider.export-status-url:https://noon-catalog.noon.partners/_svc/mp-partner-impex-api/export/status}") String statusUrl,
            @Value("${nuono.sales.noon.report-provider.max-status-polls:60}") int maxStatusPolls,
            @Value("${nuono.sales.noon.report-provider.status-poll-interval-millis:3000}") long statusPollIntervalMillis
    ) {
        this.objectMapper = objectMapper;
        this.sessionFactory = sessionFactory;
        this.createUrl = createUrl;
        this.statusUrl = statusUrl;
        this.maxStatusPolls = Math.max(1, maxStatusPolls);
        this.statusPollIntervalMillis = Math.max(0L, statusPollIntervalMillis);
    }

    public NoonSalesReportPayload export(NoonSalesReportBinding binding, LocalDate dateFrom, LocalDate dateTo) {
        NoonSalesReportExportStatus created = createExport(binding, dateFrom, dateTo);
        NoonSalesReportExportStatus status = waitUntilComplete(binding, created.getExportCode());
        return download(binding, status);
    }

    public NoonSalesReportExportStatus createExport(
            NoonSalesReportBinding binding,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        requireBinding(binding);
        if (dateFrom == null || dateTo == null || dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Noon 销量报表日期范围无效。");
        }
        NoonSalesReportSession session = sessionFactory.login(binding);
        String exportCode = createExportCode(session, binding, dateFrom, dateTo);
        return NoonSalesReportExportStatus.pending(exportCode, "CREATED");
    }

    public NoonSalesReportExportStatus pollExport(NoonSalesReportBinding binding, String exportCode) {
        requireBinding(binding);
        if (!StringUtils.hasText(exportCode)) {
            throw new IllegalArgumentException("Noon 销量报表同步缺少 exportCode。");
        }
        NoonSalesReportSession session = sessionFactory.login(binding);
        return pollOnce(session, binding, exportCode);
    }

    public NoonSalesReportPayload download(NoonSalesReportBinding binding, NoonSalesReportExportStatus status) {
        requireBinding(binding);
        if (status == null || !StringUtils.hasText(status.getExportCode())) {
            throw new IllegalArgumentException("Noon 销量报表下载缺少 exportCode。");
        }
        String filename = EXPORT_CATEGORY_CODE + "-" + status.getExportCode() + ".csv";
        if (status.getTotalRows() <= 0 && !StringUtils.hasText(status.getDownloadUrl())) {
            return new NoonSalesReportPayload(filename, "");
        }
        if (!StringUtils.hasText(status.getDownloadUrl())) {
            throw new IllegalStateException("Noon 销量报表已完成但缺少下载链接。");
        }
        NoonSalesReportSession session = sessionFactory.login(binding);
        String csv = session.getText(status.getDownloadUrl(), false, Map.of("Accept", "text/csv,*/*"));
        return new NoonSalesReportPayload(filename, csv);
    }

    public int maxPollAttempts() {
        return maxStatusPolls;
    }

    public void waitBeforeNextPoll() {
        sleepBeforeNextPoll();
    }

    private String createExportCode(
            NoonSalesReportSession session,
            NoonSalesReportBinding binding,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("id_partner", binding.getPartnerId());
        params.put("lang", "en");
        params.put("country", binding.getSiteCode().toLowerCase(Locale.ROOT));
        params.put("from_date", dateFrom.toString());
        params.put("to_date", dateTo.toString());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("channelCode", "web");
        body.put("exportCategoryCode", EXPORT_CATEGORY_CODE);
        try {
            body.put("params", objectMapper.writeValueAsString(params));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("构造 Noon 销量报表导出参数失败：" + exception.getMessage(), exception);
        }

        JsonNode root = session.postJson(createUrl, body, true, localeHeaders(binding));
        String exportCode = text(root, "export");
        if (!StringUtils.hasText(exportCode)) {
            throw new IllegalStateException("Noon 销量报表导出创建失败：缺少 exportCode。");
        }
        return exportCode;
    }

    private NoonSalesReportExportStatus waitUntilComplete(
            NoonSalesReportBinding binding,
            String exportCode
    ) {
        NoonSalesReportSession session = sessionFactory.login(binding);
        for (int attempt = 1; attempt <= maxStatusPolls; attempt++) {
            NoonSalesReportExportStatus status = pollOnce(session, binding, exportCode);
            if (status.isComplete()) {
                return status;
            }
            if (status.isFailed()) {
                throw new IllegalStateException("Noon 销量报表导出失败：" + status.getStatus());
            }
            if (attempt < maxStatusPolls) {
                sleepBeforeNextPoll();
            }
        }
        throw new IllegalStateException("Noon 销量报表导出超时，未在轮询次数内完成。");
    }

    private NoonSalesReportExportStatus pollOnce(
            NoonSalesReportSession session,
            NoonSalesReportBinding binding,
            String exportCode
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("exportCode", exportCode);
        body.put("log", false);

        JsonNode root = session.postJson(statusUrl, body, true, localeHeaders(binding));
        JsonNode exportNode = root.path("export");
        String statusCode = text(exportNode, "status_code");
        if ("COMPLETE".equalsIgnoreCase(statusCode) || "COMPLETED".equalsIgnoreCase(statusCode)) {
            return NoonSalesReportExportStatus.complete(
                    exportCode,
                    text(exportNode, "download_url"),
                    totalRows(exportNode.path("result"))
            );
        }
        if (FAILED_STATUS_CODES.contains(statusCode == null ? "" : statusCode.toUpperCase(Locale.ROOT))) {
            return NoonSalesReportExportStatus.failed(exportCode, statusCode, "Noon 销量报表导出失败：" + statusCode);
        }
        return NoonSalesReportExportStatus.pending(exportCode, statusCode);
    }

    private int totalRows(JsonNode resultNode) {
        if (resultNode == null || resultNode.isMissingNode() || resultNode.isNull()) {
            return 0;
        }
        if (resultNode.isObject()) {
            return resultNode.path("total_rows").asInt(0);
        }
        if (!StringUtils.hasText(resultNode.asText())) {
            return 0;
        }
        try {
            return objectMapper.readTree(resultNode.asText()).path("total_rows").asInt(0);
        } catch (JsonProcessingException exception) {
            return 0;
        }
    }

    private void sleepBeforeNextPoll() {
        if (statusPollIntervalMillis <= 0L) {
            return;
        }
        try {
            Thread.sleep(statusPollIntervalMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Noon 报表导出时被中断：" + exception.getMessage(), exception);
        }
    }

    private static Map<String, String> localeHeaders(NoonSalesReportBinding binding) {
        return Map.of("X-Locale", "en-" + binding.getSiteCode().toLowerCase(Locale.ROOT), "X-Lang", "en");
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            return null;
        }
        String value = node.path(fieldName).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static void requireBinding(NoonSalesReportBinding binding) {
        if (binding == null
                || !StringUtils.hasText(binding.getProjectCode())
                || !StringUtils.hasText(binding.getStoreCode())
                || !StringUtils.hasText(binding.getSiteCode())
                || !StringUtils.hasText(binding.getPartnerId())) {
            throw new IllegalArgumentException("Noon 销量报表同步缺少有效店铺绑定。");
        }
    }

}
