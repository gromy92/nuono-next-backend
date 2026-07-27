package com.nuono.next.infrastructure.mapper;

import com.nuono.next.product.ProductImageSuiteAssetRole;
import com.nuono.next.product.ProductImageSuiteRecord;
import com.nuono.next.product.ProductImageSuiteReviewTargetRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductImageSuiteWorkflowMapper {

    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "profile_id", property = "profileId"),
            @Result(column = "suite_status", property = "suiteStatus", javaType = com.nuono.next.product.ProductImageSuiteStatus.class)
    })
    @Select({
            "SELECT id, profile_id, suite_status",
            "FROM product_image_suite",
            "WHERE profile_id = #{profileId} AND deleted = b'0' AND suite_status <> 'DISCARDED'",
            "ORDER BY updated_at DESC, id DESC",
            "LIMIT 1 FOR UPDATE"
    })
    ProductImageSuiteRecord selectLatestSuiteForUpdate(@Param("profileId") Long profileId);

    @Update({
            "UPDATE product_image_suite",
            "SET suite_status = 'PENDING_GENERATION', generation_task_id = #{generationTaskId},",
            "    review_comment = #{comment}, reviewed_by = #{reviewedBy}, reviewed_at = NOW(),",
            "    failure_stage = NULL, failure_reason = NULL, updated_by = #{reviewedBy}, updated_at = NOW()",
            "WHERE id = #{suiteId} AND profile_id = #{profileId}",
            "  AND suite_status IN ('PENDING_REVIEW', 'ADOPTED') AND deleted = b'0'"
    })
    int restartSuiteForRework(
            @Param("suiteId") Long suiteId,
            @Param("profileId") Long profileId,
            @Param("generationTaskId") String generationTaskId,
            @Param("comment") String comment,
            @Param("reviewedBy") Long reviewedBy
    );

    @Update({
            "UPDATE product_image_suite",
            "SET suite_name = #{suiteName}, skin_id = #{skinId}, skin_name = #{skinName}, generation_task_id = #{generationTaskId},",
            "    draft_package_json = #{draftPackageJson}, draft_prompt_text = #{draftPromptText},",
            "    suite_status = 'PENDING_GENERATION', review_comment = NULL, reviewed_by = NULL, reviewed_at = NULL,",
            "    failure_stage = NULL, failure_reason = NULL, published_at = NULL, publish_manifest_json = NULL,",
            "    adopted_at = NULL, updated_by = #{updatedBy}, updated_at = NOW()",
            "WHERE id = #{suiteId} AND profile_id = #{profileId} AND deleted = b'0'",
            "  AND suite_status NOT IN ('PENDING_GENERATION', 'GENERATING', 'REGENERATING', 'PUBLISHING')"
    })
    int restartSuiteGeneration(
            @Param("suiteId") Long suiteId,
            @Param("profileId") Long profileId,
            @Param("suiteName") String suiteName,
            @Param("skinId") Long skinId,
            @Param("skinName") String skinName,
            @Param("generationTaskId") String generationTaskId,
            @Param("draftPackageJson") String draftPackageJson,
            @Param("draftPromptText") String draftPromptText,
            @Param("updatedBy") Long updatedBy
    );

    @Delete("DELETE FROM product_image_suite_review_target WHERE suite_id = #{suiteId}")
    int deleteReviewTargets(@Param("suiteId") Long suiteId);

    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "suite_id", property = "suiteId"),
            @Result(column = "target_scope", property = "targetScope"),
            @Result(column = "asset_id", property = "assetId"),
            @Result(column = "image_role", property = "imageRole", javaType = ProductImageSuiteAssetRole.class),
            @Result(column = "role_ordinal", property = "roleOrdinal")
    })
    @Select({
            "SELECT id, suite_id, target_scope, asset_id, image_role, role_ordinal",
            "FROM product_image_suite_review_target",
            "WHERE suite_id = #{suiteId}",
            "ORDER BY id ASC"
    })
    List<ProductImageSuiteReviewTargetRecord> selectReviewTargets(@Param("suiteId") Long suiteId);

    @Update({
            "UPDATE product_image_suite_asset",
            "SET image_url = #{imageUrl}, content_type = #{contentType}, size_bytes = #{sizeBytes}, sha256 = #{sha256}",
            "WHERE id = #{assetId} AND suite_id = #{suiteId}"
    })
    int updateSuiteAssetContent(
            @Param("suiteId") Long suiteId,
            @Param("assetId") Long assetId,
            @Param("imageUrl") String imageUrl,
            @Param("contentType") String contentType,
            @Param("sizeBytes") Long sizeBytes,
            @Param("sha256") String sha256
    );
}
