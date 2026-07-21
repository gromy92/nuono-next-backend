package com.nuono.next.postsaleprofit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PostSaleProfitHttpAdapterMappingTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PostSaleProfitReadService readService = mock(PostSaleProfitReadService.class);
        PostSaleProfitRecalculationService recalculationService = mock(PostSaleProfitRecalculationService.class);
        PostSaleProfitAttributionAdjustmentService attributionService =
                mock(PostSaleProfitAttributionAdjustmentService.class);
        PostSaleProfitFxRateService fxRateService = mock(PostSaleProfitFxRateService.class);
        BusinessAccessResolver accessResolver = mock(BusinessAccessResolver.class);
        when(accessResolver.requireStoreAccess(
                any(HttpServletRequest.class),
                eq(BusinessCapability.SALES_DATA),
                eq("STR108065-NSA")
        )).thenReturn(accessContext());
        PostSaleProfitHttpSupport support = new PostSaleProfitHttpSupport(accessResolver);

        mockMvc = MockMvcBuilders.standaloneSetup(
                new PostSaleProfitQueryController(readService, fxRateService, support),
                new PostSaleProfitCommandController(
                        recalculationService,
                        attributionService,
                        fxRateService,
                        support
                )
        ).build();
    }

    @Test
    void allEightExistingMappingsRemainUniqueAndReachable() throws Exception {
        mockMvc.perform(get("/api/post-sale-profit/latest-run")
                        .param("storeCode", "STR108065-NSA").param("siteCode", "SA"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/post-sale-profit/batches")
                        .param("storeCode", "STR108065-NSA").param("siteCode", "SA")
                        .param("dateFrom", "2026-05-01").param("dateTo", "2026-05-31"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/post-sale-profit/batch-detail")
                        .param("storeCode", "STR108065-NSA").param("siteCode", "SA")
                        .param("batchId", "700"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/post-sale-profit/fx-rates")
                        .param("storeCode", "STR108065-NSA").param("siteCode", "SA"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/post-sale-profit/recalculate-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recalculationJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/post-sale-profit/batch-attributions/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lockJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/post-sale-profit/batch-attributions/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/post-sale-profit/fx-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fxRateJson()))
                .andExpect(status().isOk());
    }

    @Test
    void adaptersOnlyDependOnTheirSideOfTheProtocol() {
        assertThat(PostSaleProfitQueryController.class.getDeclaredFields())
                .extracting(field -> field.getType().getSimpleName())
                .containsExactlyInAnyOrder(
                        "PostSaleProfitReadService",
                        "PostSaleProfitFxRateService",
                        "PostSaleProfitHttpSupport"
                );
        assertThat(PostSaleProfitCommandController.class.getDeclaredFields())
                .extracting(field -> field.getType().getSimpleName())
                .containsExactlyInAnyOrder(
                        "PostSaleProfitRecalculationService",
                        "PostSaleProfitAttributionAdjustmentService",
                        "PostSaleProfitFxRateService",
                        "PostSaleProfitHttpSupport"
                );
    }

    private String recalculationJson() {
        return "{\"storeCode\":\"STR108065-NSA\",\"siteCode\":\"SA\","
                + "\"dateFrom\":\"2026-05-01\",\"dateTo\":\"2026-05-31\"}";
    }

    private String lockJson() {
        return "{\"storeCode\":\"STR108065-NSA\",\"siteCode\":\"SA\","
                + "\"batchId\":700,\"locked\":true}";
    }

    private String moveJson() {
        return "{\"storeCode\":\"STR108065-NSA\",\"siteCode\":\"SA\","
                + "\"sourceBatchId\":700,\"targetBatchId\":701,\"quantity\":1}";
    }

    private String fxRateJson() {
        return "{\"storeCode\":\"STR108065-NSA\",\"siteCode\":\"SA\","
                + "\"currency\":\"SAR\",\"rateToCny\":1.8833,\"effectiveFrom\":\"2026-05-01\"}";
    }

    private static BusinessAccessContext accessContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(501L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/api/post-sale-profit"))
                .build();
    }
}
