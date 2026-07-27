package com.nuono.next.logisticsquote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nuono.next.auth.AuthApiProtectionProperties;
import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.permission.access.RequiredBusinessAccess;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(LogisticsQuoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class LogisticsQuoteControllerMvcAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogisticsQuoteWorkbenchService workbenchService;

    @MockBean
    private LogisticsQuoteOperationService operationService;

    @MockBean
    private BusinessAccessResolver businessAccessResolver;

    @MockBean
    private AuthSessionTokenService authSessionTokenService;

    @MockBean
    private AuthApiProtectionProperties authApiProtectionProperties;

    @TempDir
    private Path tempDir;

    @Test
    void everyEndpointRequiresLogisticsQuoteAccess() {
        Method[] endpoints = Arrays.stream(LogisticsQuoteController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class))
                .toArray(Method[]::new);

        assertThat(endpoints).hasSize(13);
        for (Method endpoint : endpoints) {
            RequiredBusinessAccess[] declarations = Arrays.stream(endpoint.getParameters())
                    .filter(parameter -> parameter.getType().equals(BusinessAccessContext.class))
                    .map(parameter -> parameter.getAnnotation(RequiredBusinessAccess.class))
                    .filter(annotation -> annotation != null)
                    .toArray(RequiredBusinessAccess[]::new);
            assertThat(declarations).hasSize(1);
            assertThat(declarations[0].capability()).isEqualTo(BusinessCapability.LOGISTICS_QUOTE);
        }
    }

    @Test
    void priceAdjustmentUsesTheAuthenticatedSessionAsOperator() throws Exception {
        when(businessAccessResolver.requireBusinessContext(
                any(HttpServletRequest.class),
                eq(BusinessCapability.LOGISTICS_QUOTE)
        )).thenReturn(context());
        when(operationService.savePriceAdjustment(
                eq(90001L),
                any(LogisticsQuoteOperationPriceAdjustmentCommand.class)
        )).thenReturn(new LogisticsQuoteOperationPriceAdjustmentView());

        mockMvc.perform(post("/api/logistics-quote/operations/price-adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"targetType\":\"BASE_PRICE\","
                                + "\"targetId\":912001,"
                                + "\"numericField\":\"unit_price\","
                                + "\"adjustedValue\":70,"
                                + "\"reason\":\"market change\","
                                + "\"operatorUserId\":2"
                                + "}"))
                .andExpect(status().isOk());

        verify(operationService).savePriceAdjustment(
                eq(90001L),
                any(LogisticsQuoteOperationPriceAdjustmentCommand.class)
        );
    }

    @Test
    void deniedCapabilityStopsBeforeQuoteModulesAndArchiveResolution() throws Exception {
        when(businessAccessResolver.requireBusinessContext(
                any(HttpServletRequest.class),
                eq(BusinessCapability.LOGISTICS_QUOTE)
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有物流报价权限。"));

        mockMvc.perform(get("/api/logistics-quote/workbench"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/logistics-quote/source-files/77/archive"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/logistics-quote/operations/price-items"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workbenchService, operationService);
    }

    @Test
    void archivedSourceDownloadPreventsClientCaching() throws Exception {
        Path archive = Files.writeString(tempDir.resolve("quote.xlsx"), "quote");
        when(businessAccessResolver.requireBusinessContext(
                any(HttpServletRequest.class),
                eq(BusinessCapability.LOGISTICS_QUOTE)
        )).thenReturn(context());
        when(workbenchService.resolveArchivedSourceFile(77L))
                .thenReturn(new LogisticsQuoteArchivedFile(archive, "quote.xlsx"));

        mockMvc.perform(get("/api/logistics-quote/source-files/77/archive"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .menuPaths(Set.of("/purchase/logistics-quote"))
                .build();
    }
}
