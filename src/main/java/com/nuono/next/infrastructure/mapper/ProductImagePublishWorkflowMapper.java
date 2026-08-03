package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.product.ProductImageSuiteRecord;
import com.nuono.next.product.ProductImageSuiteStatus;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductImagePublishWorkflowMapper {

    @ResultMap("com.nuono.next.infrastructure.mapper.ProductImageProfileMapper.productImageSuiteMap")
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
            "UPDATE product_image_suite suite",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = suite.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET suite.failure_stage = 'PUBLISH_AUTH_RECOVERED',",
            "    suite.failure_reason = 'Noon 授权已恢复；图片发布不会自动重放，请人工确认后重试一次。',",
            "    suite.updated_at = #{now}",
            "WHERE item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.source_domain = 'PRODUCT_IMAGE_SUITE'",
            "  AND item.resume_policy = 'READBACK_REQUIRED'",
            "  AND item.status = 'PENDING'",
            "  AND suite.suite_status = 'FAILED'",
            "  AND suite.failure_stage = 'PUBLISH_AUTH_RECOVERY'",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(",
            "      IF(JSON_VALID(suite.publish_manifest_json), suite.publish_manifest_json, NULL),",
            "      '$.attemptId'",
            "  )) = item.source_checkpoint",
            "  AND suite.deleted = b'0'",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int markSuiteAuthorizationRecovered(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE product_image_suite suite",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = suite.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET suite.failure_stage = 'PUBLISH_AUTH_FAILED',",
            "    suite.failure_reason = #{diagnostic},",
            "    suite.updated_at = #{now}",
            "WHERE item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.source_domain = 'PRODUCT_IMAGE_SUITE'",
            "  AND item.status = 'PENDING'",
            "  AND suite.suite_status = 'FAILED'",
            "  AND suite.failure_stage = 'PUBLISH_AUTH_RECOVERY'",
            "  AND suite.deleted = b'0'",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int markSuiteAuthorizationRecoveryFailed(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("diagnostic") String diagnostic,
            @Param("now") LocalDateTime now
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
