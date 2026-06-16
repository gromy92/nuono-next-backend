package com.nuono.next.productpublicdetail;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nuono.next.auth.AuthApiProtectionProperties;
import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductPublicDetailController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductPublicDetailApiSmokeTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductPublicDetailSyncService syncService;

    @MockBean
    private BusinessAccessResolver businessAccessResolver;

    @MockBean
    private AuthSessionTokenService authSessionTokenService;

    @MockBean
    private AuthApiProtectionProperties authApiProtectionProperties;

    @Test
    void submitSyncTaskUsesProductMasterStoreAccessAndReturnsTask() throws Exception {
        BusinessAccessContext context = context();
        ProductPublicDetailTaskView task = ProductPublicDetailTaskView.from(taskRecord());
        when(businessAccessResolver.requireStoreAccess(
                org.mockito.ArgumentMatchers.any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("STR108065-NAE")
        )).thenReturn(context);
        when(syncService.submitManual(context, "STR108065-NAE", "AE")).thenReturn(task);

        mockMvc.perform(post("/api/product-public-details/sync-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeCode\":\"STR108065-NAE\",\"siteCode\":\"AE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(150452L))
                .andExpect(jsonPath("$.taskType").value(ProductPublicDetailSyncService.TASK_TYPE))
                .andExpect(jsonPath("$.storeCode").value("STR108065-NAE"))
                .andExpect(jsonPath("$.siteCode").value("AE"));

        verify(businessAccessResolver).requireStoreAccess(
                org.mockito.ArgumentMatchers.any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("STR108065-NAE")
        );
    }

    @Test
    void syncStatusUsesProductMasterStoreAccessAndReturnsScopeStatus() throws Exception {
        BusinessAccessContext context = context();
        ProductPublicDetailStatusView statusView = new ProductPublicDetailStatusView();
        statusView.setOwnerUserId(307L);
        statusView.setStoreCode("STR108065-NAE");
        statusView.setSiteCode("AE");
        statusView.setCandidateCount(1);
        statusView.setLatestPartialCount(1);
        when(businessAccessResolver.requireStoreAccess(
                org.mockito.ArgumentMatchers.any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("STR108065-NAE")
        )).thenReturn(context);
        when(syncService.syncStatus(context, "STR108065-NAE", "AE")).thenReturn(statusView);

        mockMvc.perform(get("/api/product-public-details/sync-status")
                        .param("storeCode", "STR108065-NAE")
                        .param("siteCode", "AE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").value(307L))
                .andExpect(jsonPath("$.storeCode").value("STR108065-NAE"))
                .andExpect(jsonPath("$.siteCode").value("AE"))
                .andExpect(jsonPath("$.candidateCount").value(1))
                .andExpect(jsonPath("$.latestPartialCount").value(1));

        verify(businessAccessResolver).requireStoreAccess(
                org.mockito.ArgumentMatchers.any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("STR108065-NAE")
        );
    }

    @Test
    void latestUsesProductMasterStoreAccessAndReturnsSnapshot() throws Exception {
        BusinessAccessContext context = context();
        ProductPublicDetailSnapshot snapshot = new ProductPublicDetailSnapshot();
        snapshot.setId(300001L);
        snapshot.setOwnerUserId(307L);
        snapshot.setStoreCode("STR108065-NAE");
        snapshot.setSiteCode("AE");
        snapshot.setProductMasterId(52654L);
        snapshot.setProductVariantId(53600L);
        snapshot.setNoonProductCode("ZE77E911445B6633FC201Z");
        snapshot.setSyncStatus(ProductPublicDetailSyncStatus.PARTIAL);
        snapshot.setFailureCode("PARTIAL_DETAIL");
        snapshot.setLatest(Boolean.TRUE);
        when(businessAccessResolver.requireStoreAccess(
                org.mockito.ArgumentMatchers.any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("STR108065-NAE")
        )).thenReturn(context);
        when(syncService.latest(context, "STR108065-NAE", "AE", 52654L, 53600L)).thenReturn(snapshot);

        mockMvc.perform(get("/api/product-public-details/latest")
                        .param("storeCode", "STR108065-NAE")
                        .param("siteCode", "AE")
                        .param("productMasterId", "52654")
                        .param("productVariantId", "53600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(300001L))
                .andExpect(jsonPath("$.noonProductCode").value("ZE77E911445B6633FC201Z"))
                .andExpect(jsonPath("$.syncStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.failureCode").value("PARTIAL_DETAIL"))
                .andExpect(jsonPath("$.latest").value(true));

        verify(businessAccessResolver).requireStoreAccess(
                org.mockito.ArgumentMatchers.any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("STR108065-NAE")
        );
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .storeCodes(java.util.Set.of("STR108065-NAE"))
                .storeOwnerUserIds(Map.of("STR108065-NAE", 307L))
                .build();
    }

    private static com.nuono.next.system.task.OperationalTask taskRecord() {
        com.nuono.next.system.task.OperationalTask task = new com.nuono.next.system.task.OperationalTask();
        task.setId(150452L);
        task.setTaskType(ProductPublicDetailSyncService.TASK_TYPE);
        task.setOwnerUserId(307L);
        task.setStoreCode("STR108065-NAE");
        task.setSiteCode("AE");
        task.setNaturalKey("product-public-detail:307:STR108065-NAE:AE:manual:smoke");
        task.setStatus(com.nuono.next.system.task.OperationalTaskStatus.SUCCEEDED);
        task.setProgressPercent(100);
        task.setMessage("商品前台详情同步完成：成功 0，部分 1，未找到 0，失败 0，跳过 0。");
        return task;
    }
}
