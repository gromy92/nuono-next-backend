package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitProductMatchViews.CandidateListView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitProductMatchControllerTest {
    @Mock
    private InTransitBatchService batchService;
    @Mock
    private InTransitGoodsAccessScopeService accessScopeService;
    @Mock
    private InTransitProductMatchService productMatchService;
    @Mock
    private BusinessAccessResolver businessAccessResolver;
    @Mock
    private HttpServletRequest request;
    private InTransitProductMatchController controller;

    @BeforeEach
    void setUp() {
        controller = new InTransitProductMatchController(
                batchService,
                accessScopeService,
                productMatchService,
                businessAccessResolver
        );
    }

    @Test
    void shouldListCandidatesOnlyAfterBatchAccessCheck() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .businessOwnerUserId(10002L)
                .sessionUserId(90001L)
                .build();
        BatchView batch = new BatchView();
        CandidateListView expected = new CandidateListView();
        when(businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.IN_TRANSIT_GOODS
        )).thenReturn(context);
        when(batchService.getBatch(10002L, 53001L)).thenReturn(batch);
        when(productMatchService.list(10002L, 53001L)).thenReturn(expected);

        CandidateListView result = controller.list(53001L, request);

        assertSame(expected, result);
        verify(accessScopeService).requireBatchAccess(context, batch);
    }
}
