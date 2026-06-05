package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Ali1688HistoricalOrderProviderContractTest {

    @Test
    void fakeProviderReturnsOneBuyerOrderWithItems() {
        FakeAli1688HistoricalOrderProvider provider = new FakeAli1688HistoricalOrderProvider();

        Ali1688HistoricalOrderProvider.Page page = provider.fetchFirstPage(authorization());

        assertThat(page.getOrders()).hasSize(1);
        Ali1688HistoricalOrderProvider.OrderSnapshot order = page.getOrders().get(0);
        assertThat(order.getProviderOrderNo()).isEqualTo("ALI-ORDER-20260525-001");
        assertThat(order.getOrderTime()).isEqualTo("2026-05-25 10:30:00");
        assertThat(order.getPaidAt()).isEqualTo("2026-05-25 10:31:20");
        assertThat(order.getBuyerCompanyName()).isEqualTo("松果果电子商务有限公司");
        assertThat(order.getBuyerMemberName()).isEqualTo("沁雪冰菏");
        assertThat(order.getSupplierName()).isEqualTo("义乌诚信通源头工厂");
        assertThat(order.getSellerMemberName()).isEqualTo("诚信通源头工厂");
        assertThat(order.getGoodsTotalText()).isEqualTo("¥128.00");
        assertThat(order.getFreightText()).isEqualTo("¥0.00");
        assertThat(order.getAdjustmentText()).isNull();
        assertThat(order.getPaidAmountText()).isEqualTo("¥128.00");
        assertThat(order.getAmountText()).isEqualTo("¥128.00");
        assertThat(order.getShipperName()).isEqualTo("商家发货");
        assertThat(order.getReceiverName()).isEqualTo("梁宇");
        assertThat(order.getInitiatorLoginName()).isEqualTo("沁雪冰菏");
        assertThat(order.getItems()).hasSize(1);
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item = order.getItems().get(0);
        assertThat(item.getOfferId()).isEqualTo("745612345678");
        assertThat(item.getSkuId()).isEqualTo("SKU-745612345678-RED");
        assertThat(item.getTitle()).isEqualTo("仿真罂粟花束 6 支装 家居装饰");
        assertThat(item.getModelText()).isEqualTo("仿真花束");
        assertThat(item.getProductCode()).isEqualTo("彩虹蛋糕");
        assertThat(item.getSingleProductCode()).isEqualTo("MX-001");
        assertThat(item.getQuantity()).isEqualTo(10);
        assertThat(item.getUnit()).isEqualTo("套");
        assertThat(item.getLogisticsCompany()).isEqualTo("中通快递(ZTO)");
        assertThat(item.getTrackingNo()).isEqualTo("ZTO20260525001");
    }

    private Ali1688HistoricalOrderAuthorizationRow authorization() {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(91001L);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_DEV");
        row.setStatus("authorized");
        return row;
    }
}
