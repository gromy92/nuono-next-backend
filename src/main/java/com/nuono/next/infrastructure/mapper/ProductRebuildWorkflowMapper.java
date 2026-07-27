package com.nuono.next.infrastructure.mapper;

import com.nuono.next.product.ProductPublishTaskRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductRebuildWorkflowMapper {

    @Select({
            "SELECT *",
            "FROM product_publish_task",
            "WHERE is_deleted = 0",
            "  AND task_type = 'product-delete'",
            "  AND status = 'synced'",
            "  AND JSON_VALID(request_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(request_json, '$.rebuildAction')) = 'product-rebuild'",
            "  AND (",
            "    result_json IS NULL",
            "    OR NOT JSON_VALID(result_json)",
            "    OR JSON_EXTRACT(result_json, '$.rebuild.status') IS NULL",
            "    OR (",
            "      JSON_UNQUOTE(JSON_EXTRACT(result_json, '$.rebuild.status')) = 'listing_running'",
            "      AND gmt_updated < #{staleBefore}",
            "    )",
            "  )",
            "ORDER BY COALESCE(finished_at, gmt_updated, gmt_create), id",
            "LIMIT #{limit}"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    List<ProductPublishTaskRecord> selectProductRebuildDeleteTasksReadyForListing(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("limit") int limit
    );

    @Select({
            "SELECT *",
            "FROM product_publish_task",
            "WHERE is_deleted = 0",
            "  AND task_type = 'product-delete'",
            "  AND status = 'synced'",
            "  AND JSON_VALID(request_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(request_json, '$.rebuildAction')) = 'product-rebuild'",
            "  AND JSON_VALID(result_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(result_json, '$.rebuild.status')) IN (",
            "    'listing_submitted', 'listing_running', 'listing_already_submitted',",
            "    'noon_auth_required'",
            "  )",
            "ORDER BY COALESCE(gmt_updated, gmt_create), id",
            "LIMIT #{limit}"
    })
    @ResultMap("ProductPublishTaskRecordMap")
    List<ProductPublishTaskRecord> selectProductRebuildDeleteTasksPendingListingReconciliation(
            @Param("limit") int limit
    );

    @Update({
            "UPDATE product_publish_task",
            "SET result_json = #{resultJson},",
            "    updated_by = #{ownerUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND task_type = 'product-delete'",
            "  AND status = 'synced'",
            "  AND JSON_VALID(request_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(request_json, '$.rebuildAction')) = 'product-rebuild'",
            "  AND (",
            "    result_json IS NULL",
            "    OR NOT JSON_VALID(result_json)",
            "    OR JSON_EXTRACT(result_json, '$.rebuild.status') IS NULL",
            "    OR (",
            "      JSON_UNQUOTE(JSON_EXTRACT(result_json, '$.rebuild.status')) = 'listing_running'",
            "      AND gmt_updated < #{staleBefore}",
            "    )",
            "  )",
            "  AND is_deleted = 0"
    })
    int claimProductRebuildDeleteTaskForListing(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("resultJson") String resultJson
    );

    @Update({
            "UPDATE product_publish_task",
            "SET result_json = #{resultJson},",
            "    updated_by = #{ownerUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND task_type = 'product-delete'",
            "  AND status = 'synced'",
            "  AND JSON_VALID(result_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(result_json, '$.rebuild.status')) = 'listing_running'",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(result_json, '$.rebuild.claimToken')) = #{claimToken}",
            "  AND is_deleted = 0"
    })
    int renewProductRebuildListingClaim(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("claimToken") String claimToken,
            @Param("resultJson") String resultJson
    );

    @Update({
            "UPDATE product_publish_task",
            "SET result_json = #{resultJson},",
            "    updated_by = #{ownerUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND task_type = 'product-delete'",
            "  AND status = 'synced'",
            "  AND JSON_VALID(result_json)",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(result_json, '$.rebuild.status')) = 'listing_running'",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(result_json, '$.rebuild.claimToken')) = #{claimToken}",
            "  AND is_deleted = 0"
    })
    int completeProductRebuildListingClaim(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("claimToken") String claimToken,
            @Param("resultJson") String resultJson
    );

    @Update({
            "UPDATE product_publish_task",
            "SET result_json = #{resultJson},",
            "    updated_by = #{ownerUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND task_type = 'product-delete'",
            "  AND status = 'synced'",
            "  AND is_deleted = 0"
    })
    int updateProductRebuildDeleteTaskResult(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("resultJson") String resultJson
    );
}
