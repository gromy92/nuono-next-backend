package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nuono.next.auth.AuthApiProtectionProperties;
import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.permission.access.BusinessStoreAccess;
import com.nuono.next.permission.access.BusinessStoreAccessTestFixture;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ProductLiteController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductLiteControllerMvcAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductLiteQueryService service;

    @MockBean
    private BusinessAccessResolver businessAccessResolver;

    @MockBean
    private AuthSessionTokenService authSessionTokenService;

    @MockBean
    private AuthApiProtectionProperties authApiProtectionProperties;

    @Test
    void mvcAdapterSuppliesCanonicalTypedScopeToProductLite() throws Exception {
        BusinessStoreAccess access = BusinessStoreAccessTestFixture.access(501L, "STR108065-NSA");
        ProductLiteView view = new ProductLiteView();
        view.setProductMasterId(90001L);
        when(businessAccessResolver.requireStoreAccessScope(
                any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq(" str108065-nsa ")
        )).thenReturn(access);
        when(service.search(eq(access), any(ProductLiteQuery.class))).thenReturn(List.of(view));

        mockMvc.perform(get("/api/products/lite")
                        .param("storeCode", " str108065-nsa ")
                        .param("siteCode", "sa")
                        .param("titleKeyword", " Basket ")
                        .param("limit", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productMasterId").value(90001L));

        verify(service).search(eq(access), any(ProductLiteQuery.class));
    }

    @Test
    void missingStoreKeepsSpringMissingParameterContract() throws Exception {
        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(get("/api/products/lite"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResolvedException())
                .isInstanceOfSatisfying(MissingServletRequestParameterException.class, error -> {
                    assertThat(error.getParameterName()).isEqualTo("storeCode");
                    assertThat(error.getParameterType()).isEqualTo("String");
                });
        verify(businessAccessResolver, never()).requireStoreAccessScope(any(), any(), any());
        verify(service, never()).search(any(), any());
    }

    @Test
    void malformedOptionalParameterFailsBeforeSessionResolution() throws Exception {
        when(businessAccessResolver.requireStoreAccessScope(
                any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("NO-SESSION")
        )).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_REQUIRED"));
        when(businessAccessResolver.requireStoreAccessScope(
                any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("NO-MAPPER")
        )).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "业务访问控制暂时不可用。"));

        mockMvc.perform(get("/api/products/lite")
                        .param("storeCode", "NO-SESSION")
                        .param("limit", "abc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/products/lite")
                        .param("storeCode", "NO-MAPPER")
                        .param("limit", "abc"))
                .andExpect(status().isBadRequest());

        verify(businessAccessResolver, never()).requireStoreAccessScope(any(), any(), any());
        verify(service, never()).search(any(), any());
    }

    @Test
    void blankAndUnauthorizedStoreRemainForbidden() throws Exception {
        when(businessAccessResolver.requireStoreAccessScope(
                any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq(" ")
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));
        when(businessAccessResolver.requireStoreAccessScope(
                any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("STR-OTHER")
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));

        mockMvc.perform(get("/api/products/lite").param("storeCode", " "))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/products/lite").param("storeCode", "STR-OTHER"))
                .andExpect(status().isForbidden());

        verify(service, never()).search(any(), any());
    }

    @Test
    void sessionAndMapperFailuresKeepExistingStatuses() throws Exception {
        when(businessAccessResolver.requireStoreAccessScope(
                any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("NO-SESSION")
        )).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_REQUIRED"));
        when(businessAccessResolver.requireStoreAccessScope(
                any(HttpServletRequest.class),
                eq(BusinessCapability.PRODUCT_MASTER),
                eq("NO-MAPPER")
        )).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "业务访问控制暂时不可用。"));

        mockMvc.perform(get("/api/products/lite").param("storeCode", "NO-SESSION"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products/lite").param("storeCode", "NO-MAPPER"))
                .andExpect(status().isServiceUnavailable());

        verify(service, never()).search(any(), any());
    }
}
