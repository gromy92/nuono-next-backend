package com.nuono.next.datapull.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.DataPullScheduleApplyMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleEpochRetentionMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskPlanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeBindingMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskCompactionMapper;
import com.nuono.next.infrastructure.mapper.Dp08BoundedScheduleScopeMapper;
import com.nuono.next.infrastructure.mapper.Dp08ScheduleEvidenceMapper;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ScheduleBoundedSqlContractTest {

    @Test
    void sourceEpochKeepsIndependentPassStateAndBindsTheVerifiedCutover() throws Exception {
        String insert = sql(DataPullScheduleScanMapper.class, "insertEpoch", Insert.class);
        String passOne = sql(DataPullScheduleScanMapper.class, "advancePassOne", Update.class);
        String passTwo = sql(DataPullScheduleScanMapper.class, "advancePassTwo", Update.class);

        assertThat(insert).contains("cutover_key", "#{cutoverKey}", "'PASS_ONE'");
        assertThat(passOne)
                .contains("pass_one_cursor", "pass_one_scope_count", "pass_one_ordered_sha256")
                .doesNotContain("pass_two_cursor =");
        assertThat(passTwo)
                .contains("pass_two_cursor", "pass_two_scope_count", "pass_two_ordered_sha256")
                .doesNotContain("pass_one_cursor =");
    }

    @Test
    void cleanupSchedulingAndCompactionReadsAreExplicitlyRowBounded() throws Exception {
        String cleanup = sql(
                DataPullScheduleEpochRetentionMapper.class,
                "deleteTerminalScopeRows",
                Delete.class
        );
        String completed = sql(
                DataPullScheduleTaskPlanMapper.class,
                "deleteCompletedScheduleStages",
                Delete.class
        );
        String pending = sql(
                DataPullScheduleTaskPlanMapper.class,
                "findPendingScheduleAtOrBefore",
                Select.class
        );
        String latest = sql(
                DataPullScheduleTaskPlanMapper.class, "listLatestSlots", Select.class
        );
        String compaction = sql(
                DataPullTaskCompactionMapper.class,
                "lockStrictlyNeverStartedBatch",
                Select.class
        );

        assertThat(cleanup).contains("LIMIT #{limit}");
        assertThat(completed).contains("LIMIT 64");
        assertThat(pending).contains("LIMIT 1").doesNotContain("COUNT(*)");
        assertThat(latest).contains("ORDER BY task.schedule_slot DESC").contains("LIMIT 1");
        assertThat(compaction).contains("LIMIT 65 FOR UPDATE");
    }

    @Test
    void epochIdentityAndTerminalDeletionArePersistentAndChildFirst() throws Exception {
        String sequence = sql(
                DataPullScheduleScanMapper.class, "lockEpochSequence", Select.class
        );
        String allocate = sql(
                DataPullScheduleScanMapper.class, "advanceEpochSequence", Update.class
        );
        String complete = sql(
                DataPullScheduleTaskPlanMapper.class, "advanceSchedulePhase", Update.class
        );
        String deleteHeader = sql(
                DataPullScheduleEpochRetentionMapper.class,
                "deleteTerminalEpochIfEmpty",
                Delete.class
        );

        assertThat(sequence)
                .contains("dp_pull_schedule_epoch_sequence", "FOR UPDATE")
                .doesNotContain("MAX(epoch_no)");
        assertThat(allocate)
                .contains("last_epoch_no = #{nextEpochNo}", "version_no = #{expectedVersion}");
        assertThat(complete)
                .contains("terminal_at_utc", "NOT EXISTS", "dp_pull_schedule_source_scope");
        assertThat(deleteHeader)
                .contains("active_operation_slot IS NULL", "NOT EXISTS", "LIMIT 1")
                .doesNotContain("CASCADE");
    }

    @Test
    void dp08MissingCloseStartsFromOnlyTheFullySealedSourceSet() throws Exception {
        String missing = sql(
                DataPullScopeBindingMapper.class,
                "lockMissingOpenBindingsAfter",
                Select.class
        );
        String transition = sql(
                DataPullScheduleApplyMapper.class,
                "advanceBindingMissingPhase",
                Update.class
        );
        String latest = sql(
                DataPullScopeBindingMapper.class,
                "lockLatestBindingsByScopeKeys",
                Select.class
        );

        assertThat(missing)
                .contains("NOT EXISTS")
                .contains("staged.epoch_no = #{epochNo}")
                .contains("LIMIT #{limit} FOR UPDATE");
        assertThat(transition)
                .contains("binding_close_state = 'RUNNING'")
                .contains("#{nextState} = 'SCHEDULING'")
                .contains("THEN 'COMPLETE'");
        assertThat(latest)
                .contains("ORDER BY candidate.effective_from_utc DESC")
                .contains("LIMIT 1")
                .contains("FOR UPDATE");
    }

    @Test
    void sourceAdaptersUseNativeKeysetsAndDp08EvidenceIsOneSetQuery() throws Exception {
        String noon = sql(
                NoonDataPullScopeMapper.class, "listActiveBoundScopesAfter", Select.class
        );
        String ali = sql(
                Ali1688Dp10RuntimeMapper.class,
                "listEffectiveOpenApiAuthorizationsAfter",
                Select.class
        );
        String dp08a = sql(
                Dp08BoundedScheduleScopeMapper.class,
                "listKeywordMembersAfter",
                Select.class
        );
        String dp08b = sql(
                Dp08BoundedScheduleScopeMapper.class,
                "listTargetMembersAfter",
                Select.class
        );
        String evidence = sql(
                Dp08ScheduleEvidenceMapper.class, "listEvidence", Select.class
        );

        for (String source : new String[]{noon, ali, dp08a, dp08b}) {
            assertThat(source).contains("LIMIT #{limit}").doesNotContain("OFFSET");
        }
        assertThat(evidence)
                .contains("<foreach collection='requests'")
                .contains("UNION ALL")
                .contains("operations_competitor_rank_fact")
                .contains("operations_competitor_product_snapshot");
        String members = sql(
                Dp08MemberSetMapper.class, "listMemberItemsAfter", Select.class
        );
        String staged = sql(
                Dp08MemberSetMapper.class, "listStageItemsAfter", Select.class
        );
        assertThat(members).contains("LIMIT #{limit}").doesNotContain("OFFSET");
        assertThat(staged).contains("LIMIT #{limit}").doesNotContain("OFFSET");
    }

    private static String sql(
            Class<?> mapper,
            String methodName,
            Class<? extends Annotation> annotationType
    ) throws Exception {
        Method method = Arrays.stream(mapper.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Annotation annotation = method.getAnnotation(annotationType);
        Method value = annotationType.getMethod("value");
        return String.join(" ", (String[]) value.invoke(annotation));
    }
}
