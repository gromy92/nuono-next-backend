package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorSearchResultRetentionMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@ExtendWith(MockitoExtension.class)
class CompetitorSearchResultRetentionSchedulerTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-15T04:05:00Z"),
            BUSINESS_ZONE
    );
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 7, 31, 12, 5);

    @Mock
    private CompetitorSearchResultRetentionMapper mapper;

    @Test
    void springContextUsesTheConfiguredProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CompetitorSearchResultRetentionMapper.class, () -> mapper);
            context.register(CompetitorSearchResultRetentionScheduler.class);

            context.refresh();

            assertNotNull(context.getBean(CompetitorSearchResultRetentionScheduler.class));
        }
    }

    @Test
    void disabledCleanerDoesNotIssueAnyDelete() {
        CompetitorSearchResultRetentionScheduler scheduler = scheduler(false, 500, 5);

        assertEquals(0, scheduler.runOnce());

        verifyNoInteractions(mapper);
    }

    @Test
    void deletesBoundedBatchesUntilTheFirstShortBatch() {
        when(mapper.deleteExpiredTerminalSearchResults(CUTOFF, 500))
                .thenReturn(500, 500, 23);
        CompetitorSearchResultRetentionScheduler scheduler = scheduler(true, 500, 5);

        assertEquals(1_023, scheduler.runOnce());

        verify(mapper, org.mockito.Mockito.times(3)).deleteExpiredTerminalSearchResults(CUTOFF, 500);
        verify(mapper, never()).deleteExpiredTerminalSearchResults(CUTOFF, 501);
    }

    @Test
    void stopsAtTheConfiguredMaximumBatchCount() {
        when(mapper.deleteExpiredTerminalSearchResults(CUTOFF, 500)).thenReturn(500);
        CompetitorSearchResultRetentionScheduler scheduler = scheduler(true, 500, 2);

        assertEquals(1_000, scheduler.runOnce());

        verify(mapper, org.mockito.Mockito.times(2)).deleteExpiredTerminalSearchResults(CUTOFF, 500);
    }

    @Test
    void rejectsUnexpectedMapperCountsRatherThanContinuingWithAnUnboundedCleanup() {
        when(mapper.deleteExpiredTerminalSearchResults(CUTOFF, 500)).thenReturn(501);
        CompetitorSearchResultRetentionScheduler scheduler = scheduler(true, 500, 5);

        assertThrows(IllegalStateException.class, scheduler::runOnce);

        verify(mapper).deleteExpiredTerminalSearchResults(CUTOFF, 500);
    }

    @Test
    void validatesRetentionConfigurationAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new CompetitorSearchResultRetentionScheduler(
                mapper, true, 0, 500, 5, CLOCK
        ));
        assertThrows(IllegalArgumentException.class, () -> new CompetitorSearchResultRetentionScheduler(
                mapper, true, 15, 1_001, 5, CLOCK
        ));
        assertThrows(IllegalArgumentException.class, () -> new CompetitorSearchResultRetentionScheduler(
                mapper, true, 15, 500, 101, CLOCK
        ));
    }

    private CompetitorSearchResultRetentionScheduler scheduler(
            boolean enabled,
            int batchSize,
            int maximumBatchesPerRun
    ) {
        return new CompetitorSearchResultRetentionScheduler(
                mapper,
                enabled,
                15,
                batchSize,
                maximumBatchesPerRun,
                CLOCK
        );
    }
}
