package com.nuono.next.operationsconfig;

import com.nuono.next.infrastructure.mapper.ProductLiteMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductClassificationOptionRecord;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOperationConfigProductDimensionRepository implements OperationConfigProductDimensionRepository {

    private final ProductManagementMapper mapper;
    private final ProductLiteMapper productLiteMapper;

    public MyBatisOperationConfigProductDimensionRepository(
            ProductManagementMapper mapper,
            ProductLiteMapper productLiteMapper
    ) {
        this.mapper = mapper;
        this.productLiteMapper = productLiteMapper;
    }

    @Override
    public List<ProductClassificationOptionRecord> listBrandOptions(
            Long ownerUserId,
            String storeCode,
            String query,
            int limit
    ) {
        List<ProductClassificationOptionRecord> dictionary = mapper.selectBrandDictionaryOptions(
                ownerUserId,
                storeCode,
                query,
                limit
        );
        return dictionary.isEmpty()
                ? productLiteMapper.selectBrandProjectionClassificationOptions(ownerUserId, storeCode, query, limit)
                : dictionary;
    }

    @Override
    public List<ProductClassificationOptionRecord> listProductFulltypeOptions(
            Long ownerUserId,
            String storeCode,
            String query,
            int limit
    ) {
        List<ProductClassificationOptionRecord> dictionary = mapper.selectFulltypeDictionaryOptions(
                ownerUserId,
                storeCode,
                query,
                limit
        );
        return dictionary.isEmpty()
                ? productLiteMapper.selectFulltypeProjectionClassificationOptions(ownerUserId, storeCode, query, limit)
                : dictionary;
    }
}
