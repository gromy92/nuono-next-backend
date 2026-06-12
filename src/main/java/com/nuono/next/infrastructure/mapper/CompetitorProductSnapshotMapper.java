package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.CompetitorProductChangeEventCommand;
import com.nuono.next.competitoranalysis.CompetitorProductSnapshotCommand;
import com.nuono.next.competitoranalysis.CompetitorProductSnapshotRow;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface CompetitorProductSnapshotMapper {

    @Insert({
            "INSERT INTO operations_competitor_analysis_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateCompetitorAnalysisId(IdSequenceCommand command);

    default Long nextCompetitorAnalysisId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateCompetitorAnalysisId(command);
        return command.getAllocatedId();
    }

    default Long nextProductSnapshotId() {
        return nextCompetitorAnalysisId("operations_competitor_product_snapshot", 260000L);
    }

    default Long nextProductChangeEventId() {
        return nextCompetitorAnalysisId("operations_competitor_product_change_event", 270000L);
    }

    @Select({
            "SELECT id, title_en AS titleEn, brand, price_amount AS priceAmount, currency_code AS currencyCode,",
            "  rating, review_count AS reviewCount, main_image_url_normalized AS mainImageUrlNormalized,",
            "  main_image_asset_key AS mainImageAssetKey",
            "FROM operations_competitor_product_snapshot",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND subject_type = #{subjectType}",
            "  AND noon_product_code = #{noonProductCode}",
            "  AND fact_date = #{factDate}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorProductSnapshotRow selectDailySnapshot(
            @Param("watchProductId") Long watchProductId,
            @Param("subjectType") String subjectType,
            @Param("noonProductCode") String noonProductCode,
            @Param("factDate") LocalDate factDate
    );

    @Select({
            "SELECT id, title_en AS titleEn, brand, price_amount AS priceAmount, currency_code AS currencyCode,",
            "  rating, review_count AS reviewCount, main_image_url_normalized AS mainImageUrlNormalized,",
            "  main_image_asset_key AS mainImageAssetKey",
            "FROM operations_competitor_product_snapshot",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND subject_type = #{subjectType}",
            "  AND noon_product_code = #{noonProductCode}",
            "  AND fact_date < #{factDate}",
            "  AND is_deleted = b'0'",
            "ORDER BY fact_date DESC, id DESC",
            "LIMIT 1"
    })
    CompetitorProductSnapshotRow selectPreviousSnapshot(
            @Param("watchProductId") Long watchProductId,
            @Param("subjectType") String subjectType,
            @Param("noonProductCode") String noonProductCode,
            @Param("factDate") LocalDate factDate
    );

    @Insert({
            "INSERT INTO operations_competitor_product_snapshot (",
            "  id, owner_user_id, watch_product_id, competitor_product_id, subject_type, site_code,",
            "  noon_product_code, code_type, fact_date, captured_at, source_run_id, detail_url,",
            "  title_en, brand, price_amount, currency_code, rating, review_count, main_image_url_raw,",
            "  main_image_url_normalized, main_image_asset_key, snapshot_hash, raw_detail_json,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{watchProductId}, #{competitorProductId}, #{subjectType}, #{siteCode},",
            "  #{noonProductCode}, #{codeType}, #{factDate}, #{capturedAt}, #{sourceRunId}, #{detailUrl},",
            "  #{titleEn}, #{brand}, #{priceAmount}, #{currencyCode}, #{rating}, #{reviewCount}, #{mainImageUrlRaw},",
            "  #{mainImageUrlNormalized}, #{mainImageAssetKey}, #{snapshotHash}, #{rawDetailJson},",
            "  b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")"
    })
    int insertProductSnapshot(CompetitorProductSnapshotCommand command);

    @Update({
            "UPDATE operations_competitor_product_snapshot",
            "SET competitor_product_id = #{competitorProductId},",
            "    captured_at = #{capturedAt}, source_run_id = #{sourceRunId}, detail_url = #{detailUrl},",
            "    title_en = #{titleEn}, brand = #{brand}, price_amount = #{priceAmount}, currency_code = #{currencyCode},",
            "    rating = #{rating}, review_count = #{reviewCount}, main_image_url_raw = #{mainImageUrlRaw},",
            "    main_image_url_normalized = #{mainImageUrlNormalized}, main_image_asset_key = #{mainImageAssetKey},",
            "    snapshot_hash = #{snapshotHash}, raw_detail_json = #{rawDetailJson},",
            "    updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE id = #{id}"
    })
    int updateProductSnapshot(CompetitorProductSnapshotCommand command);

    @Update({
            "UPDATE operations_competitor_product_change_event",
            "SET is_deleted = b'1', updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE snapshot_id = #{snapshotId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteChangeEventsBySnapshotId(
            @Param("snapshotId") Long snapshotId,
            @Param("actorUserId") Long actorUserId
    );

    @Insert({
            "INSERT INTO operations_competitor_product_change_event (",
            "  id, snapshot_id, previous_snapshot_id, owner_user_id, watch_product_id, competitor_product_id,",
            "  subject_type, site_code, noon_product_code, fact_date, field_key, field_label, change_type,",
            "  old_value_json, new_value_json, severity, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{snapshotId}, #{previousSnapshotId}, #{ownerUserId}, #{watchProductId}, #{competitorProductId},",
            "  #{subjectType}, #{siteCode}, #{noonProductCode}, #{factDate}, #{fieldKey}, #{fieldLabel}, #{changeType},",
            "  #{oldValueJson}, #{newValueJson}, #{severity}, b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")"
    })
    int insertProductChangeEvent(CompetitorProductChangeEventCommand command);
}
