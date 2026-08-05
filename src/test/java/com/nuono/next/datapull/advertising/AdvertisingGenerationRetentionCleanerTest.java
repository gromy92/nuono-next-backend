package com.nuono.next.datapull.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.Dp06AdvertisingGenerationFactMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingGenerationMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingHeadMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingIdMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingRetentionMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingStageMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.apache.ibatis.annotations.Delete;
import org.junit.jupiter.api.Test;

class AdvertisingGenerationRetentionCleanerTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void deletesOnlyOneBoundedSlicePerTableAndThrottlesRuns() {
        Dp06AdvertisingRetentionMapper mapper = mock(Dp06AdvertisingRetentionMapper.class);
        AdvertisingGenerationRetentionCleaner cleaner =
                new AdvertisingGenerationRetentionCleaner(mapper);
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                NOW.minus(AdvertisingGenerationRetentionCleaner.TERMINAL_GRACE),
                ZoneOffset.UTC
        );

        cleaner.run(NOW);
        cleaner.run(NOW.plusSeconds(30));

        verify(mapper).deleteQueriesBatch(cutoff, 100);
        verify(mapper).deleteAbandonedQueriesBatch(cutoff, 100);
        verify(mapper).deleteCampaignsBatch(cutoff, 100);
        verify(mapper).deleteAbandonedCampaignsBatch(cutoff, 100);
        verify(mapper).deleteGenerationsBatch(cutoff, 1);
        verify(mapper).deleteAbandonedGenerationsBatch(cutoff, 1);
        verify(mapper, never()).deleteQueriesBatch(any(), eq(200));
    }

    @Test
    void rejectsMapperCountsOutsideTheConfiguredBound() {
        Dp06AdvertisingRetentionMapper mapper = mock(Dp06AdvertisingRetentionMapper.class);
        when(mapper.deleteQueriesBatch(any(), eq(100))).thenReturn(101);
        AdvertisingGenerationRetentionCleaner cleaner =
                new AdvertisingGenerationRetentionCleaner(mapper);

        assertThrows(IllegalStateException.class, () -> cleaner.run(NOW));
    }

    @Test
    void supersededRetentionAllowsOnlySuccessfulOrSupersededNonHeadGenerations() {
        Arrays.stream(Dp06AdvertisingRetentionMapper.class.getMethods())
                .filter(method -> !method.getName().contains("Abandoned"))
                .map(method -> method.getAnnotation(Delete.class))
                .filter(java.util.Objects::nonNull)
                .map(annotation -> String.join(" ", annotation.value()))
                .forEach(sql -> assertThat(sql)
                        .contains("t.state IN ('SUCCEEDED','SUPERSEDED')")
                        .contains("NOT EXISTS (SELECT 1 FROM dp_pull_advertising_current_head own")
                        .doesNotContain("'FAILED'"));
    }

    @Test
    void abandonedRetentionRequiresAQuiescentTerminalTaskAndNoVisibleHead() {
        Arrays.stream(Dp06AdvertisingRetentionMapper.class.getMethods())
                .filter(method -> method.getName().contains("Abandoned"))
                .map(method -> method.getAnnotation(Delete.class))
                .filter(java.util.Objects::nonNull)
                .map(annotation -> String.join(" ", annotation.value()))
                .forEach(sql -> assertThat(sql)
                        .contains("g.state='PREPARING'")
                        .contains("t.state IN ('FAILED','SUPERSEDED')")
                        .contains("t.lease_owner IS NULL")
                        .contains("t.lease_until IS NULL")
                        .contains("NOT EXISTS (SELECT 1 FROM dp_pull_advertising_current_head own")
                        .contains("LIMIT #{limit}"));
    }

    @Test
    void resetDeletesChildrenInForeignKeyOrderAndOnlyInBoundedSlices() {
        Dp06AdvertisingStageMapper stage = mock(Dp06AdvertisingStageMapper.class);
        Dp06AdvertisingGenerationMapper generations =
                mock(Dp06AdvertisingGenerationMapper.class);
        Dp06AdvertisingGenerationFactMapper facts =
                mock(Dp06AdvertisingGenerationFactMapper.class);
        Dp06AdvertisingHeadMapper heads = mock(Dp06AdvertisingHeadMapper.class);
        NoonAdvertisingFactWriter writer = new NoonAdvertisingFactWriter(
                stage, generations, facts, heads, mock(Dp06AdvertisingIdMapper.class)
        );
        AdvertisingTaskFenceRow fence = new AdvertisingTaskFenceRow();
        fence.setTaskId(6001L); fence.setOperationCode("DP06"); fence.setFenceEpoch(7L);
        fence.setState("RUNNING"); fence.setLeaseOwner("worker-1"); fence.setLeaseValid(true);
        AdvertisingGenerationRow generation = new AdvertisingGenerationRow();
        generation.setState("PREPARING");
        when(stage.selectTaskForUpdate(6001L)).thenReturn(fence);
        when(stage.countLiveFence(6001L, 7L, "worker-1")).thenReturn(1);
        when(generations.selectForUpdate(6001L)).thenReturn(generation);
        when(facts.deleteCampaignsBatch(6001L, 200)).thenReturn(1, 0);
        when(generations.deletePreparingIfEmpty(6001L)).thenReturn(1);

        assertEquals(AdvertisingFactWriter.ResetResult.MORE_WORK,
                writer.reset(6001L, 7L, "worker-1"));
        assertEquals(AdvertisingFactWriter.ResetResult.CLEARED,
                writer.reset(6001L, 7L, "worker-1"));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(facts);
        order.verify(facts).deleteQueriesBatch(6001L, 200);
        order.verify(facts).deleteCampaignsBatch(6001L, 200);
        order.verify(facts).deleteQueriesBatch(6001L, 200);
        order.verify(facts).deleteCampaignsBatch(6001L, 200);
        verify(generations).deletePreparingIfEmpty(6001L);
    }
}
