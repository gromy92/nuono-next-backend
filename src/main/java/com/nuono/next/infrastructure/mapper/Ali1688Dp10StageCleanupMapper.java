package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10StageCleanupReason;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10StageCleanupMarker;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Exact-generation DP-10 cleanup statements; caller must first lock the task row. */
public interface Ali1688Dp10StageCleanupMapper {

    @Select({
            "SELECT generation_no AS generationNo,reason,active_fence_epoch AS fenceEpoch",
            "FROM dp_pull_dp10_stage_cleanup WHERE task_id=#{taskId} FOR UPDATE"
    })
    Ali1688Dp10StageCleanupMarker selectTaskMarkerForUpdate(
            @Param("taskId") long taskId);

    @Select({
            "SELECT MIN(generation_no) FROM (",
            " SELECT generation_no FROM dp_pull_dp10_stage_fingerprint_count",
            " WHERE task_id=#{taskId} AND generation_no<#{currentGenerationNo}",
            " UNION ALL SELECT generation_no FROM dp_pull_dp10_stage_identity",
            " WHERE task_id=#{taskId} AND generation_no<#{currentGenerationNo}",
            " UNION ALL SELECT generation_no FROM dp_pull_dp10_stage_item",
            " WHERE task_id=#{taskId} AND generation_no<#{currentGenerationNo}",
            " UNION ALL SELECT generation_no FROM dp_pull_dp10_stage_page",
            " WHERE task_id=#{taskId} AND generation_no<#{currentGenerationNo}",
            ") generations"
    })
    Long selectOldestGenerationBefore(
            @Param("taskId") long taskId,
            @Param("currentGenerationNo") long currentGenerationNo
    );

    @Select({
            "SELECT reason FROM dp_pull_dp10_stage_cleanup",
            "WHERE task_id=#{taskId} AND generation_no=#{generationNo} FOR UPDATE"
    })
    Ali1688Dp10StageCleanupReason selectMarkerReasonForUpdate(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo
    );

    @Insert({
            "INSERT INTO dp_pull_dp10_stage_cleanup(task_id,generation_no,reason,",
            "active_fence_epoch,gmt_create,gmt_updated) VALUES(#{taskId},#{generationNo},",
            "#{reason},#{fenceEpoch},#{nowUtc},#{nowUtc})"
    })
    int insertMarker(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("reason") Ali1688Dp10StageCleanupReason reason,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_cleanup SET active_fence_epoch=#{fenceEpoch},",
            "gmt_updated=#{nowUtc} WHERE task_id=#{taskId} AND generation_no=#{generationNo}",
            "AND reason=#{reason} AND active_fence_epoch<=#{fenceEpoch}"
    })
    int refreshMarker(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("reason") Ali1688Dp10StageCleanupReason reason,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Update({
            "UPDATE dp_pull_dp10_stage_cleanup SET reason='FAILED_RETENTION',",
            "active_fence_epoch=#{newFenceEpoch},gmt_updated=#{nowUtc}",
            "WHERE task_id=#{taskId} AND generation_no=#{generationNo}",
            "AND reason=#{oldReason} AND active_fence_epoch=#{oldFenceEpoch}",
            "AND active_fence_epoch<=#{newFenceEpoch}"
    })
    int adoptMarkerForFailedRetention(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("oldReason") Ali1688Dp10StageCleanupReason oldReason,
            @Param("oldFenceEpoch") long oldFenceEpoch,
            @Param("newFenceEpoch") long newFenceEpoch,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Delete({
            "DELETE FROM dp_pull_dp10_stage_cleanup WHERE task_id=#{taskId}",
            "AND generation_no=#{generationNo} AND reason=#{reason}",
            "AND active_fence_epoch=#{fenceEpoch}"
    })
    int deleteMarker(
            @Param("taskId") long taskId,
            @Param("generationNo") long generationNo,
            @Param("reason") Ali1688Dp10StageCleanupReason reason,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Select("SELECT EXISTS(SELECT 1 FROM dp_pull_dp10_stage_fingerprint_count"
            + " WHERE task_id=#{taskId} AND generation_no=#{generationNo} LIMIT 1)")
    int hasFingerprintCount(@Param("taskId") long taskId,
                            @Param("generationNo") long generationNo);

    @Delete("DELETE FROM dp_pull_dp10_stage_fingerprint_count WHERE task_id=#{taskId}"
            + " AND generation_no=#{generationNo} ORDER BY partition_name ASC,"
            + " list_content_fingerprint ASC LIMIT #{batchSize}")
    int deleteFingerprintCountBatch(@Param("taskId") long taskId,
                                    @Param("generationNo") long generationNo,
                                    @Param("batchSize") int batchSize);

    @Select("SELECT EXISTS(SELECT 1 FROM dp_pull_dp10_stage_identity"
            + " WHERE task_id=#{taskId} AND generation_no=#{generationNo} LIMIT 1)")
    int hasIdentity(@Param("taskId") long taskId,
                    @Param("generationNo") long generationNo);

    @Delete("DELETE FROM dp_pull_dp10_stage_identity WHERE task_id=#{taskId}"
            + " AND generation_no=#{generationNo} ORDER BY provider_order_no ASC"
            + " LIMIT #{batchSize}")
    int deleteIdentityBatch(@Param("taskId") long taskId,
                            @Param("generationNo") long generationNo,
                            @Param("batchSize") int batchSize);

    @Select("SELECT EXISTS(SELECT 1 FROM dp_pull_dp10_stage_item"
            + " WHERE task_id=#{taskId} AND generation_no=#{generationNo} LIMIT 1)")
    int hasItem(@Param("taskId") long taskId,
                @Param("generationNo") long generationNo);

    @Delete("DELETE FROM dp_pull_dp10_stage_item WHERE task_id=#{taskId}"
            + " AND generation_no=#{generationNo} ORDER BY scan_pass ASC,"
            + " partition_name ASC,page_no ASC,item_ordinal ASC LIMIT #{batchSize}")
    int deleteItemBatch(@Param("taskId") long taskId,
                        @Param("generationNo") long generationNo,
                        @Param("batchSize") int batchSize);

    @Select("SELECT EXISTS(SELECT 1 FROM dp_pull_dp10_stage_page"
            + " WHERE task_id=#{taskId} AND generation_no=#{generationNo} LIMIT 1)")
    int hasPage(@Param("taskId") long taskId,
                @Param("generationNo") long generationNo);

    @Delete("DELETE FROM dp_pull_dp10_stage_page WHERE task_id=#{taskId}"
            + " AND generation_no=#{generationNo} ORDER BY scan_pass ASC,"
            + " partition_name ASC,page_no ASC LIMIT #{batchSize}")
    int deletePageBatch(@Param("taskId") long taskId,
                        @Param("generationNo") long generationNo,
                        @Param("batchSize") int batchSize);
}
