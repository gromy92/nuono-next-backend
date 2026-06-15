package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Ali1688HistoricalOrderSensitiveFieldPolicyTest {

    @Test
    void bossGetsMaskedSensitiveFieldsWithoutRawValues() {
        Ali1688HistoricalOrderWorkbenchView.SensitiveFieldsView view =
                Ali1688HistoricalOrderSensitiveFieldPolicy.apply(bossContext(), sensitiveOrder());

        assertThat(view.getRedactionLevel()).isEqualTo("masked");
        assertThat(view.getReceiverPhone()).isEqualTo("138****8000");
        assertThat(view.getReceiverAddress()).doesNotContain("西湖区文三路 99 号 3 幢 501 室");
        assertThat(view.getBuyerRemark()).isEqualTo("已隐藏");
        assertThat(view.getSupplierContact()).isEqualTo("已隐藏");
    }

    @Test
    void operatorGetsSafeHiddenSensitiveFieldsByDefault() {
        Ali1688HistoricalOrderWorkbenchView.SensitiveFieldsView view =
                Ali1688HistoricalOrderSensitiveFieldPolicy.apply(operatorContext(), sensitiveOrder());

        assertThat(view.getRedactionLevel()).isEqualTo("hidden");
        assertThat(view.getReceiverPhone()).isEqualTo("已隐藏");
        assertThat(view.getReceiverAddress()).isEqualTo("已隐藏");
        assertThat(view.getBuyerRemark()).isEqualTo("已隐藏");
        assertThat(view.getSupplierContact()).isEqualTo("已隐藏");
    }

    private BusinessAccessContext bossContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleName("老板")
                .roleLevel(1)
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }

    private BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(409L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleName("运营")
                .roleLevel(3)
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }

    private Ali1688HistoricalOrderRow sensitiveOrder() {
        Ali1688HistoricalOrderRow row = new Ali1688HistoricalOrderRow();
        row.setReceiverPhone("13800138000");
        row.setReceiverAddress("浙江省杭州市西湖区文三路 99 号 3 幢 501 室");
        row.setBuyerRemark("周五前发货，联系采购小王");
        row.setSupplierContact("旺旺：supplier-contact");
        return row;
    }
}
