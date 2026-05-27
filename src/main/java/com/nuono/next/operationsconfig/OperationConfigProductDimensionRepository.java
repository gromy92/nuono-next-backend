package com.nuono.next.operationsconfig;

import com.nuono.next.product.ProductClassificationOptionRecord;
import java.util.List;

public interface OperationConfigProductDimensionRepository {

    List<ProductClassificationOptionRecord> listBrandOptions(
            Long ownerUserId,
            String storeCode,
            String query,
            int limit
    );

    List<ProductClassificationOptionRecord> listProductFulltypeOptions(
            Long ownerUserId,
            String storeCode,
            String query,
            int limit
    );
}
