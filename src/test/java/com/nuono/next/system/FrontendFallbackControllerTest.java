package com.nuono.next.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FrontendFallbackControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new FrontendFallbackController())
            .build();

    @Test
    void shouldForwardSystemReportFrontendRoutesToIndex() throws Exception {
        mockMvc.perform(get("/system-reports/store-data"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/system-reports/noon-data-completeness"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/system-reports/noon-data-gaps"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void shouldForwardDataWorkspaceFrontendRoutesToIndex() throws Exception {
        mockMvc.perform(get("/data/sales-analysis"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/data/sales-forecast"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
