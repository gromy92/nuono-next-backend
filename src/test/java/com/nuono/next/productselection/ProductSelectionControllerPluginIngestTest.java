package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ProductSelectionControllerPluginIngestTest {

    @Mock
    private ObjectProvider<LocalDbSourceCollectionService> sourceCollectionServiceProvider;

    @Mock
    private ObjectProvider<ProductSelectionAnalysisSkill> productSelectionAnalysisSkillProvider;

    @Mock
    private ObjectProvider<ProductSelectionAccessAdapter> accessAdapterProvider;

    @Mock
    private LocalDbSourceCollectionService sourceCollectionService;

    @Mock
    private BusinessAccessResolver accessResolver;

    private ProductSelectionController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductSelectionController(
                sourceCollectionServiceProvider,
                productSelectionAnalysisSkillProvider,
                accessAdapterProvider,
                accessResolver
        );
    }

    @Test
    void pluginIngestStatusReportsAuthenticatedSourceCollectionCapability() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/product-selection/source-collections/plugin-ingest/status");
        when(accessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT)).thenReturn(access());
        when(sourceCollectionServiceProvider.getIfAvailable()).thenReturn(sourceCollectionService);

        Map<String, Object> payload = controller.pluginIngestStatus(request);

        assertEquals(Boolean.TRUE, payload.get("success"));
        assertEquals("source-collection-plugin-ingest", payload.get("capability"));
        assertEquals(307L, payload.get("operatorUserId"));
        verify(sourceCollectionServiceProvider).getIfAvailable();
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of("STR108065-NAE"))
                .build();
    }
}
