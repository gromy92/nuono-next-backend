package com.nuono.next.preorderprofit;

public class PreOrderProfitPurchaseOrderView extends PreOrderProfitPurchaseOrderCommand {
    private Long id;
    private Long itemCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getItemCount() {
        return itemCount;
    }

    public void setItemCount(Long itemCount) {
        this.itemCount = itemCount;
    }
}
