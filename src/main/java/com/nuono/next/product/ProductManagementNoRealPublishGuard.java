package com.nuono.next.product;

import java.util.Collection;
import org.springframework.util.StringUtils;

public class ProductManagementNoRealPublishGuard {

    public void assertNoRealPublishActions(Collection<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (String action : actions) {
            if (isRealPublishAction(action)) {
                throw new IllegalArgumentException(
                        "Automated product-management regression flow must not invoke real publish-current: " + action
                );
            }
        }
    }

    private boolean isRealPublishAction(String action) {
        if (!StringUtils.hasText(action)) {
            return false;
        }
        String normalized = action.trim()
                .toLowerCase()
                .replace('_', '-');
        return "publish-current".equals(normalized)
                || "publish-current-site".equals(normalized);
    }
}
