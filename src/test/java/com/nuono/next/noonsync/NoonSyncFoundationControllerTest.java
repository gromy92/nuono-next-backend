package com.nuono.next.noonsync;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NoonSyncFoundationControllerTest {

    @Test
    void overviewExposesPlanAndTaskReadModelsWithoutSensitiveProviderPayloads() throws Exception {
        NoonSyncFoundationService service = new NoonSyncFoundationService();
        service.createPlan("sales_daily_sync", NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"));
        NoonSyncTask failed = service.markRunning(service.createTask(new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                NoonSyncTriggerMode.SCHEDULED_DAILY,
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 19)),
                NoonSyncRetryPolicy.RETRYABLE
        )));
        service.markFailed(
                failed.getId(),
                NoonSyncFailureReason.PROVIDER_UNAVAILABLE,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe("trace-api", "cookie=secret; Authorization: Bearer token; raw={sensitive}")
        );
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new NoonSyncFoundationController(service))
                .build();

        mvc.perform(get("/api/noon-sync-foundation/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCatalog[0].key").value("product_initialization"))
                .andExpect(jsonPath("$.plans[0].definition.key").value("sales_daily_sync"))
                .andExpect(jsonPath("$.tasks[0].failureReason").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.tasks[0].providerTraceId").value("trace-api"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("Bearer token"))))
                .andExpect(content().string(not(containsString("raw={sensitive}"))));
    }
}
