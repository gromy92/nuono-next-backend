package com.nuono.next.intransit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nuono.next.auth.AuthApiProtectionProperties;
import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.intransit.InTransitFreightCostCommands.SaveRateCardVersionCommand;
import com.nuono.next.intransit.InTransitFreightCostRecords.FreightStatisticsView;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardVersionView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.permission.access.RequiredBusinessAccess;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(InTransitFreightCostController.class)
@AutoConfigureMockMvc(addFilters = false)
class InTransitFreightCostControllerMvcAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InTransitBatchService batchService;

    @MockBean
    private InTransitFreightCostService freightCostService;

    @MockBean
    private BusinessAccessResolver businessAccessResolver;

    @MockBean
    private InTransitGoodsAccessScopeService accessScopeService;

    @MockBean
    private AuthSessionTokenService authSessionTokenService;

    @MockBean
    private AuthApiProtectionProperties authApiProtectionProperties;

    @Test
    void mvcAdapterSuppliesAuthorizedContextToTheController() throws Exception {
        BusinessAccessContext context = context();
        when(businessAccessResolver.requireBusinessContext(
                any(HttpServletRequest.class),
                eq(BusinessCapability.IN_TRANSIT_GOODS)
        )).thenReturn(context);
        when(freightCostService.statistics(10002L, null, null, 30700002L))
                .thenReturn(new FreightStatisticsView());

        mockMvc.perform(get("/api/in-transit-goods/freight-costs/statistics")
                        .param("standardForwarderId", "30700002"))
                .andExpect(status().isOk());

        verify(businessAccessResolver).requireBusinessContext(
                any(HttpServletRequest.class),
                eq(BusinessCapability.IN_TRANSIT_GOODS)
        );
        verify(freightCostService).statistics(10002L, null, null, 30700002L);
    }

    @Test
    void deniedBusinessScopeStopsBeforeTheFreightModule() throws Exception {
        when(businessAccessResolver.requireBusinessContext(
                any(HttpServletRequest.class),
                eq(BusinessCapability.IN_TRANSIT_GOODS)
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有对应业务菜单权限。"));

        mockMvc.perform(get("/api/in-transit-goods/freight-costs/statistics"))
                .andExpect(status().isForbidden());

        verify(freightCostService, never()).statistics(any(), any(), any(), any());
    }

    @Test
    void requestOwnerAndOperatorCannotOverrideTheAuthorizedContext() throws Exception {
        BusinessAccessContext context = context();
        when(businessAccessResolver.requireBusinessContext(
                any(HttpServletRequest.class),
                eq(BusinessCapability.IN_TRANSIT_GOODS)
        )).thenReturn(context);
        ArgumentCaptor<SaveRateCardVersionCommand> commandCaptor =
                ArgumentCaptor.forClass(SaveRateCardVersionCommand.class);
        RateCardVersionView saved = new RateCardVersionView();
        saved.setRateCardVersionId(64001L);
        when(freightCostService.saveRateCardVersion(commandCaptor.capture())).thenReturn(saved);

        mockMvc.perform(post("/api/in-transit-goods/freight-costs/rate-card-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerUserId\":1,\"operatorUserId\":2,\"forwarderCode\":\"YITONG\"}"))
                .andExpect(status().isOk());

        assertThat(commandCaptor.getValue().getOwnerUserId()).isEqualTo(10002L);
        assertThat(commandCaptor.getValue().getOperatorUserId()).isEqualTo(90001L);
    }

    @Test
    void everyPilotEndpointDeclaresTheSameRequiredBusinessCapability() {
        Method[] endpoints = Arrays.stream(InTransitFreightCostController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class))
                .toArray(Method[]::new);

        assertThat(endpoints).hasSize(5);
        for (Method endpoint : endpoints) {
            RequiredBusinessAccess[] declarations = Arrays.stream(endpoint.getParameters())
                    .map(parameter -> parameter.getAnnotation(RequiredBusinessAccess.class))
                    .filter(annotation -> annotation != null)
                    .toArray(RequiredBusinessAccess[]::new);
            assertThat(declarations).hasSize(1);
            assertThat(declarations[0].capability()).isEqualTo(BusinessCapability.IN_TRANSIT_GOODS);
            assertThat(declarations[0].storeQueryParameter()).isEmpty();
        }
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .menuPaths(Set.of("/purchase/in-transit-goods"))
                .build();
    }
}
