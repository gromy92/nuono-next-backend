package com.nuono.next.product;

import java.util.List;

public class ProductImageAssetBatchRemoveCommand {
    private List<ProductImageAssetRemoveItemCommand> assets;

    public List<ProductImageAssetRemoveItemCommand> getAssets() {
        return assets;
    }

    public void setAssets(List<ProductImageAssetRemoveItemCommand> assets) {
        this.assets = assets;
    }
}
