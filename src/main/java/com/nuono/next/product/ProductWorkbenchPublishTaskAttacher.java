package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.publish.ProductPublishCommandService;
import org.springframework.util.StringUtils;

class ProductWorkbenchPublishTaskAttacher {

    private final ProductManagementMapper productManagementMapper;
    private final ProductPublishCommandService productPublishCommandService;
    private final TaskViewBuilder taskViewBuilder;

    ProductWorkbenchPublishTaskAttacher(
            ProductManagementMapper productManagementMapper,
            ProductPublishCommandService productPublishCommandService,
            TaskViewBuilder taskViewBuilder
    ) {
        this.productManagementMapper = productManagementMapper;
        this.productPublishCommandService = productPublishCommandService;
        this.taskViewBuilder = taskViewBuilder;
    }

    void attachActivePublishTask(Long ownerUserId, ProductWorkbenchRecord record) {
        if (ownerUserId == null || record == null || record.getDraftSnapshot() == null) {
            return;
        }
        String storeCode = textValue(record.getDraftSnapshot().getStoreContext().get("storeCode"));
        String skuParent = textValue(record.getDraftSnapshot().getIdentity().get("skuParent"));
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(skuParent)) {
            return;
        }
        if (productManagementMapper == null || productPublishCommandService == null) {
            record.setPublishTask(null);
            return;
        }
        Long productMasterId = productManagementMapper.selectProductMasterIdByStoreCode(
                ownerUserId,
                storeCode,
                skuParent
        );
        productPublishCommandService.recoverStaleRunningTasks();
        ProductPublishTaskRecord activeTask = productMasterId == null
                ? null
                : productManagementMapper.selectActiveProductPublishTask(productMasterId);
        record.setPublishTask(activeTask == null || taskViewBuilder == null
                ? null
                : taskViewBuilder.build(activeTask, false));
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    interface TaskViewBuilder {
        ProductPublishTaskView build(ProductPublishTaskRecord task, boolean includeWorkbench);
    }
}
