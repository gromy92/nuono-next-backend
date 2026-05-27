package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonProductViewsSalesReportExporterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsExportPollsUntilCompleteAndDownloadsCsv() throws Exception {
        RecordingSession session = new RecordingSession(objectMapper);
        NoonProductViewsSalesReportExporter exporter = new NoonProductViewsSalesReportExporter(
                objectMapper,
                binding -> session,
                "https://noon-catalog.noon.partners/_svc/mp-partner-impex-api/export/create",
                "https://noon-catalog.noon.partners/_svc/mp-partner-impex-api/export/status",
                3,
                0
        );

        NoonSalesReportPayload payload = exporter.export(
                binding(),
                LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 5, 19)
        );

        assertEquals("csv-from-noon", payload.getCsv());
        assertEquals("noon_catalog_reports_productviewsandsalesdata-EXP-1.csv", payload.getSourceFilename());
        assertEquals(2, session.posts.size());
        assertEquals("noon_catalog_reports_productviewsandsalesdata", session.posts.get(0).body.get("exportCategoryCode").asText());
        JsonNode params = objectMapper.readTree(session.posts.get(0).body.get("params").asText());
        assertEquals("245027", params.get("id_partner").asText());
        assertEquals("en", params.get("lang").asText());
        assertEquals("sa", params.get("country").asText());
        assertEquals("2026-05-19", params.get("from_date").asText());
        assertEquals("2026-05-19", params.get("to_date").asText());
        assertEquals("EXP-1", session.posts.get(1).body.get("exportCode").asText());
        assertEquals("https://download.example/report.csv", session.downloadUrl);
    }

    private static NoonSalesReportBinding binding() {
        return new NoonSalesReportBinding(
                10002L,
                245027L,
                "PRJ245027",
                "STR245027-NSA",
                "SA",
                "245027",
                "project-user@example.com",
                "secret",
                "session=already-present"
        );
    }

    private static class RecordingSession implements NoonSalesReportSession {
        private final ObjectMapper objectMapper;
        private final List<PostCall> posts = new ArrayList<>();
        private String downloadUrl;

        private RecordingSession(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            posts.add(new PostCall(url, body));
            if (url.endsWith("/export/create")) {
                return objectMapper.createObjectNode().put("export", "EXP-1");
            }
            return objectMapper.createObjectNode()
                    .set("export", objectMapper.createObjectNode()
                            .put("status_code", "COMPLETE")
                            .put("result", "{\"total_rows\":1}")
                            .put("download_url", "https://download.example/report.csv"));
        }

        @Override
        public String getText(String url, boolean withProject, Map<String, String> extraHeaders) {
            downloadUrl = url;
            return "csv-from-noon";
        }
    }

    private static class PostCall {
        private final String url;
        private final JsonNode body;

        private PostCall(String url, JsonNode body) {
            this.url = url;
            this.body = body;
        }
    }
}
