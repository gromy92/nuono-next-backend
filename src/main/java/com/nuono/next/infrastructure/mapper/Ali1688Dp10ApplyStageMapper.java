package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ApplySlice;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10StageItemRow;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10StagePageRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Bounded VERIFY/APPLY locators for one sealed DP-10 generation. */
public interface Ali1688Dp10ApplyStageMapper {
    @Select({
            "SELECT " + Ali1688Dp10StageMapper.PAGE_COLUMNS,
            "FROM dp_pull_dp10_stage_page WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2",
            " AND state IN ('READY', 'VERIFYING')",
            "ORDER BY CASE partition_name WHEN 'CURRENT' THEN 0 ELSE 1 END, page_no ASC",
            "LIMIT 1 FOR UPDATE"
    })
    Ali1688Dp10StagePageRow selectNextVerificationPageForUpdate(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo
    );

    @Select({
            "SELECT " + Ali1688Dp10StageMapper.ITEM_COLUMNS,
            "FROM dp_pull_dp10_stage_item WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2",
            " AND partition_name = #{partition} AND page_no = #{pageNo}",
            "ORDER BY item_ordinal ASC FOR UPDATE"
    })
    java.util.List<Ali1688Dp10StageItemRow> selectPageItemsForUpdate(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_page SET state = 'VERIFYING',",
            " active_fence_epoch = #{fenceEpoch}, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = 2 AND partition_name = #{partition} AND page_no = #{pageNo}",
            " AND state = 'READY' AND active_fence_epoch <= #{fenceEpoch}"
    })
    int markPageVerifying(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Select({
            "SELECT " + Ali1688Dp10StageMapper.ITEM_COLUMNS,
            "FROM dp_pull_dp10_stage_item WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2",
            " AND partition_name = #{partition} AND page_no = #{pageNo}",
            " AND verification_state = 'PENDING' ORDER BY item_ordinal ASC LIMIT 1 FOR UPDATE"
    })
    Ali1688Dp10StageItemRow selectNextVerificationItemForUpdate(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_item SET verification_state = 'VERIFIED',",
            " apply_state = #{applyState}, state = #{state}, validation_code = #{validationCode},",
            " gmt_updated = UTC_TIMESTAMP(3) WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2",
            " AND partition_name = #{partitionName} AND page_no = #{pageNo}",
            " AND item_ordinal = #{itemOrdinal} AND verification_state = 'PENDING'"
    })
    int completeVerification(Ali1688Dp10StageItemRow row);

    @Insert({
            "INSERT IGNORE INTO dp_pull_dp10_stage_identity (",
            " task_id, generation_no, provider_order_no, first_partition, first_page_no,",
            " first_item_ordinal, active_fence_epoch, gmt_create",
            ") VALUES (#{taskId}, #{generationNo}, #{providerOrderNo}, #{partition},",
            " #{pageNo}, #{itemOrdinal}, #{fenceEpoch}, UTC_TIMESTAMP(3))"
    })
    int insertIdentityIfAbsent(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("providerOrderNo") String providerOrderNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo,
            @Param("itemOrdinal") int itemOrdinal,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_dp10_stage_identity",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND provider_order_no = #{providerOrderNo} AND first_partition = #{partition}",
            " AND first_page_no = #{pageNo} AND first_item_ordinal = #{itemOrdinal}"
    })
    int countIdentityOwner(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("providerOrderNo") String providerOrderNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo,
            @Param("itemOrdinal") int itemOrdinal
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_page SET state = 'VERIFIED',",
            " active_fence_epoch = #{fenceEpoch}, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = 2 AND partition_name = #{partition} AND page_no = #{pageNo}",
            " AND state = 'VERIFYING'"
    })
    int markPageVerified(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_dp10_stage_item WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2",
            " AND partition_name = #{partition} AND page_no = #{pageNo}",
            " AND verification_state <> 'VERIFIED'"
    })
    int countUnverifiedItemsOnPage(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_dp10_stage_page WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2"
    })
    int countPassTwoPages(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_dp10_stage_page WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2",
            " AND state NOT IN ('VERIFIED', 'APPLIED')"
    })
    int countUnverifiedPages(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo
    );

    @Select({
            "SELECT " + Ali1688Dp10StageMapper.ITEM_COLUMNS,
            "FROM dp_pull_dp10_stage_item WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2 AND apply_state = 'READY'",
            "ORDER BY CASE partition_name WHEN 'CURRENT' THEN 0 ELSE 1 END,",
            " page_no ASC, item_ordinal ASC LIMIT 1 FOR UPDATE"
    })
    Ali1688Dp10StageItemRow selectNextApplyItemForUpdate(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_item SET apply_item_cursor = #{nextCursor},",
            " gmt_updated = UTC_TIMESTAMP(3) WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2",
            " AND partition_name = #{partition} AND page_no = #{pageNo}",
            " AND item_ordinal = #{itemOrdinal} AND apply_state = 'READY'",
            " AND apply_item_cursor = #{expectedCursor}"
    })
    int advanceApplyCursor(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo,
            @Param("itemOrdinal") int itemOrdinal,
            @Param("expectedCursor") int expectedCursor,
            @Param("nextCursor") int nextCursor
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_item SET apply_item_cursor = #{nextCursor},",
            " apply_state = 'APPLIED', gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo} AND scan_pass = 2",
            " AND partition_name = #{partition} AND page_no = #{pageNo}",
            " AND item_ordinal = #{itemOrdinal} AND apply_state = 'READY'",
            " AND apply_item_cursor = #{expectedCursor}"
    })
    int markItemApplied(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo,
            @Param("itemOrdinal") int itemOrdinal,
            @Param("expectedCursor") int expectedCursor,
            @Param("nextCursor") int nextCursor
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_item item",
            "JOIN dp_pull_task task ON task.id = item.task_id",
            "SET item.state = 'SKIP_BUSINESS_ITEM',",
            " item.validation_code = #{validationCode}, item.apply_state = 'SKIPPED',",
            " item.gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task.id = #{task.id} AND task.operation_code = 'DP10'",
            " AND task.owner_user_id = #{task.ownerUserId}",
            " AND BINARY task.account_key = BINARY #{task.accountKey}",
            " AND BINARY task.scope_key = BINARY #{task.scopeKey}",
            " AND task.state = 'RUNNING'",
            " AND BINARY task.lease_owner = BINARY #{task.leaseOwner}",
            " AND task.lease_until > UTC_TIMESTAMP(6)",
            " AND task.fence_epoch = #{task.fenceEpoch}",
            " AND item.generation_no = #{slice.generationNo} AND item.scan_pass = 2",
            " AND BINARY item.partition_name = BINARY #{slice.partition}",
            " AND item.page_no = #{slice.pageNo} AND item.item_ordinal = #{slice.itemOrdinal}",
            " AND BINARY item.provider_order_no = BINARY #{slice.order.providerOrderNo}",
            " AND item.verification_state = 'VERIFIED' AND item.state = 'COMPLETE'",
            " AND item.validation_code IS NULL",
            " AND item.apply_state = 'READY'",
            " AND item.apply_item_cursor = #{slice.itemCursor}"
    })
    int markBusinessSkipped(
            @Param("task") DataPullTask task,
            @Param("slice") Ali1688Dp10ApplySlice slice,
            @Param("validationCode") String validationCode
    );

    @Select({
            "SELECT " + Ali1688Dp10StageMapper.PAGE_COLUMNS,
            "FROM dp_pull_dp10_stage_page WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2 AND state = 'VERIFIED'",
            "ORDER BY CASE partition_name WHEN 'CURRENT' THEN 0 ELSE 1 END, page_no ASC",
            "LIMIT 1 FOR UPDATE"
    })
    Ali1688Dp10StagePageRow selectNextVerifiedPageForUpdate(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_page SET state = 'APPLIED',",
            " active_fence_epoch = #{fenceEpoch}, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task_id = #{taskId} AND generation_no = #{generationNo}",
            " AND scan_pass = 2 AND partition_name = #{partition} AND page_no = #{pageNo}",
            " AND state = 'VERIFIED'"
    })
    int markPageApplied(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_dp10_stage_item WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2",
            " AND partition_name = #{partition} AND page_no = #{pageNo}",
            " AND apply_state NOT IN ('APPLIED', 'SKIPPED')"
    })
    int countUnappliedItemsOnPage(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("partition") String partition,
            @Param("pageNo") int pageNo
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_dp10_stage_page WHERE task_id = #{taskId}",
            " AND generation_no = #{generationNo} AND scan_pass = 2 AND state <> 'APPLIED'"
    })
    int countUnappliedPages(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo
    );
}
