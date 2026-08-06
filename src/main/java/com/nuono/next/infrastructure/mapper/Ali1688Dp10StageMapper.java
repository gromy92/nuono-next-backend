package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10StageItemRow;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FingerprintCountRow;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10StagePageRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Fenced MyBatis Adapter for immutable generation/pass list and detail staging. */
public interface Ali1688Dp10StageMapper {
    String PAGE_COLUMNS = "task_id AS taskId, generation_no AS generationNo,"
            + " scan_pass AS scanPass, partition_name AS partitionName, page_no AS pageNo,"
            + " active_fence_epoch AS activeFenceEpoch, page_size AS pageSize,"
            + " total_record AS totalRecord, expected_pages AS expectedPages,"
            + " raw_row_count AS rawRowCount, state, page_fingerprint AS pageFingerprint";
    String ITEM_COLUMNS = "task_id AS taskId, generation_no AS generationNo,"
            + " scan_pass AS scanPass, partition_name AS partitionName, page_no AS pageNo,"
            + " item_ordinal AS itemOrdinal, provider_order_no AS providerOrderNo,"
            + " provider_modified_at AS providerModifiedAt, state,"
            + " validation_code AS validationCode,"
            + " list_content_fingerprint AS listContentFingerprint,"
            + " content_fingerprint AS contentFingerprint, payload,"
            + " verification_state AS verificationState, apply_state AS applyState,"
            + " apply_item_cursor AS applyItemCursor";

    @Select({
            "SELECT " + PAGE_COLUMNS + " FROM dp_pull_dp10_stage_page",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = #{scanPass} AND partition_name = #{partition}",
            " AND page_no = #{pageNo} FOR UPDATE"
    })
    Ali1688Dp10StagePageRow selectPageForUpdate(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("scanPass") int scanPass,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo
    );

    @Insert({
            "INSERT INTO dp_pull_dp10_stage_page (",
            " task_id, generation_no, scan_pass, partition_name, page_no, active_fence_epoch,",
            " page_size, total_record, expected_pages, raw_row_count, state, page_fingerprint,",
            " gmt_create, gmt_updated) VALUES (#{taskId}, #{generationNo}, #{scanPass},",
            " #{partitionName}, #{pageNo}, #{activeFenceEpoch}, #{pageSize}, #{totalRecord},",
            " #{expectedPages}, #{rawRowCount}, #{state}, #{pageFingerprint},",
            " UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))"
    })
    int insertPage(Ali1688Dp10StagePageRow row);

    @Insert({
            "INSERT INTO dp_pull_dp10_stage_item (",
            " task_id, generation_no, scan_pass, partition_name, page_no, item_ordinal,",
            " provider_order_no, provider_modified_at, state, validation_code,",
            " list_content_fingerprint, content_fingerprint, payload, verification_state,",
            " apply_state, apply_item_cursor, gmt_create, gmt_updated",
            ") VALUES (#{taskId}, #{generationNo}, #{scanPass}, #{partitionName}, #{pageNo},",
            " #{itemOrdinal}, #{providerOrderNo}, #{providerModifiedAt}, #{state},",
            " #{validationCode}, #{listContentFingerprint}, #{contentFingerprint}, #{payload},",
            " #{verificationState}, #{applyState}, #{applyItemCursor},",
            " UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))"
    })
    int insertItem(Ali1688Dp10StageItemRow row);

    @Insert({
            "INSERT INTO dp_pull_dp10_stage_fingerprint_count (",
            " task_id, generation_no, partition_name, list_content_fingerprint,",
            " pass_one_count, pass_two_count, gmt_create, gmt_updated",
            ") VALUES (#{taskId}, #{generationNo}, #{partition}, #{fingerprint},",
            " #{passOneDelta}, #{passTwoDelta}, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
            "ON DUPLICATE KEY UPDATE",
            " pass_one_count = pass_one_count + #{passOneDelta},",
            " pass_two_count = pass_two_count + #{passTwoDelta},",
            " gmt_updated = UTC_TIMESTAMP(3)"
    })
    int upsertFingerprintCount(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("fingerprint") String fingerprint,
            @Param("passOneDelta") long passOneDelta,
            @Param("passTwoDelta") long passTwoDelta
    );

    @Select({
            "<script>",
            "SELECT list_content_fingerprint AS fingerprint,",
            " pass_one_count AS passOneCount, pass_two_count AS passTwoCount",
            "FROM dp_pull_dp10_stage_fingerprint_count",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND partition_name = #{partition}",
            "<if test='afterFingerprint != null'>",
            " AND list_content_fingerprint &gt; #{afterFingerprint}",
            "</if>",
            "ORDER BY list_content_fingerprint ASC LIMIT #{fetchLimit}",
            "</script>"
    })
    List<Ali1688Dp10FingerprintCountRow> selectFingerprintCounts(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("afterFingerprint") String afterFingerprint,
            @Param("fetchLimit") int fetchLimit
    );

    @Select({
            "SELECT " + ITEM_COLUMNS + " FROM dp_pull_dp10_stage_item",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = #{scanPass} AND partition_name = #{partition}",
            " AND page_no = #{pageNo} ORDER BY item_ordinal ASC"
    })
    List<Ali1688Dp10StageItemRow> selectItems(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("scanPass") int scanPass,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo
    );

    @Select({
            "SELECT " + ITEM_COLUMNS + " FROM dp_pull_dp10_stage_item",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = 2 AND state = 'PENDING_DETAIL'",
            "ORDER BY CASE partition_name WHEN 'CURRENT' THEN 0 ELSE 1 END,",
            " page_no ASC, item_ordinal ASC LIMIT 1 FOR UPDATE"
    })
    Ali1688Dp10StageItemRow selectNextPendingForUpdate(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo
    );

    @Select({
            "SELECT " + ITEM_COLUMNS + " FROM dp_pull_dp10_stage_item",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = #{scanPass} AND partition_name = #{partition}",
            " AND page_no = #{pageNo} AND item_ordinal = #{itemOrdinal} FOR UPDATE"
    })
    Ali1688Dp10StageItemRow selectItemForUpdate(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("scanPass") int scanPass,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo,
            @Param("itemOrdinal") int itemOrdinal
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_item SET state = #{state},",
            " validation_code = #{validationCode}, content_fingerprint = #{contentFingerprint},",
            " payload = #{payload}, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = #{scanPass} AND partition_name = #{partitionName}",
            " AND page_no = #{pageNo} AND item_ordinal = #{itemOrdinal}",
            " AND state = 'PENDING_DETAIL'"
    })
    int completeItem(Ali1688Dp10StageItemRow row);

    @Select({
            "SELECT COUNT(*) FROM dp_pull_dp10_stage_item",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = 2 AND partition_name = #{partition}",
            " AND page_no = #{pageNo} AND state = 'PENDING_DETAIL'"
    })
    int countPendingOnPage(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_page SET state = 'READY',",
            " active_fence_epoch = #{fenceEpoch}, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = 2 AND partition_name = #{partition} AND page_no = #{pageNo}",
            " AND state = 'LISTED'"
    })
    int markReady(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_page SET active_fence_epoch = #{fenceEpoch},",
            " gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = #{scanPass} AND partition_name = #{partition}",
            " AND page_no = #{pageNo} AND active_fence_epoch <= #{fenceEpoch}"
    })
    int adoptFence(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("scanPass") int scanPass,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo,
            @Param("fenceEpoch") long fenceEpoch
    );

}
