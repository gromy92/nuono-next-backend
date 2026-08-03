package com.nuono.next.infrastructure.mapper;

import com.nuono.next.product.ProductPublishTaskRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

public interface ProductDeleteTaskSubmissionMapper extends ProductPublishRetryMapper {

    @Select({
            "SELECT *",
            "FROM product_publish_task",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = 0",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    @ResultMap("com.nuono.next.infrastructure.mapper.ProductManagementMapper.ProductPublishTaskRecordMap")
    ProductPublishTaskRecord selectLatestProductPublishTask(
            @Param("productMasterId") Long productMasterId
    );
}
