package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class SnapshotFactApplyMapperSqlTest {

    @Test
    void lockedPrecheckAndMarkerInsertBothBindTheCompleteLeaseIdentity() throws Exception {
        String taskLock = sql("selectTaskForUpdate", Select.class);
        String markerInsert = sql("insertMarkerIfLive", Insert.class);

        assertThat(taskLock)
                .contains("id AS taskId")
                .contains("fence_epoch AS fenceEpoch")
                .contains("lease_owner AS leaseOwner")
                .contains("lease_until AS leaseUntil")
                .contains("WHERE id = #{taskId}")
                .contains("FOR UPDATE");
        assertThat(markerInsert)
                .contains("WHERE task.id = #{snapshot.taskId}")
                .contains("#{snapshot.authority.generationTokenSha256}")
                .contains("#{snapshot.authority.declaredCollectionCount}")
                .contains("#{snapshot.sourceItemCount}")
                .contains("#{snapshot.appliedItemCount}")
                .contains("#{snapshot.businessSkippedItemCount}")
                .contains("#{effectiveItemCount}")
                .contains("#{carryMode}")
                .contains("#{carriedFromTaskId}")
                .contains("task.fence_epoch = #{snapshot.fenceEpoch}")
                .contains("BINARY task.lease_owner = BINARY #{snapshot.leaseOwner}")
                .contains("task.lease_until > #{nowUtc}");
    }

    @Test
    void canonicalApplyChunkIsCursorBoundedAndDeterministicallyDeduplicated() throws Exception {
        String chunk = sql("selectCanonicalChunk", Select.class);
        String advance = sql("advanceProgress", Update.class);

        assertThat(chunk)
                .contains("i.task_id=#{taskId}")
                .contains("i.page_no>#{afterPageNo}")
                .contains("i.item_ordinal>#{afterItemOrdinal}")
                .contains("i.validated_identity_candidate AS validatedIdentityCandidate")
                .contains("i.absence_reconciliation_safe AS absenceReconciliationSafe")
                .contains("earlier.stable_identity=i.stable_identity")
                .contains("valid_item.stable_identity=i.stable_identity")
                .contains("ORDER BY i.page_no ASC, i.item_ordinal ASC")
                .contains("LIMIT #{limit}");
        assertThat(advance)
                .contains("progress.cursor_page_no=#{expectedPageNo}")
                .contains("progress.cursor_item_ordinal=#{expectedItemOrdinal}")
                .contains("task.state='RUNNING'")
                .contains("BINARY task.lease_owner=BINARY #{snapshot.leaseOwner}")
                .contains("task.lease_until>#{nowUtc}");
    }

    @Test
    void currentHeadUpsertIsMonotonicAndProgressSealIsExactCountBound() throws Exception {
        String head = sql("upsertCurrentHead", Insert.class);
        String seal = sql("markProgressSealed", Update.class);

        assertThat(head)
                .contains("incoming.schedule_slot>dp_pull_snapshot_current_head.schedule_slot")
                .contains("incoming.schedule_slot=dp_pull_snapshot_current_head.schedule_slot")
                .contains("incoming.task_id>dp_pull_snapshot_current_head.task_id")
                .contains("version_no+1");
        assertThat(seal)
                .contains("state='SEALED'")
                .contains("active_fence_epoch=#{fenceEpoch}")
                .contains("carry_mode=#{carryMode}")
                .contains("prepared_item_count=#{expectedItemCount}")
                .contains("effective_item_count=#{expectedEffectiveItemCount}");
    }

    private String sql(
            String methodName,
            Class<? extends Annotation> annotationType
    ) throws Exception {
        Method method = java.util.Arrays.stream(SnapshotFactApplyMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] fragments;
        if (annotation instanceof Select) {
            fragments = ((Select) annotation).value();
        } else if (annotation instanceof Insert) {
            fragments = ((Insert) annotation).value();
        } else {
            fragments = ((Update) annotation).value();
        }
        String raw = String.join("\n", fragments);
        new XMLLanguageDriver().createSqlSource(new Configuration(), raw, Object.class);
        return raw.replaceAll("\\s+", " ");
    }
}
