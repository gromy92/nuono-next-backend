package com.nuono.next.postsaleprofit.batchattribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionListView;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PostSaleProfitBatchAttributionControllerAccessTest {

    @Mock
    private PostSaleProfitBatchAttributionService service;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private PostSaleProfitBatchAttributionController controller;

    @BeforeEach
    void setUp() {
        controller = new PostSaleProfitBatchAttributionController(service, businessAccessResolver);
    }

    @Test
    void summaryUsesSalesDataCapabilityAndAuthorizedOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(accessContext());
        when(service.listSummary(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                "PAPERSAY"
        )).thenReturn(new BatchAttributionListView());

        controller.summary(
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                " PAPERSAY ",
                request
        );

        verify(service).listSummary(307L, "STR108065-NSA", "SA", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "PAPERSAY");
    }

    @Test
    void skuDetailRejectsMissingPartnerSkuBeforeServiceCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(accessContext());

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> controller.skuDetail("STR108065-NSA", "SA", null, null, " ", request)
        );

        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).getSkuDetail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void summaryDoesNotCallServiceWhenStoreAccessIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> controller.summary("STR108065-NSA", "SA", null, null, null, request)
        );

        assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(service, never()).listSummary(any(), any(), any(), any(), any(), any());
    }

    private static BusinessAccessContext accessContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(501L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(2)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/api/post-sale-profit"))
                .build();
    }
}
