package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.CompetitorListingObservationCommand;
import com.nuono.next.competitoranalysis.CompetitorListingObservationRow;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface CompetitorListingObservationMapper {

    default Long nextListingObservationId() {
        return nextCompetitorAnalysisId(
                "operations_competitor_listing_observation",
                280000L
        );
    }

    @Insert({
            "INSERT INTO operations_competitor_analysis_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    int allocateCompetitorAnalysisId(IdSequenceCommand command);

    default Long nextCompetitorAnalysisId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(
                sequenceName,
                initialValue
        );
        allocateCompetitorAnalysisId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException(
                    "竞品列表观察 ID 序列分配失败：" + sequenceName
            );
        }
        return id;
    }

    @Insert({
            "INSERT INTO operations_competitor_listing_observation (",
            "  id, owner_user_id, store_code, site_code, noon_product_code, code_type, fact_date,",
            "  status, acquisition_mode, lease_token, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{noonProductCode}, #{codeType}, #{factDate},",
            "  'RUNNING', 'EXACT_SEARCH', #{leaseToken}, b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")"
    })
    int insertExactClaim(CompetitorListingObservationCommand command);

    @Select({
            "SELECT id, noon_product_code AS noonProductCode, code_type AS codeType, fact_date AS factDate,",
            "  status, acquisition_mode AS acquisitionMode, lease_token AS leaseToken,",
            "  canonical_url AS canonicalUrl, title_en AS titleEn, title_ar AS titleAr, image_url AS imageUrl,",
            "  price_amount AS priceAmount, currency_code AS currencyCode, tags_json AS tagsJson,",
            "  source_url AS sourceUrl, parser_version AS parserVersion,",
            "  provider_http_status AS providerHttpStatus, response_hash AS responseHash, captured_at AS capturedAt,",
            "  last_error_code AS lastErrorCode, last_error_message AS lastErrorMessage",
            "FROM operations_competitor_listing_observation",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}",
            "  AND noon_product_code = #{noonProductCode}",
            "  AND fact_date = #{factDate}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorListingObservationRow selectDaily(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("noonProductCode") String noonProductCode,
            @Param("factDate") LocalDate factDate
    );

    @Update({
            "UPDATE operations_competitor_listing_observation",
            "SET status = 'RUNNING', acquisition_mode = 'EXACT_SEARCH', lease_token = #{leaseToken},",
            "    last_error_code = NULL, last_error_message = NULL,",
            "    updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'",
            "  AND (status = 'FAILED_RETRYABLE'",
            "       OR (status = 'RUNNING' AND gmt_updated < DATE_SUB(NOW(), INTERVAL 15 MINUTE))",
            "       OR (status = 'FOUND' AND acquisition_mode = 'RANK_SCAN'",
            "           AND (NULLIF(TRIM(title_en), '') IS NULL",
            "                OR NULLIF(TRIM(title_ar), '') IS NULL)))"
    })
    int claimRetryableOrStale(CompetitorListingObservationCommand command);

    @Update({
            "UPDATE operations_competitor_listing_observation",
            "SET status = 'FOUND', acquisition_mode = 'EXACT_SEARCH', lease_token = NULL,",
            "    canonical_url = #{canonicalUrl}, title_en = #{titleEn}, title_ar = #{titleAr},",
            "    image_url = #{imageUrl}, price_amount = #{priceAmount}, currency_code = #{currencyCode},",
            "    tags_json = #{tagsJson}, source_url = #{sourceUrl}, parser_version = #{parserVersion},",
            "    provider_http_status = #{providerHttpStatus}, response_hash = #{responseHash},",
            "    captured_at = #{capturedAt}, last_error_code = NULL, last_error_message = NULL,",
            "    updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{leaseToken} AND is_deleted = b'0'"
    })
    int completeExactFound(CompetitorListingObservationCommand command);

    @Update({
            "UPDATE operations_competitor_listing_observation",
            "SET status = 'NOT_FOUND', acquisition_mode = 'EXACT_SEARCH', lease_token = NULL,",
            "    source_url = #{sourceUrl}, provider_http_status = #{providerHttpStatus},",
            "    response_hash = #{responseHash},",
            "    captured_at = #{capturedAt}, last_error_code = #{lastErrorCode},",
            "    last_error_message = #{lastErrorMessage}, updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{leaseToken} AND is_deleted = b'0'"
    })
    int completeExactNotFound(CompetitorListingObservationCommand command);

    @Update({
            "UPDATE operations_competitor_listing_observation",
            "SET status = 'FAILED_RETRYABLE', acquisition_mode = 'EXACT_SEARCH', lease_token = NULL,",
            "    source_url = #{sourceUrl}, provider_http_status = #{providerHttpStatus},",
            "    response_hash = #{responseHash},",
            "    captured_at = #{capturedAt}, last_error_code = #{lastErrorCode},",
            "    last_error_message = #{lastErrorMessage}, updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{leaseToken} AND is_deleted = b'0'"
    })
    int completeExactFailure(CompetitorListingObservationCommand command);

    @Insert({
            "INSERT INTO operations_competitor_listing_observation (",
            "  id, owner_user_id, store_code, site_code, noon_product_code, code_type, fact_date,",
            "  status, acquisition_mode, canonical_url, title_en, title_ar, image_url, price_amount,",
            "  currency_code, tags_json, source_url, parser_version, provider_http_status, response_hash,",
            "  captured_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{noonProductCode}, #{codeType}, #{factDate},",
            "  'FOUND', 'RANK_SCAN', #{canonicalUrl}, #{titleEn}, #{titleAr}, #{imageUrl}, #{priceAmount},",
            "  #{currencyCode}, #{tagsJson}, #{sourceUrl}, #{parserVersion}, #{providerHttpStatus}, #{responseHash},",
            "  #{capturedAt}, b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  acquisition_mode = IF(acquisition_mode = 'EXACT_SEARCH'",
            "                        AND status IN ('RUNNING', 'FOUND'), 'EXACT_SEARCH', 'RANK_SCAN'),",
            "  status = IF(acquisition_mode = 'EXACT_SEARCH' AND status = 'RUNNING', 'RUNNING', 'FOUND'),",
            "  lease_token = IF(acquisition_mode = 'EXACT_SEARCH' AND status = 'RUNNING', lease_token, NULL),",
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
    int upsertRankFound(CompetitorListingObservationCommand command);
}
