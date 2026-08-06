package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.advertising.AdvertisingApplyCommand;
import com.nuono.next.datapull.advertising.AdvertisingGenerationHeadRow;
import com.nuono.next.datapull.advertising.AdvertisingGenerationRow;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** O(1) current-generation head for DP-06 read visibility. */
public interface Dp06AdvertisingHeadMapper {
    @Select({
            "SELECT owner_user_id AS ownerUserId,project_code AS projectCode,",
            " store_code AS storeCode,site_code AS siteCode,report_date AS reportDate,",
            " task_id AS taskId,batch_id AS batchId,schedule_slot AS scheduleSlot",
            "FROM dp_pull_advertising_current_head",
            "WHERE owner_user_id=#{command.ownerUserId}",
            " AND BINARY project_code=BINARY #{command.projectCode}",
            " AND BINARY store_code=BINARY #{command.storeCode}",
            " AND BINARY site_code=BINARY #{command.siteCode}",
            " AND report_date=#{command.reportDate} FOR UPDATE"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    AdvertisingGenerationHeadRow selectForUpdate(
            @Param("command") AdvertisingApplyCommand command
    );

    @Insert({
            "INSERT INTO dp_pull_advertising_current_head (",
            " owner_user_id,project_code,store_code,site_code,report_date,task_id,batch_id,",
            " schedule_slot,authority_token_sha256,source_digest_sha256,gmt_create,gmt_updated",
            ") VALUES (#{command.ownerUserId},#{command.projectCode},#{command.storeCode},",
            " #{command.siteCode},#{command.reportDate},#{command.taskId},#{generation.batchId},",
            " #{command.scheduleSlot},#{generation.authorityTokenSha256},",
            " #{generation.digestChainSha256},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            "ON DUPLICATE KEY UPDATE",
            " task_id=IF(VALUES(schedule_slot)>schedule_slot OR",
            "  (VALUES(schedule_slot)=schedule_slot AND VALUES(task_id)>task_id),",
            "  VALUES(task_id),task_id),",
            " batch_id=IF(VALUES(schedule_slot)>schedule_slot OR",
            "  (VALUES(schedule_slot)=schedule_slot AND VALUES(task_id)>=task_id),",
            "  VALUES(batch_id),batch_id),",
            " authority_token_sha256=IF(VALUES(schedule_slot)>schedule_slot OR",
            "  (VALUES(schedule_slot)=schedule_slot AND VALUES(task_id)>=task_id),",
            "  VALUES(authority_token_sha256),authority_token_sha256),",
            " source_digest_sha256=IF(VALUES(schedule_slot)>schedule_slot OR",
            "  (VALUES(schedule_slot)=schedule_slot AND VALUES(task_id)>=task_id),",
            "  VALUES(source_digest_sha256),source_digest_sha256),",
            " schedule_slot=GREATEST(schedule_slot,VALUES(schedule_slot)),gmt_updated=UTC_TIMESTAMP(3)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int upsert(
            @Param("command") AdvertisingApplyCommand command,
            @Param("generation") AdvertisingGenerationRow generation
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_advertising_current_head WHERE task_id=#{taskId}"
    })
    int countForTask(@Param("taskId") long taskId);
}
