package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.advertising.AdvertisingGenerationFactRow;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Writes at most one bounded chunk into an invisible DP-06 generation. */
public interface Dp06AdvertisingGenerationFactMapper {
    @Select({
            "<script>",
            "SELECT normalized_identity FROM dp_pull_advertising_campaign_fact",
            "WHERE task_id=#{taskId} AND normalized_identity IN",
            "<foreach collection='identities' item='value' open='(' separator=',' close=')'>",
            " #{value}",
            "</foreach>",
            "UNION ALL",
            "SELECT normalized_identity FROM dp_pull_advertising_query_fact",
            "WHERE task_id=#{taskId} AND normalized_identity IN",
            "<foreach collection='identities' item='value' open='(' separator=',' close=')'>",
            " #{value}",
            "</foreach>",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    List<String> selectExistingIdentities(
            @Param("taskId") long taskId,
            @Param("identities") List<String> identities
    );

    @Insert({
            "<script>",
            "INSERT INTO dp_pull_advertising_campaign_fact (",
            " task_id,page_no,item_ordinal,normalized_identity,content_fingerprint,",
            " id,batch_id,source_system,owner_user_id,project_code,store_code,site_code,",
            " report_date_from,report_date_to,campaign_code,campaign_name,campaign_status,",
            " qc_status,adgroup_code,campaign_start_date,campaign_end_date,views,clicks,",
            " orders_count,assisted_orders,atc_count,spend_amount,ad_revenue,ctr_percentage,",
            " roas,cpc,cps,cvr_percentage,raw_payload_json,gmt_create,gmt_updated",
            ") VALUES",
            "<foreach collection='rows' item='row' separator=','>",
            " (#{row.taskId},#{row.pageNo},#{row.itemOrdinal},#{row.normalizedIdentity},",
            " #{row.contentFingerprint},#{row.campaign.id},#{row.campaign.batchId},",
            " #{row.campaign.sourceSystem},#{row.campaign.ownerUserId},",
            " #{row.campaign.projectCode},#{row.campaign.storeCode},#{row.campaign.siteCode},",
            " #{row.campaign.reportDateFrom},#{row.campaign.reportDateTo},",
            " #{row.campaign.campaignCode},#{row.campaign.campaignName},",
            " #{row.campaign.campaignStatus},#{row.campaign.qcStatus},",
            " #{row.campaign.adgroupCode},#{row.campaign.campaignStartDate},",
            " #{row.campaign.campaignEndDate},#{row.campaign.views},#{row.campaign.clicks},",
            " #{row.campaign.ordersCount},#{row.campaign.assistedOrders},",
            " #{row.campaign.atcCount},#{row.campaign.spendAmount},",
            " #{row.campaign.adRevenue},#{row.campaign.ctrPercentage},#{row.campaign.roas},",
            " #{row.campaign.cpc},#{row.campaign.cps},#{row.campaign.cvrPercentage},",
            " #{row.campaign.rawPayloadJson},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            "</foreach>",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int insertCampaigns(@Param("rows") List<AdvertisingGenerationFactRow> rows);

    @Insert({
            "<script>",
            "INSERT INTO dp_pull_advertising_query_fact (",
            " task_id,page_no,item_ordinal,normalized_identity,content_fingerprint,",
            " id,batch_id,source_system,owner_user_id,project_code,store_code,site_code,",
            " report_date_from,report_date_to,campaign_code,campaign_name,ad_sku_code,",
            " partner_sku,query_text,query_hash,query_kind,views,clicks,orders_count,",
            " assisted_orders,atc_count,spend_amount,ad_revenue,ctr_percentage,roas,cpc,cps,",
            " cvr_percentage,raw_payload_json,gmt_create,gmt_updated",
            ") VALUES",
            "<foreach collection='rows' item='row' separator=','>",
            " (#{row.taskId},#{row.pageNo},#{row.itemOrdinal},#{row.normalizedIdentity},",
            " #{row.contentFingerprint},#{row.query.id},#{row.query.batchId},",
            " #{row.query.sourceSystem},#{row.query.ownerUserId},#{row.query.projectCode},",
            " #{row.query.storeCode},#{row.query.siteCode},#{row.query.reportDateFrom},",
            " #{row.query.reportDateTo},#{row.query.campaignCode},#{row.query.campaignName},",
            " #{row.query.adSkuCode},#{row.query.partnerSku},#{row.query.queryText},",
            " #{row.query.queryHash},#{row.query.queryKind},#{row.query.views},",
            " #{row.query.clicks},#{row.query.ordersCount},#{row.query.assistedOrders},",
            " #{row.query.atcCount},#{row.query.spendAmount},#{row.query.adRevenue},",
            " #{row.query.ctrPercentage},#{row.query.roas},#{row.query.cpc},#{row.query.cps},",
            " #{row.query.cvrPercentage},#{row.query.rawPayloadJson},",
            " UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            "</foreach>",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int insertQueries(@Param("rows") List<AdvertisingGenerationFactRow> rows);

    @Delete({
            "DELETE FROM dp_pull_advertising_query_fact WHERE task_id=#{taskId}",
            "ORDER BY page_no ASC,item_ordinal ASC LIMIT #{limit}"
    })
    int deleteQueriesBatch(
            @Param("taskId") long taskId,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_advertising_campaign_fact WHERE task_id=#{taskId}",
            "ORDER BY page_no ASC,item_ordinal ASC LIMIT #{limit}"
    })
    int deleteCampaignsBatch(
            @Param("taskId") long taskId,
            @Param("limit") int limit
    );

}
