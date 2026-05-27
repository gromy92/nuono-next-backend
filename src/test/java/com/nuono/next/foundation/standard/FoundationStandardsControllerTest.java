package com.nuono.next.foundation.standard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FoundationStandardsControllerTest {

    @Test
    void overviewExposesFoundationStandardsForReviewAndReuse() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new FoundationStandardsController(new FoundationStandardsService()))
                .build();

        mvc.perform(get("/api/foundation-standards/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facts[0].key").value("daily_sales_fact"))
                .andExpect(jsonPath("$.metrics[0].key").value("sales_net_units"))
                .andExpect(jsonPath("$.aiOutputModes[0].key").value("file_document_parse"))
                .andExpect(jsonPath("$.adapterStandard.errorCodes[5]").value("challenge_page"))
                .andExpect(jsonPath("$.workflowStandard.statusKeys[0]").value("queued"));
    }
}
