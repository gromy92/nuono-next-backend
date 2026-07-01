package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.publish.ProductPublishCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductWorkbenchPublishTaskAttacherTest {

    @Mock
    private ProductManagementMapper productManagementMapper;

    @Mock
    private ProductPublishCommandService productPublishCommandService;

    private ProductWorkbenchPublishTaskAttacher.TaskViewBuilder taskViewBuilder;
    private ProductWorkbenchPublishTaskAttacher attacher;

    @BeforeEach
    void setUp() {
        taskViewBuilder = mock(ProductWorkbenchPublishTaskAttacher.TaskViewBuilder.class);
        attacher = new ProductWorkbenchPublishTaskAttacher(
                productManagementMapper,
                productPublishCommandService,
                taskViewBuilder
        );
    }

    @Test
    void attachesActivePublishTaskViewFromDraftIdentity() {
        ProductWorkbenchRecord record = record();
        ProductPublishTaskRecord activeTask = new ProductPublishTaskRecord();
        activeTask.setId(77001L);
        ProductPublishTaskView taskView = new ProductPublishTaskView();
        taskView.setTaskId(77001L);
        ProductMasterIdentityRecord identity = new ProductMasterIdentityRecord();
        identity.setProductMasterId(90001L);
        identity.setLogicalStoreId(50001L);
        identity.setStoreCode("STR245027-NAE");
        identity.setSkuParent("PAPERSAYSB132");
        identity.setPartnerSku("PARTNER-132");
        when(productManagementMapper.selectProductMasterIdentityByStorePartnerSku(
                10002L,
                "STR245027-NAE",
                "PARTNER-132"
        )).thenReturn(identity);
        when(productManagementMapper.selectActiveProductPublishTask(90001L)).thenReturn(activeTask);
        when(taskViewBuilder.build(activeTask, false)).thenReturn(taskView);

        attacher.attachActivePublishTask(10002L, record);

        verify(productPublishCommandService).recoverStaleRunningTasks();
        assertSame(taskView, record.getPublishTask());
    }

    private ProductWorkbenchRecord record() {
        ProductMasterSnapshotView draft = new ProductMasterSnapshotView();
        draft.getStoreContext().put("storeCode", "STR245027-NAE");
        draft.getIdentity().put("skuParent", "PAPERSAYSB132");
        draft.getIdentity().put("partnerSku", "PARTNER-132");
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        record.setDraftSnapshot(draft);
        return record;
    }
}
