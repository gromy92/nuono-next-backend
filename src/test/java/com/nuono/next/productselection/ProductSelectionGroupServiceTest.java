package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSelectionGroupServiceTest {

    @Mock
    private ProductSelectionMapper productSelectionMapper;

    @Mock
    private ProductSelectionPermissionGuard permissionGuard;

    @Mock
    private ProductSelectionSourceCollectionCollector sourceCollectionCollector;

    @Mock
    private ProductSelectionSourceCollectionLocalizer sourceCollectionLocalizer;

    private ProductSelectionGroupService service;

    @BeforeEach
    void setUp() {
        service = new ProductSelectionGroupService(
                productSelectionMapper,
                permissionGuard,
                sourceCollectionCollector,
                sourceCollectionLocalizer,
                new ObjectMapper(),
                this::sourceCollectionView,
                this::sourceCollectionSnapshotView
        );
    }

    @Test
    void createGroupOwnsGroupPersistenceOutsideSourceCollectionService() {
        ProductSelectionSourceCollectionRow first = sourceCollection(
                86001L,
                "Sharpie Permanent Markers",
                "Sharpie 永久记号笔"
        );
        ProductSelectionSourceCollectionRow second = sourceCollection(
                86002L,
                "Marker Pen Competitor",
                "竞品记号笔"
        );
        ProductSelectionGroupCommand command = new ProductSelectionGroupCommand();
        command.setOperatorUserId(307L);
        command.setSourceCollectionIds(List.of("86001", "86002"));
        command.setGroupName("Sharpie 记号笔组");

        when(productSelectionMapper.selectSourceCollectionById(86001L)).thenReturn(first);
        when(productSelectionMapper.selectSourceCollectionById(86002L)).thenReturn(second);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser());
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, 301L)).thenReturn(1);
        when(productSelectionMapper.selectActiveGroupMaterialBySourceCollectionId(86001L)).thenReturn(null);
        when(productSelectionMapper.selectActiveGroupMaterialBySourceCollectionId(86002L)).thenReturn(null);
        when(productSelectionMapper.nextSelectionGroupId()).thenReturn(91001L);
        when(productSelectionMapper.nextSelectionGroupMaterialId()).thenReturn(92001L, 92002L);
        when(productSelectionMapper.selectGroupById(91001L)).thenReturn(groupRow(91001L, "Sharpie 记号笔组"));
        when(productSelectionMapper.listGroupMaterialsByGroupIds(List.of(91001L)))
                .thenReturn(List.of(groupMaterialRow(92001L, 91001L, first), groupMaterialRow(92002L, 91001L, second)));
        when(productSelectionMapper.selectGroupProcurementByGroupId(91001L)).thenReturn(null);

        ProductSelectionGroupView view = service.createGroup(command);

        ArgumentCaptor<ProductSelectionGroupRow> groupCaptor = ArgumentCaptor.forClass(ProductSelectionGroupRow.class);
        verify(productSelectionMapper).insertSelectionGroup(groupCaptor.capture());
        assertEquals(91001L, groupCaptor.getValue().getGroupId());
        assertEquals("Sharpie 记号笔组", groupCaptor.getValue().getGroupName());

        ArgumentCaptor<ProductSelectionGroupMaterialRow> materialCaptor =
                ArgumentCaptor.forClass(ProductSelectionGroupMaterialRow.class);
        verify(productSelectionMapper, times(2)).insertSelectionGroupMaterial(materialCaptor.capture());
        assertEquals(86001L, materialCaptor.getAllValues().get(0).getSourceCollectionId());
        assertEquals(86002L, materialCaptor.getAllValues().get(1).getSourceCollectionId());
        assertEquals("91001", view.getGroupId());
        assertEquals(2, view.getMaterialCount());
    }

    @Test
    void savingProfitRejectsAnUnauthorizedTargetSiteWithinAWritableLogicalStore() {
        ProductSelectionPermissionGuard realPermissionGuard =
                new ProductSelectionPermissionGuard(productSelectionMapper);
        ProductSelectionGroupService scopedService = new ProductSelectionGroupService(
                productSelectionMapper,
                realPermissionGuard,
                sourceCollectionCollector,
                sourceCollectionLocalizer,
                new ObjectMapper(),
                this::sourceCollectionView,
                this::sourceCollectionSnapshotView
        );
        ProductSelectionGroupRow saGroup = groupRow(91002L, "SA 店选品组");
        saGroup.setLogicalStoreId(302L);
        saGroup.setSiteCode("SA");
        ProductSelectionGroupProfitSnapshotCommand command =
                new ProductSelectionGroupProfitSnapshotCommand();
        command.setOperatorUserId(90001L);

        when(productSelectionMapper.selectGroupById(91002L)).thenReturn(saGroup);
        when(productSelectionMapper.selectUserContext(90001L)).thenReturn(activeOperator());
        when(productSelectionMapper.selectVisibleStoreScope(90001L, "STR-STORE-AE"))
                .thenReturn(storeScope(302L, "STR-STORE-AE", true));
        when(productSelectionMapper.listLogicalStoreSiteCodes(302L, "SA"))
                .thenReturn(List.of("STR-STORE-SA"));
        when(productSelectionMapper.selectVisibleStoreScope(90001L, "STR-STORE-SA"))
                .thenReturn(storeScope(302L, "STR-STORE-SA", false));

        assertEquals(
                "STR-STORE-AE",
                realPermissionGuard
                        .requireWritableStore(90001L, "STR-STORE-AE")
                        .getStoreCode()
        );
        ProductSelectionAccessDeniedException error = assertThrows(
                ProductSelectionAccessDeniedException.class,
                () -> scopedService.saveGroupProfitEstimate("91002", command)
        );

        assertEquals("当前选品组所属店铺未授权，不能保存利润估算。", error.getMessage());
        verify(productSelectionMapper, never()).insertSelectionGroupProfitSnapshot(
                org.mockito.ArgumentMatchers.any(ProductSelectionGroupProfitSnapshotRow.class)
        );
    }

    @Test
    void savingProfitSucceedsWhenTheTargetSiteWithinTheLogicalStoreIsWritable() {
        ProductSelectionGroupService scopedService = new ProductSelectionGroupService(
                productSelectionMapper,
                new ProductSelectionPermissionGuard(productSelectionMapper),
                sourceCollectionCollector,
                sourceCollectionLocalizer,
                new ObjectMapper(),
                this::sourceCollectionView,
                this::sourceCollectionSnapshotView
        );
        ProductSelectionGroupRow saGroup = groupRow(91002L, "SA 店选品组");
        saGroup.setLogicalStoreId(302L);
        saGroup.setSiteCode("SA");
        ProductSelectionGroupProfitSnapshotCommand command =
                new ProductSelectionGroupProfitSnapshotCommand();
        command.setOperatorUserId(90001L);
        command.setCurrencyCode("SAR");
        command.setProfitAmount(new BigDecimal("25.50"));

        when(productSelectionMapper.selectGroupById(91002L)).thenReturn(saGroup);
        when(productSelectionMapper.selectUserContext(90001L)).thenReturn(activeOperator());
        when(productSelectionMapper.listLogicalStoreSiteCodes(302L, "SA"))
                .thenReturn(List.of("STR-STORE-SA"));
        when(productSelectionMapper.selectVisibleStoreScope(90001L, "STR-STORE-SA"))
                .thenReturn(storeScope(302L, "STR-STORE-SA", true));
        when(productSelectionMapper.nextSelectionGroupProfitSnapshotId())
                .thenReturn(93002L);

        ProductSelectionGroupProfitSnapshotView saved =
                scopedService.saveGroupProfitEstimate("91002", command);

        assertEquals("93002", saved.getSnapshotId());
        assertEquals("91002", saved.getGroupId());
        assertEquals("SAR", saved.getCurrencyCode());
        assertEquals(new BigDecimal("25.50"), saved.getProfitAmount());
        verify(productSelectionMapper).insertSelectionGroupProfitSnapshot(
                org.mockito.ArgumentMatchers.argThat(row ->
                        Long.valueOf(91002L).equals(row.getGroupId())
                                && Long.valueOf(90001L).equals(row.getCreatedBy()))
        );
    }

    private ProductSelectionSourceCollectionView sourceCollectionView(ProductSelectionSourceCollectionRow row) {
        ProductSelectionSourceCollectionView view = new ProductSelectionSourceCollectionView();
        view.setId(row.getId() == null ? null : String.valueOf(row.getId()));
        view.setSourceTitle(row.getSourceTitle());
        view.setSourceTitleCn(row.getSourceTitleCn());
        view.setStatus(row.getStatus());
        return view;
    }

    private ProductSelectionSourceCollectionView sourceCollectionSnapshotView(
            ProductSelectionSourceCollectionRow row,
            ProductSelectionSourceCollectionResult result
    ) {
        ProductSelectionSourceCollectionView view = new ProductSelectionSourceCollectionView();
        view.setCollectedFieldCount(1);
        view.setCollectedFieldTotal(1);
        return view;
    }

    private ProductSelectionSourceCollectionRow sourceCollection(
            Long id,
            String sourceTitle,
            String sourceTitleCn
    ) {
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(id);
        row.setOwnerUserId(307L);
        row.setLogicalStoreId(301L);
        row.setSiteCode("SA");
        row.setSourceTitle(sourceTitle);
        row.setSourceTitleCn(sourceTitleCn);
        row.setStatus("success");
        row.setUpdatedBy(307L);
        return row;
    }

    private ProductSelectionGroupRow groupRow(Long groupId, String groupName) {
        ProductSelectionGroupRow row = new ProductSelectionGroupRow();
        row.setGroupId(groupId);
        row.setOwnerUserId(307L);
        row.setLogicalStoreId(301L);
        row.setSiteCode("SA");
        row.setGroupNo("PSG-" + groupId);
        row.setGroupName(groupName);
        row.setGroupStatus("active");
        row.setCreatedBy(307L);
        row.setUpdatedBy(307L);
        return row;
    }

    private ProductSelectionGroupMaterialRow groupMaterialRow(
            Long materialId,
            Long groupId,
            ProductSelectionSourceCollectionRow source
    ) {
        ProductSelectionGroupMaterialRow row = new ProductSelectionGroupMaterialRow();
        row.setMaterialId(materialId);
        row.setGroupId(groupId);
        row.setSourceCollectionId(source.getId());
        row.setOwnerUserId(source.getOwnerUserId());
        row.setLogicalStoreId(source.getLogicalStoreId());
        row.setSiteCode(source.getSiteCode());
        row.setMaterialStatus("active");
        row.setCreatedBy(307L);
        row.setUpdatedBy(307L);
        row.copySourceCollectionFieldsFrom(source);
        return row;
    }

    private ProductSelectionUserContext activeUser() {
        ProductSelectionUserContext user = new ProductSelectionUserContext();
        user.setUserId(307L);
        user.setAccountNo("boss");
        user.setLevel(1);
        user.setStatus(1);
        return user;
    }

    private ProductSelectionUserContext activeOperator() {
        ProductSelectionUserContext user = new ProductSelectionUserContext();
        user.setUserId(90001L);
        user.setAccountNo("multi-store-operator");
        user.setLevel(2);
        user.setStatus(1);
        return user;
    }

    private ProductSelectionStoreScope storeScope(
            Long logicalStoreId,
            String storeCode,
            boolean authorized
    ) {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOperatorUserId(90001L);
        scope.setOwnerUserId(307L);
        scope.setLogicalStoreId(logicalStoreId);
        scope.setStoreCode(storeCode);
        scope.setAuthorized(authorized);
        return scope;
    }
}
