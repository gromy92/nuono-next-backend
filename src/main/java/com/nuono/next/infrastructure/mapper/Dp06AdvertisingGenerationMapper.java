package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.advertising.AdvertisingApplyCommand;
import com.nuono.next.datapull.advertising.AdvertisingFactChunk;
import com.nuono.next.datapull.advertising.AdvertisingGenerationRow;
import com.nuono.next.datapull.advertising.AdvertisingGenerationSeed;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Durable DP-06 generation cursor; every mutation is one fenced bounded transaction step. */
public interface Dp06AdvertisingGenerationMapper {
    @Insert({
            "INSERT IGNORE INTO dp_pull_advertising_generation (",
            " task_id,active_fence_epoch,state,owner_user_id,project_code,store_code,site_code,",
            " report_date,schedule_slot,business_window_key,authority_token_sha256,",
            " active_campaign_digest_sha256,provider_as_of_utc,",
            " declared_campaign_count,active_campaign_count,campaign_page_count,last_page,",
            " staged_campaign_item_count,campaign_business_skipped_item_count,",
            " staged_item_count,source_item_count,business_skipped_item_count,",
            " cursor_page_no,cursor_item_ordinal,processed_item_count,campaign_fact_count,",
            " query_fact_count,identity_skipped_item_count,campaign_identity_skipped_item_count,",
            " query_page_proof_count,matched_active_campaign_count,",
            " batch_id,campaign_id_start,query_id_start,digest_chain_sha256,gmt_create,gmt_updated",
            ") VALUES (",
            " #{seed.command.taskId},#{seed.command.fenceEpoch},'PREPARING',",
            " #{seed.command.ownerUserId},#{seed.command.projectCode},#{seed.command.storeCode},",
            " #{seed.command.siteCode},#{seed.command.reportDate},#{seed.command.scheduleSlot},",
            " #{seed.command.businessWindowKey},#{seed.command.authority.generationTokenSha256},",
            " #{seed.activeCampaignDigestSha256},",
            " #{seed.command.authority.providerAsOfUtc},",
            " #{seed.command.authority.declaredCampaignCount},",
            " #{seed.command.activeCampaignCount},#{seed.command.campaignPageCount},",
            " #{seed.manifest.knownLastPage},#{seed.manifest.campaignItemCount},",
            " #{seed.manifest.campaignBusinessSkippedItemCount},",
            " #{seed.manifest.stagedItemCount},#{seed.manifest.sourceItemCount},",
            " #{seed.manifest.businessSkippedItemCount},0,-1,0,0,0,0,0,0,0,",
            " #{seed.batchId},#{seed.campaignIdStart},#{seed.queryIdStart},",
            " #{seed.digestChainSha256},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int insertIfAbsent(@Param("seed") AdvertisingGenerationSeed seed);

    @Select({
            "SELECT task_id AS taskId,active_fence_epoch AS activeFenceEpoch,state,",
            " owner_user_id AS ownerUserId,project_code AS projectCode,store_code AS storeCode,",
            " site_code AS siteCode,report_date AS reportDate,schedule_slot AS scheduleSlot,",
            " business_window_key AS businessWindowKey,",
            " authority_token_sha256 AS authorityTokenSha256,",
            " active_campaign_digest_sha256 AS activeCampaignDigestSha256,",
            " provider_as_of_utc AS providerAsOfUtc,",
            " declared_campaign_count AS declaredCampaignCount,",
            " active_campaign_count AS activeCampaignCount,",
            " campaign_page_count AS campaignPageCount,last_page AS lastPage,",
            " staged_campaign_item_count AS stagedCampaignItemCount,",
            " campaign_business_skipped_item_count AS campaignBusinessSkippedItemCount,",
            " staged_item_count AS stagedItemCount,source_item_count AS sourceItemCount,",
            " business_skipped_item_count AS businessSkippedItemCount,",
            " cursor_page_no AS cursorPageNo,cursor_item_ordinal AS cursorItemOrdinal,",
            " processed_item_count AS processedItemCount,campaign_fact_count AS campaignFactCount,",
            " query_fact_count AS queryFactCount,",
            " identity_skipped_item_count AS identitySkippedItemCount,",
            " campaign_identity_skipped_item_count AS campaignIdentitySkippedItemCount,",
            " query_page_proof_count AS queryPageProofCount,",
            " matched_active_campaign_count AS matchedActiveCampaignCount,",
            " batch_id AS batchId,campaign_id_start AS campaignIdStart,",
            " query_id_start AS queryIdStart,digest_chain_sha256 AS digestChainSha256",
            "FROM dp_pull_advertising_generation WHERE task_id=#{taskId} FOR UPDATE"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    AdvertisingGenerationRow selectForUpdate(@Param("taskId") long taskId);

    @Update({
            "UPDATE dp_pull_advertising_generation",
            "SET active_fence_epoch=#{fenceEpoch},gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE task_id=#{taskId} AND active_fence_epoch<#{fenceEpoch}",
            " AND state='PREPARING'"
    })
    int adoptFence(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Update({
            "UPDATE dp_pull_advertising_generation SET",
            " cursor_page_no=#{chunk.lastPageNo},",
            " cursor_item_ordinal=#{chunk.lastItemOrdinal},",
            " processed_item_count=processed_item_count+#{chunk.processedCount},",
            " campaign_fact_count=campaign_fact_count+#{chunk.campaigns.size},",
            " query_fact_count=query_fact_count+#{chunk.queries.size},",
            " identity_skipped_item_count=identity_skipped_item_count+#{chunk.skippedIdentityCount},",
            " campaign_identity_skipped_item_count=campaign_identity_skipped_item_count",
            "  +#{chunk.campaignSkippedIdentityCount},",
            " query_page_proof_count=query_page_proof_count+#{chunk.queryPageProofCount},",
            " matched_active_campaign_count=matched_active_campaign_count+#{chunk.matchedActiveCount},",
            " digest_chain_sha256=#{chunk.digestChainSha256},gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE task_id=#{command.taskId} AND active_fence_epoch=#{command.fenceEpoch}",
            " AND state='PREPARING' AND cursor_page_no=#{generation.cursorPageNo}",
            " AND cursor_item_ordinal=#{generation.cursorItemOrdinal}"
    })
    int advance(
            @Param("command") AdvertisingApplyCommand command,
            @Param("generation") AdvertisingGenerationRow generation,
            @Param("chunk") AdvertisingFactChunk chunk
    );

    @Update({
            "UPDATE dp_pull_advertising_generation",
            "SET state='SEALED',sealed_fence_epoch=#{command.fenceEpoch},",
            " source_digest_sha256=digest_chain_sha256,sealed_at=UTC_TIMESTAMP(3),",
            " gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE task_id=#{command.taskId} AND active_fence_epoch=#{command.fenceEpoch}",
            " AND state='PREPARING' AND processed_item_count=staged_item_count",
            " AND campaign_fact_count+campaign_identity_skipped_item_count",
            "  =staged_campaign_item_count",
            " AND staged_campaign_item_count+campaign_business_skipped_item_count",
            "  =declared_campaign_count",
            " AND query_page_proof_count=active_campaign_count",
            " AND source_item_count-active_campaign_count-declared_campaign_count",
            "  =query_fact_count+identity_skipped_item_count",
            "   -campaign_identity_skipped_item_count+business_skipped_item_count",
            "   -campaign_business_skipped_item_count",
            " AND campaign_fact_count+query_fact_count+identity_skipped_item_count",
            "  +query_page_proof_count=staged_item_count"
    })
    int seal(@Param("command") AdvertisingApplyCommand command);

    @Delete({
            "DELETE FROM dp_pull_advertising_generation WHERE task_id=#{taskId}",
            " AND state='PREPARING'",
            " AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_campaign_fact c",
            "  WHERE c.task_id=dp_pull_advertising_generation.task_id)",
            " AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_query_fact q",
            "  WHERE q.task_id=dp_pull_advertising_generation.task_id)"
    })
    int deletePreparingIfEmpty(@Param("taskId") long taskId);
}
