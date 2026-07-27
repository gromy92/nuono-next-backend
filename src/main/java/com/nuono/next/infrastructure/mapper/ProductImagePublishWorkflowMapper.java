package com.nuono.next.infrastructure.mapper;

import com.nuono.next.product.ProductImageSuiteRecord;
import com.nuono.next.product.ProductImageSuiteStatus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductImagePublishWorkflowMapper {

    @ResultMap("productImageSuiteMap")
    @Select({
            "SELECT *",
            "FROM product_image_suite",
            "WHERE id = #{suiteId}",
            "  AND profile_id = #{profileId}",
            "  AND deleted = b'0'",
            "LIMIT 1 FOR UPDATE"
    })
    ProductImageSuiteRecord selectSuiteByIdForUpdate(
            @Param("suiteId") Long suiteId,
            @Param("profileId") Long profileId
    );

    @Update({
            "UPDATE product_image_suite",
            "SET suite_status = 'PUBLISHING',",
            "    publish_manifest_json = #{manifestJson},",
            "    review_comment = NULL,",
            "    reviewed_by = #{updatedBy},",
            "    reviewed_at = NOW(),",
            "    failure_stage = NULL,",
            "    failure_reason = NULL,",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{suiteId}",
            "  AND profile_id = #{profileId}",
            "  AND suite_status IN ('PENDING_REVIEW', 'ADOPTED')",
            "  AND deleted = b'0'"
    })
    int startSuitePublishWorkflow(
            @Param("suiteId") Long suiteId,
            @Param("profileId") Long profileId,
            @Param("manifestJson") String manifestJson,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_suite",
            "SET publish_manifest_json = JSON_SET(",
            "      publish_manifest_json,",
            "      '$.executionToken', #{executionToken},",
            "      '$.executionClaimedAt', DATE_FORMAT(NOW(), '%Y-%m-%dT%H:%i:%s')",
            "    ),",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{suiteId}",
            "  AND suite_status = 'PUBLISHING'",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "      IF(JSON_VALID(publish_manifest_json), publish_manifest_json, NULL),",
            "      '$.attemptId'",
            "  )) = #{attemptId}",
            "  AND NULLIF(JSON_UNQUOTE(JSON_EXTRACT(",
            "      IF(JSON_VALID(publish_manifest_json), publish_manifest_json, NULL),",
            "      '$.executionToken'",
            "  )), '') IS NULL",
            "  AND deleted = b'0'"
    })
    int claimSuitePublishExecution(
            @Param("suiteId") Long suiteId,
            @Param("attemptId") String attemptId,
            @Param("executionToken") String executionToken,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_suite",
            "SET publish_manifest_json = JSON_SET(",
            "      #{manifestJson}, '$.executionToken', #{executionToken}",
            "    ),",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{suiteId}",
            "  AND suite_status = 'PUBLISHING'",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "      IF(JSON_VALID(publish_manifest_json), publish_manifest_json, NULL),",
            "      '$.attemptId'",
            "  )) = #{attemptId}",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "      IF(JSON_VALID(publish_manifest_json), publish_manifest_json, NULL),",
            "      '$.executionToken'",
            "  )) = #{executionToken}",
            "  AND deleted = b'0'"
    })
    int updateSuitePublishManifest(
            @Param("suiteId") Long suiteId,
            @Param("attemptId") String attemptId,
            @Param("executionToken") String executionToken,
            @Param("manifestJson") String manifestJson,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_suite",
            "SET suite_status = #{status},",
            "    failure_stage = NULL,",
            "    failure_reason = NULL,",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{suiteId}",
            "  AND profile_id = #{profileId}",
            "  AND suite_status = 'FAILED'",
            "  AND deleted = b'0'"
    })
    int retryFailedSuiteWorkflow(
            @Param("suiteId") Long suiteId,
            @Param("profileId") Long profileId,
            @Param("status") ProductImageSuiteStatus status,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_suite",
            "SET suite_status = 'PUBLISHING',",
            "    publish_manifest_json = #{manifestJson},",
            "    failure_stage = NULL,",
            "    failure_reason = NULL,",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{suiteId}",
            "  AND profile_id = #{profileId}",
            "  AND suite_status = 'FAILED'",
            "  AND deleted = b'0'"
    })
    int retryFailedSuitePublishWorkflow(
            @Param("suiteId") Long suiteId,
            @Param("profileId") Long profileId,
            @Param("manifestJson") String manifestJson,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_suite",
            "SET suite_status = 'FAILED',",
            "    failure_stage = #{failureStage},",
            "    failure_reason = #{failureReason},",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{suiteId}",
            "  AND suite_status = 'PUBLISHING'",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "      IF(JSON_VALID(publish_manifest_json), publish_manifest_json, NULL),",
            "      '$.attemptId'",
            "  )) = #{attemptId}",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "      IF(JSON_VALID(publish_manifest_json), publish_manifest_json, NULL),",
            "      '$.executionToken'",
            "  )) = #{executionToken}",
            "  AND deleted = b'0'"
    })
    int failPublishingSuiteWorkflow(
            @Param("suiteId") Long suiteId,
            @Param("attemptId") String attemptId,
            @Param("executionToken") String executionToken,
            @Param("failureStage") String failureStage,
            @Param("failureReason") String failureReason,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_suite",
            "SET suite_status = 'ONLINE',",
            "    published_at = NOW(),",
            "    publish_manifest_json = #{manifestJson},",
            "    failure_stage = NULL,",
            "    failure_reason = NULL,",
            "    updated_at = NOW()",
            "WHERE id = #{suiteId}",
            "  AND suite_status = 'PUBLISHING'",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "      IF(JSON_VALID(publish_manifest_json), publish_manifest_json, NULL),",
            "      '$.attemptId'",
            "  )) = #{attemptId}",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "      IF(JSON_VALID(publish_manifest_json), publish_manifest_json, NULL),",
            "      '$.executionToken'",
            "  )) = #{executionToken}",
            "  AND deleted = b'0'"
    })
    int markSuiteOnline(
            @Param("suiteId") Long suiteId,
            @Param("attemptId") String attemptId,
            @Param("executionToken") String executionToken,
            @Param("manifestJson") String manifestJson
    );

    @Update({
            "UPDATE product_image_suite",
            "SET suite_status = 'FAILED',",
            "    failure_stage = 'PUBLISH_STALE_RECOVERY',",
            "    failure_reason = '图片发布进程中断，请人工重试；重试会先回读 Noon。',",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE suite_status = 'PUBLISHING'",
            "  AND updated_at < DATE_SUB(NOW(), INTERVAL #{staleMinutes} MINUTE)",
            "  AND deleted = b'0'"
    })
    int failStalePublishingSuites(
            @Param("staleMinutes") int staleMinutes,
            @Param("updatedBy") Long updatedBy
    );
}
