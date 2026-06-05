package com.nuono.next.product;

import com.nuono.next.product.publish.ProductPublishCommandService;
import java.util.List;
import java.util.function.Function;

class ProductPublishTaskViewBuilder {

    private final ProductPublishCommandService productPublishCommandService;
    private final Function<ProductPublishTaskRecord, ProductMasterWorkbenchView> terminalWorkbenchBuilder;
    private final Function<ProductPublishTaskRecord, List<String>> changedDomainsResolver;

    ProductPublishTaskViewBuilder(
            ProductPublishCommandService productPublishCommandService,
            Function<ProductPublishTaskRecord, ProductMasterWorkbenchView> terminalWorkbenchBuilder,
            Function<ProductPublishTaskRecord, List<String>> changedDomainsResolver
    ) {
        this.productPublishCommandService = productPublishCommandService;
        this.terminalWorkbenchBuilder = terminalWorkbenchBuilder;
        this.changedDomainsResolver = changedDomainsResolver;
    }

    ProductPublishTaskView build(ProductPublishTaskRecord task, boolean includeWorkbench) {
        return requirePublishCommandService().buildTaskView(
                task,
                includeWorkbench,
                terminalWorkbenchBuilder,
                changedDomainsResolver
        );
    }

    ProductPublishTaskView load(Long taskId, Long ownerUserId) {
        return requirePublishCommandService().loadTask(
                taskId,
                ownerUserId,
                terminalWorkbenchBuilder,
                changedDomainsResolver
        );
    }

    ProductPublishTaskView retry(Long taskId, Long ownerUserId) {
        return requirePublishCommandService().retryTask(
                taskId,
                ownerUserId,
                terminalWorkbenchBuilder,
                changedDomainsResolver
        );
    }

    ProductPublishTaskView cancel(Long taskId, Long ownerUserId) {
        return requirePublishCommandService().cancelTask(
                taskId,
                ownerUserId,
                terminalWorkbenchBuilder,
                changedDomainsResolver
        );
    }

    private ProductPublishCommandService requirePublishCommandService() {
        if (productPublishCommandService == null) {
            throw new IllegalStateException("商品发布命令服务尚未初始化。");
        }
        return productPublishCommandService;
    }
}
