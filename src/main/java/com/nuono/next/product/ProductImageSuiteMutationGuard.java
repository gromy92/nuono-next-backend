package com.nuono.next.product;

final class ProductImageSuiteMutationGuard {
    private ProductImageSuiteMutationGuard() {
    }

    static void requireMutable(ProductImageSuiteRecord suite) {
        if (suite != null && suite.getSuiteStatus() == ProductImageSuiteStatus.PUBLISHING) {
            throw new IllegalStateException("图片正在发布到 Noon，当前不能删除、移动、排序或切换该套图。");
        }
    }
}
