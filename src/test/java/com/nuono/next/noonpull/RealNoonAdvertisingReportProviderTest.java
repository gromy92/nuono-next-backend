package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingImportRepository;
import com.nuono.next.noonads.NoonAdvertisingImportService;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import com.nuono.next.noonads.NoonAdvertisingReportAdapter;
import com.nuono.next.noonads.NoonAdvertisingReportBatch;
import com.nuono.next.noonads.NoonAdvertisingReportDescriptor;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class RealNoonAdvertisingReportProviderTest {

    @Test
    void downloadsAdmanagerCampaignAndQueryReportsAsUnifiedCsv() throws Exception {
        NoonPullStoreBinding binding = new NoonPullStoreBinding(
                10002L,
                "PRJ69486",
                "STR69486-NSA",
                "SA",
                "69486",
                "seller@example.com",
                "secret",
                "cookie=value"
        );
        RecordingGatewaySession session = new RecordingGatewaySession();
        RealNoonAdvertisingReportProvider provider = new RealNoonAdvertisingReportProvider(
                new ObjectMapper(),
                new StaticBindingResolver(binding),
                ignored -> session,
                "https://admanager.noon.partners",
                200,
                5,
                0
        );
        NoonReportPullRequest request = NoonReportPullRequest.builder()
                .ownerUserId(10002L)
                .storeCode("STR69486-NSA")
                .siteCode("SA")
                .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                .reportType(NoonAdvertisingReportDescriptor.DEFAULT_REPORT_TYPE)
                .dateFrom(LocalDate.of(2026, 7, 3))
                .dateTo(LocalDate.of(2026, 7, 3))
                .build();

        String exportId = provider.createExport(request);
        NoonReportExportStatus status = provider.pollExport(request, exportId);
        byte[] csv = provider.download(request, status.getDownloadUrl());

        RecordingImportRepository repository = new RecordingImportRepository();
        NoonReportProcessResult result = new NoonAdvertisingReportAdapter(new NoonAdvertisingImportService(repository))
                .process(new NoonReportDownloadedFile(request, exportId, "ads-source-batch", "digest", csv));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED, result.getCode());
        assertEquals(2, result.getImportedCount());
        assertEquals(1, session.campaignMetricsCalls);
        assertEquals(1, session.queryReportCalls);
        assertEquals("PRJ69486", session.lastHeaders.get("X-Project"));
        assertEquals("69486", session.lastHeaders.get("x-id-advertiser"));
        assertTrue(session.lastCampaignMetricsBody.path("marketplace").toString().contains("1"));

        NoonAdvertisingCampaignFact campaign = repository.campaignFacts.get(0);
        assertEquals("C_U9Q0F611VL", campaign.getCampaignCode());
        assertEquals("ZD-一个黑色射灯", campaign.getCampaignName());
        assertEquals("ADG_732M6B3ETP", campaign.getAdgroupCode());
        assertEquals(new BigDecimal("0.0274"), campaign.getCtrPercentage());
        assertEquals(new BigDecimal("0.0761"), campaign.getCvrPercentage());
        assertEquals(new BigDecimal("10.92"), campaign.getZeroOrderSpendAmount());

        NoonAdvertisingQueryFact query = repository.queryFacts.get(0);
        assertEquals("C_U9Q0F611VL", query.getCampaignCode());
        assertEquals("ZBA4C707B499F0C0F5468Z-1", query.getAdSkuCode());
        assertEquals("اضاءة شجر", query.getQueryText());
        assertEquals("arabic_search_term", query.getQueryKind());
        assertEquals(new BigDecimal("0.3171"), query.getCtrPercentage());
    }

    private static class StaticBindingResolver extends NoonPullStoreBindingResolver {
        private final NoonPullStoreBinding binding;

        private StaticBindingResolver(NoonPullStoreBinding binding) {
            super(null);
            this.binding = binding;
        }

        @Override
        public NoonPullStoreBinding resolve(NoonReportPullRequest request) {
            return binding;
        }
    }

    private static class RecordingGatewaySession implements NoonPullGatewaySession {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private int campaignMetricsCalls;
        private int queryReportCalls;
        private JsonNode lastCampaignMetricsBody;
        private Map<String, String> lastHeaders;

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            campaignMetricsCalls++;
            lastCampaignMetricsBody = body;
            lastHeaders = extraHeaders;
            ObjectNode root = objectMapper.createObjectNode();
            root.set("campaigns", objectMapper.createArrayNode().add(campaign()));
            ObjectNode metrics = objectMapper.createObjectNode();
            ObjectNode campaignMetrics = objectMapper.createObjectNode();
            campaignMetrics.put("views", 19707);
            campaignMetrics.put("clicks", 539);
            campaignMetrics.put("orders", 41);
            campaignMetrics.put("assistedOrders", 0);
            campaignMetrics.put("atc", 166);
            campaignMetrics.put("spends", "190.68");
            campaignMetrics.put("revenue", "1570.15");
            campaignMetrics.put("ctr", "2.74");
            campaignMetrics.put("roas", "8.23");
            campaignMetrics.put("cpc", "0.35");
            campaignMetrics.put("cps", "4.65");
            campaignMetrics.put("cvr", "7.61");
            metrics.set("C_U9Q0F611VL", campaignMetrics);
            root.set("campaignMetrics", metrics);
            ObjectNode pagination = objectMapper.createObjectNode();
            pagination.put("nbPages", 1);
            root.set("paginationMetadata", pagination);
            return root;
        }

        @Override
        public byte[] postBytes(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            queryReportCalls++;
            lastHeaders = extraHeaders;
            assertEquals("C_U9Q0F611VL", body.path("campaignCode").asText());
            return queryWorkbook();
        }

        @Override
        public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
            throw new UnsupportedOperationException();
        }

        private ObjectNode campaign() {
            ObjectNode campaign = objectMapper.createObjectNode();
            campaign.put("campaignCode", "C_U9Q0F611VL");
            campaign.put("name", "ZD-一个黑色射灯");
            campaign.put("status", "live");
            campaign.put("qcStatus", "live");
            campaign.put("adgroupCode", "ADG_732M6B3ETP");
            campaign.put("startDate", "2025-10-31");
            campaign.put("endDate", "2099-12-31");
            return campaign;
        }

        private byte[] queryWorkbook() {
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("(Product) Queries");
                Row header = sheet.createRow(0);
                List<String> headers = List.of(
                        "Campaign Name",
                        "Sku",
                        "Query",
                        "Views",
                        "Clicks",
                        "Orders",
                        "Assisted Orders",
                        "ATC",
                        "Spends",
                        "Revenue",
                        "CTR",
                        "ROAS",
                        "CPC",
                        "CPS",
                        "CVR"
                );
                for (int index = 0; index < headers.size(); index++) {
                    header.createCell(index).setCellValue(headers.get(index));
                }
                Row row = sheet.createRow(1);
                List<Object> values = List.of(
                        "ZD-一个黑色射灯",
                        "ZBA4C707B499F0C0F5468Z-1",
                        "اضاءة شجر",
                        82,
                        26,
                        0,
                        0,
                        12,
                        10.92,
                        163.25,
                        31.71,
                        14.95,
                        0.42,
                        2.73,
                        0
                );
                for (int index = 0; index < values.size(); index++) {
                    Object value = values.get(index);
                    if (value instanceof Number) {
                        row.createCell(index).setCellValue(((Number) value).doubleValue());
                    } else {
                        row.createCell(index).setCellValue(String.valueOf(value));
                    }
                }
                workbook.write(output);
                return output.toByteArray();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static class RecordingImportRepository implements NoonAdvertisingImportRepository {
        private final List<NoonAdvertisingReportBatch> insertedBatches = new ArrayList<>();
        private final List<NoonAdvertisingCampaignFact> campaignFacts = new ArrayList<>();
        private final List<NoonAdvertisingQueryFact> queryFacts = new ArrayList<>();

        @Override
        public Long nextReportBatchId() {
            return 200001L + insertedBatches.size();
        }

        @Override
        public Long nextCampaignFactId() {
            return 210001L + campaignFacts.size();
        }

        @Override
        public Long nextQueryFactId() {
            return 220001L + queryFacts.size();
        }

        @Override
        public Long findReportBatchId(NoonAdvertisingReportBatch batch) {
            return null;
        }

        @Override
        public void insertReportBatch(NoonAdvertisingReportBatch batch) {
            insertedBatches.add(batch);
        }

        @Override
        public void upsertCampaignFact(NoonAdvertisingCampaignFact fact) {
            campaignFacts.add(fact);
        }

        @Override
        public void upsertQueryFact(NoonAdvertisingQueryFact fact) {
            queryFacts.add(fact);
        }
    }
}
