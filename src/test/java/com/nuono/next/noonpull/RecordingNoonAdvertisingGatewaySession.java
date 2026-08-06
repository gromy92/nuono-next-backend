package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonHttpException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Mutable one-shot gateway fixture for the DP-06 runtime provider contract tests. */
final class RecordingNoonAdvertisingGatewaySession implements NoonPullGatewaySession {
    private final ObjectMapper objectMapper = new ObjectMapper();
    int advertiserCalls;
    int campaignPageCalls;
    int queryCalls;
    boolean unknownStatus;
    boolean rateLimited;
    boolean omitPagination;
    boolean oversizedCampaignPayload;
    Map<String, String> lastHeaders;
    String lastJsonUrl;
    JsonNode lastJsonBody;

    @Override
    public JsonNode postJson(
            String url,
            JsonNode body,
            boolean withProject,
            Map<String, String> extraHeaders
    ) {
        campaignPageCalls++;
        lastHeaders = extraHeaders;
        lastJsonUrl = url;
        lastJsonBody = body;
        if (rateLimited) {
            throw new NoonHttpException(429, "too many requests", url);
        }
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode pagination = objectMapper.createObjectNode();
        pagination.put("nbHits", 3);
        pagination.put("nbPages", 1);
        if (!omitPagination) root.set("paginationMetadata", pagination);
        ObjectNode firstCampaign = campaign(
                "C-LIVE-NO-ACTIVITY",
                unknownStatus ? "mystery" : "live"
        );
        if (oversizedCampaignPayload) {
            firstCampaign.put("unexpected", "X".repeat(1_000_000));
        }
        root.set("campaigns", objectMapper.createArrayNode()
                .add(firstCampaign)
                .add(campaign("C-PAUSED", "paused"))
                .add(campaign("C-RUNNING", "running")));
        return root;
    }

    @Override
    public byte[] postBytes(
            String url,
            JsonNode body,
            boolean withProject,
            Map<String, String> extraHeaders
    ) {
        queryCalls++;
        lastHeaders = extraHeaders;
        return workbook();
    }

    @Override
    public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
        advertiserCalls++;
        lastHeaders = extraHeaders;
        return "[{\"idPartner\":108065,\"advertiserCode\":\"ADV_108065\"}]".getBytes();
    }

    private ObjectNode campaign(String code, String status) {
        ObjectNode campaign = objectMapper.createObjectNode();
        campaign.put("campaignCode", code);
        campaign.put("name", code + " name");
        campaign.put("effectiveStatus", status);
        campaign.set("metrics", metric("3.00"));
        return campaign;
    }

    private ObjectNode metric(String spend) {
        ObjectNode metric = objectMapper.createObjectNode();
        metric.put("views", 10);
        metric.put("clicks", 2);
        metric.put("orders", 1);
        metric.put("assistedOrders", 0);
        metric.put("atc", 1);
        metric.put("spends", spend);
        metric.put("revenue", "5.00");
        metric.put("ctr", "20");
        metric.put("roas", "1.6");
        metric.put("cpc", "1.5");
        metric.put("cps", "3");
        metric.put("cvr", "50");
        return metric;
    }

    private byte[] workbook() {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("(Product) Queries");
            Row header = sheet.createRow(0);
            List<String> headers = List.of(
                    "Campaign Name", "Sku", "Query", "Views", "Clicks", "Orders",
                    "Assisted Orders", "ATC", "Spends", "Revenue", "CTR", "ROAS",
                    "CPC", "CPS", "CVR"
            );
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }
            writeQueryRow(sheet.createRow(1), "paper towel");
            writeQueryRow(sheet.createRow(2), "");
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void writeQueryRow(Row row, String query) {
        List<Object> values = List.of(
                "Campaign", "ZSKU-1", query, 10, 2, 0, 0, 1,
                1.25, 3.5, 20, 2.8, 0.62, 0, 0
        );
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (value instanceof Number) {
                row.createCell(index).setCellValue(((Number) value).doubleValue());
            } else {
                row.createCell(index).setCellValue(String.valueOf(value));
            }
        }
    }
}
