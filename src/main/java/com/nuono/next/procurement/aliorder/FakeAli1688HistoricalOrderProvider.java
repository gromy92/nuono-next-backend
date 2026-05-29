package com.nuono.next.procurement.aliorder;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FakeAli1688HistoricalOrderProvider implements Ali1688HistoricalOrderProvider {

    private final Map<String, Page> pagesByCursor;

    public FakeAli1688HistoricalOrderProvider() {
        this(Map.of("", singlePage()));
    }

    private FakeAli1688HistoricalOrderProvider(Map<String, Page> pagesByCursor) {
        this.pagesByCursor = pagesByCursor;
    }

    public static FakeAli1688HistoricalOrderProvider multiPage() {
        Page firstPage = singlePage();
        firstPage.setHasMore(true);
        firstPage.setNextCursor("page-2");
        firstPage.setProgressPercent(50);
        Page secondPage = new Page(List.of(secondOrder()));
        secondPage.setHasMore(false);
        secondPage.setProgressPercent(100);
        return new FakeAli1688HistoricalOrderProvider(Map.of("", firstPage, "page-2", secondPage));
    }

    public static FakeAli1688HistoricalOrderProvider partialDetailFailure() {
        Page page = singlePage();
        page.setFailureCode("missing_fields");
        page.setFailureMessage("部分订单详情字段未返回。");
        page.setRetryableFailure(true);
        page.setProgressPercent(100);
        return new FakeAli1688HistoricalOrderProvider(Map.of("", page));
    }

    public static FakeAli1688HistoricalOrderProvider incrementalUpdate() {
        OrderSnapshot order = singlePage().getOrders().get(0);
        order.setAmountText("¥138.00");
        order.setOrderStatus("已完成");
        order.setLogisticsStatus("已签收");
        return new FakeAli1688HistoricalOrderProvider(Map.of("", new Page(List.of(order))));
    }

    public static FakeAli1688HistoricalOrderProvider failure(String failureCode, String failureMessage) {
        Page page = new Page(List.of());
        page.setFailureCode(failureCode);
        page.setFailureMessage(failureMessage);
        page.setRetryableFailure(Ali1688HistoricalOrderFailureCode.fromCode(failureCode).isRetryable());
        return new FakeAli1688HistoricalOrderProvider(Map.of("", page));
    }

    @Override
    public Page fetchPage(Ali1688HistoricalOrderAuthorizationRow authorization, String cursor) {
        return pagesByCursor.getOrDefault(cursor == null ? "" : cursor, new Page(List.of()));
    }

    private static Page singlePage() {
        OrderItemSnapshot item = new OrderItemSnapshot();
        item.setOfferId("745612345678");
        item.setSkuId("SKU-745612345678-RED");
        item.setTitle("仿真罂粟花束 6 支装 家居装饰");
        item.setSkuText("红色");
        item.setModelText("仿真花束");
        item.setProductCode("彩虹蛋糕");
        item.setSingleProductCode("MX-001");
        item.setQuantity(10);
        item.setUnit("套");
        item.setUnitPriceText("¥12.80");
        item.setAmountText("¥128.00");
        item.setImageUrl("https://cbu01.alicdn.com/img/ibank/2026/001/001/ali-order-item.jpg");
        item.setLogisticsCompany("中通快递(ZTO)");
        item.setTrackingNo("ZTO20260525001");

        OrderSnapshot order = new OrderSnapshot();
        order.setProviderOrderNo("ALI-ORDER-20260525-001");
        order.setOrderTime("2026-05-25 10:30:00");
        order.setPaidAt("2026-05-25 10:31:20");
        order.setBuyerCompanyName("松果果电子商务有限公司");
        order.setBuyerMemberName("沁雪冰菏");
        order.setSupplierName("义乌诚信通源头工厂");
        order.setSellerMemberName("诚信通源头工厂");
        order.setGoodsTotalText("¥128.00");
        order.setFreightText("¥0.00");
        order.setAdjustmentText("¥0.00");
        order.setPaidAmountText("¥128.00");
        order.setAmountText("¥128.00");
        order.setCurrency("CNY");
        order.setOrderStatus("已付款");
        order.setLogisticsStatus("待发货");
        order.setShipperName("商家发货");
        order.setOriginalUrl("https://trade.1688.com/order/new_step_order_detail.htm?orderId=ALI-ORDER-20260525-001");
        order.setReceiverName("梁宇");
        order.setReceiverPhone("13800138000");
        order.setReceiverMobile("13800138000");
        order.setReceiverAddress("浙江省杭州市西湖区文三路 99 号 3 幢 501 室");
        order.setBuyerRemark("周五前发货，联系采购小王");
        order.setSupplierContact("旺旺：supplier-contact");
        order.setInitiatorLoginName("沁雪冰菏");
        order.setItems(List.of(item));
        return new Page(List.of(order));
    }

    private static OrderSnapshot secondOrder() {
        OrderItemSnapshot item = new OrderItemSnapshot();
        item.setOfferId("745612345679");
        item.setSkuId("SKU-745612345679-WHITE");
        item.setTitle("跨境收纳盒 3 件套");
        item.setSkuText("白色");
        item.setQuantity(4);
        item.setUnit("件");
        item.setUnitPriceText("¥22.00");
        item.setAmountText("¥88.00");
        item.setImageUrl("https://cbu01.alicdn.com/img/ibank/2026/002/002/ali-order-item.jpg");
        item.setLogisticsCompany("圆通速递(YTO)");
        item.setTrackingNo("YTO20260524002");

        OrderSnapshot order = new OrderSnapshot();
        order.setProviderOrderNo("ALI-ORDER-20260525-002");
        order.setOrderTime("2026-05-24 16:20:00");
        order.setPaidAt("2026-05-24 16:22:00");
        order.setBuyerCompanyName("松果果电子商务有限公司");
        order.setBuyerMemberName("沁雪冰菏");
        order.setSupplierName("深圳跨境源头供应商");
        order.setSellerMemberName("跨境源头供应商");
        order.setGoodsTotalText("¥88.00");
        order.setFreightText("¥0.00");
        order.setAdjustmentText("¥0.00");
        order.setPaidAmountText("¥88.00");
        order.setAmountText("¥88.00");
        order.setCurrency("CNY");
        order.setOrderStatus("已付款");
        order.setLogisticsStatus("已发货");
        order.setShipperName("商家发货");
        order.setOriginalUrl("https://trade.1688.com/order/new_step_order_detail.htm?orderId=ALI-ORDER-20260525-002");
        order.setInitiatorLoginName("沁雪冰菏");
        order.setItems(List.of(item));
        return order;
    }
}
