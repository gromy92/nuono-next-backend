package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ProductSelectionControllerGroupAccessTest {

    @Mock
    private ObjectProvider<LocalDbSourceCollectionService> sourceCollectionServiceProvider;

    @Mock
    private ObjectProvider<ProductSelectionAnalysisSkill> productSelectionAnalysisSkillProvider;

    @Mock
    private ObjectProvider<ProductSelectionAccessAdapter> accessAdapterProvider;

    @Mock
    private LocalDbSourceCollectionService sourceCollectionService;

    @Mock
    private ProductSelectionAccessAdapter accessAdapter;

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
    void groupDetailReadsThroughBusinessAccessScope() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/product-selection/groups/91001"
        );
        BusinessAccessContext access = access();
        ProductSelectionAccessScope scope = new ProductSelectionAccessScope(access, storeScope());
        ProductSelectionGroupView expected = new ProductSelectionGroupView();
        expected.setGroupId("91001");
        expected.setGroupName("Sharpie 记号笔组");

        when(accessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT)).thenReturn(access);
        when(accessAdapterProvider.getIfAvailable()).thenReturn(accessAdapter);
        when(accessAdapter.requireReadableStore(access, null)).thenReturn(scope);
        when(sourceCollectionServiceProvider.getIfAvailable()).thenReturn(sourceCollectionService);
        when(sourceCollectionService.getGroup("91001", 307L)).thenReturn(expected);

        ProductSelectionGroupView result = controller.group("91001", request);

        assertEquals("91001", result.getGroupId());
        assertEquals("Sharpie 记号笔组", result.getGroupName());
        verify(sourceCollectionService).getGroup("91001", 307L);
    }

    @Test
    void profitSaveUsesTheSessionOperatorWithoutResolvingAnArbitraryStore() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/product-selection/groups/91002/profit-estimate"
        );
        BusinessAccessContext access = multiStoreAccess();
        ProductSelectionGroupProfitSnapshotCommand command =
                new ProductSelectionGroupProfitSnapshotCommand();
        command.setOperatorUserId(999L);
        ProductSelectionGroupProfitSnapshotView expected =
                new ProductSelectionGroupProfitSnapshotView();
        expected.setSnapshotId("93002");
        expected.setGroupId("91002");

        when(accessResolver.requireBusinessContext(
                request,
                BusinessCapability.PROCUREMENT
        )).thenReturn(access);
        when(sourceCollectionServiceProvider.getIfAvailable())
                .thenReturn(sourceCollectionService);
        when(sourceCollectionService.saveGroupProfitEstimate("91002", command))
                .thenReturn(expected);

        ProductSelectionGroupProfitSnapshotView result =
                controller.saveGroupProfitEstimate("91002", command, request);

        assertEquals("93002", result.getSnapshotId());
        assertEquals(307L, command.getOperatorUserId());
        verify(sourceCollectionService)
                .saveGroupProfitEstimate("91002", command);
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of("STR108065-NAE"))
                .storeOwnerUserIds(Map.of("STR108065-NAE", 307L))
                .build();
    }

    private ProductSelectionStoreScope storeScope() {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOperatorUserId(307L);
        scope.setOwnerUserId(307L);
        scope.setLogicalStoreId(50005L);
        scope.setStoreCode("STR108065-NAE");
        scope.setSite("AE");
        scope.setAuthorized(true);
        return scope;
    }

    private BusinessAccessContext multiStoreAccess() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of("STR-STORE-A", "STR-STORE-B"))
                .storeOwnerUserIds(Map.of(
                        "STR-STORE-A", 307L,
                        "STR-STORE-B", 307L
                ))
                .build();
    }
}
