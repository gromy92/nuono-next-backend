package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.CompetitorListingObservationCommand;
import com.nuono.next.competitoranalysis.dp08.Dp08TaskFenceRow;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** DP-08-specific fence/idempotency statements; no second runtime task ledger. */
public interface Dp08RuntimeMapper {

    @Select({
            "SELECT id, operation_code AS operationCode, state, fence_epoch AS fenceEpoch,",
            "       lease_owner AS leaseOwner, lease_until AS leaseUntil",
            "FROM dp_pull_task",
            "WHERE id = #{taskId}",
            "LIMIT 1 FOR UPDATE"
    })
    Dp08TaskFenceRow lockRuntimeTask(@Param("taskId") long taskId);

    @Select({
            "SELECT COUNT(1)",
            "FROM dp_pull_task",
            "WHERE id = #{taskId}",
            "  AND state = 'RUNNING'",
            "  AND fence_epoch = #{fenceEpoch}",
            "  AND BINARY lease_owner = BINARY #{leaseOwner}",
            "  AND lease_until > UTC_TIMESTAMP(3)"
    })
    int countLiveRuntimeTask(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("leaseOwner") String leaseOwner
    );

    @Select({
            "SELECT kr.id",
            "FROM operations_competitor_keyword_run kr",
            "JOIN operations_competitor_search_run sr",
            "  ON sr.id = kr.search_run_id",
            " AND sr.trigger_mode = 'DP08_RUNTIME_RANK'",
            " AND sr.status = 'SUCCEEDED' AND sr.is_deleted = b'0'",
            "WHERE kr.keyword_id = #{keywordId}",
            "  AND kr.provider_status = 'SUCCESS'",
            "  AND kr.requested_result_limit = 200",
            "  AND kr.captured_at = #{scheduleSlotShanghai}",
            "  AND kr.is_deleted = b'0'",
            "ORDER BY kr.id ASC LIMIT 1"
    })
    Long selectAppliedKeywordRun(
            @Param("keywordId") long keywordId,
            @Param("scheduleSlotShanghai") LocalDateTime scheduleSlotShanghai
    );

    @Update({
            "UPDATE operations_competitor_search_run",
            "SET status = 'SUCCEEDED', finished_at = NOW(), keyword_success = 1, keyword_failed = 0,",
            "    candidate_upserted_count = #{candidateCount},",
            "    rank_fact_written_count = #{rankFactCount},",
            "    error_code = NULL, error_message = NULL, gmt_updated = NOW()",
            "WHERE id = #{runId} AND trigger_mode = 'DP08_RUNTIME_RANK'",
            "  AND status = 'RUNNING' AND is_deleted = b'0'"
    })
    int completeRankSearchRun(
            @Param("runId") long runId,
            @Param("candidateCount") int candidateCount,
            @Param("rankFactCount") int rankFactCount
    );

    @Insert({
            "INSERT INTO operations_competitor_listing_observation (",
            "  id, owner_user_id, store_code, site_code, noon_product_code, code_type, fact_date,",
            "  status, acquisition_mode, canonical_url, title_en, title_ar, image_url, price_amount,",
            "  currency_code, tags_json, source_url, parser_version, provider_http_status, response_hash,",
            "  captured_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{noonProductCode}, #{codeType}, #{factDate},",
            "  'FOUND', 'EXACT_SEARCH', #{canonicalUrl}, #{titleEn}, #{titleAr}, #{imageUrl}, #{priceAmount},",
            "  #{currencyCode}, #{tagsJson}, #{sourceUrl}, #{parserVersion}, #{providerHttpStatus}, #{responseHash},",
            "  #{capturedAt}, b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  status = 'FOUND', acquisition_mode = 'EXACT_SEARCH', lease_token = NULL,",
            "  canonical_url = COALESCE(VALUES(canonical_url), canonical_url),",
            "  title_en = COALESCE(VALUES(title_en), title_en),",
            "  title_ar = COALESCE(VALUES(title_ar), title_ar),",
            "  image_url = COALESCE(VALUES(image_url), image_url),",
            "  price_amount = COALESCE(VALUES(price_amount), price_amount),",
            "  currency_code = COALESCE(VALUES(currency_code), currency_code),",
            "  tags_json = COALESCE(VALUES(tags_json), tags_json),",
            "  source_url = VALUES(source_url), parser_version = VALUES(parser_version),",
            "  provider_http_status = VALUES(provider_http_status), response_hash = VALUES(response_hash),",
            "  captured_at = VALUES(captured_at), last_error_code = NULL, last_error_message = NULL,",
            "  updated_by = VALUES(updated_by), gmt_updated = NOW()"
    })
    int upsertListFound(CompetitorListingObservationCommand command);

    @Insert({
            "INSERT INTO operations_competitor_listing_observation (",
            "  id, owner_user_id, store_code, site_code, noon_product_code, code_type, fact_date,",
            "  status, acquisition_mode, source_url, provider_http_status, response_hash, captured_at,",
            "  last_error_code, last_error_message, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{noonProductCode}, #{codeType}, #{factDate},",
            "  'NOT_FOUND', 'EXACT_SEARCH', #{sourceUrl}, #{providerHttpStatus}, #{responseHash}, #{capturedAt},",
            "  'LIST_PRODUCT_NOT_FOUND', 'Exact list search returned no matching product code',",
            "  b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  status = 'NOT_FOUND', acquisition_mode = 'EXACT_SEARCH', lease_token = NULL,",
            "  source_url = VALUES(source_url), provider_http_status = VALUES(provider_http_status),",
            "  response_hash = VALUES(response_hash), captured_at = VALUES(captured_at),",
            "  last_error_code = 'LIST_PRODUCT_NOT_FOUND',",
            "  last_error_message = 'Exact list search returned no matching product code',",
            "  updated_by = VALUES(updated_by), gmt_updated = NOW()"
    })
    int upsertListNotFound(CompetitorListingObservationCommand command);
}
