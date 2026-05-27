package com.nuono.next.productselection;

public interface Ali1688PricePreviewClient {

    Ali1688PriceProbeResult previewOrder(Ali1688PriceProbeRequest request);

    void clickPayment(Ali1688PriceProbeRequest request);

    void submitPurchase(Ali1688PriceProbeRequest request);

    void sendSupplierMessage(Ali1688PriceProbeRequest request, String message);
}
