package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class CompetitorRefreshStaleWorkerFencingTest {
    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private CompetitorProductSnapshotService snapshotService;
    @Mock private OperationalTaskService operationalTaskService;
    @Mock private NoonFrontendSearchAdapter searchAdapter;

    private CompetitorRefreshLeaseGuard leaseGuard;

    @BeforeEach
    void setUp() {
        leaseGuard = new CompetitorRefreshLeaseGuard(
                mapper,
                Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC),
                true
        );
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void recoveredRunRejectsReturnedSearchBeforeAnyAuthoritativeWrite() {
        when(mapper.nextKeywordRunId()).thenReturn(230001L);
        when(searchAdapter.search(any())).thenReturn(new NoonSearchPage());
        CompetitorKeywordRefreshTransactionRunner transactionRunner =
                new CompetitorKeywordRefreshTransactionRunner(
                        mapper,
                        new CompetitorSearchRefreshRunner(mapper, searchAdapter),
                        leaseGuard
                );

        assertThrows(
                CompetitorRefreshLeaseLostException.class,
                () -> transactionRunner.runKeyword(
                        150001L,
                        220001L,
                        watchProduct(),
                        keyword(),
                        501L
                )
        );

        verify(mapper, never()).insertSearchResult(any());
        verify(mapper, never()).insertCompetitorProduct(any());
        verify(mapper, never()).updateCompetitorProductFromSearch(any());
        verify(mapper, never()).upsertKeywordProductRelationFromSearch(any());
        verify(mapper, never()).insertRankFact(any());
        verify(mapper, never()).insertKeywordRun(any());
        verify(mapper, never()).markKeywordProviderSucceeded(
                any(), any(), any()
        );
    }

    @Test
    void recoveredRunRejectsProviderFailureEvidenceAndKeywordStatusWrite() {
        when(mapper.nextKeywordRunId()).thenReturn(230002L);
        CompetitorKeywordRefreshTransactionRunner transactionRunner =
                new CompetitorKeywordRefreshTransactionRunner(
                        mapper,
                        ignored -> {
                            throw new NoonSearchProviderException(
                                    "RATE_LIMITED",
                                    "HTTP 429",
                                    429,
                                    null,
                                    null
                            );
                        },
                        leaseGuard
                );

        assertThrows(
                CompetitorRefreshLeaseLostException.class,
                () -> transactionRunner.runKeyword(
                        150001L,
                        220001L,
                        watchProduct(),
                        keyword(),
                        501L
                )
        );

        verify(mapper, never()).insertKeywordRun(any());
        verify(mapper, never()).markKeywordProviderFailed(
                any(), any(), any(), any()
        );
    }

    @Test
    void nullLeaseIdentityFailsBeforeAnyDatabaseAccess() {
        assertThrows(
                CompetitorRefreshLeaseLostException.class,
                () -> leaseGuard.acquire(null, null, null)
        );
        verifyNoInteractions(mapper);
    }

    @Test
    void recoveredRunRejectsDetailUpdateAndSnapshot() {
        CompetitorProductDetailWriteGuard writeGuard =
                new CompetitorProductDetailWriteGuard(
                        mapper, snapshotService, leaseGuard
                );

        assertThrows(
                CompetitorRefreshLeaseLostException.class,
                () -> writeGuard.write(
                        150001L,
                        220001L,
                        watchProduct(),
                        competitorProduct(),
                        new CompetitorProductInsertCommand(),
                        new com.nuono.next.competitoranalysis.noon.NoonProductDetail(),
                        501L
                )
        );

        verify(mapper, never()).lockWatchProductForDetailWrite(any());
        verify(mapper, never()).lockConfirmedCompetitorProductForDetailWrite(
                any(), any()
        );
        verify(mapper, never()).updateCompetitorProductFromDetail(any());
        verify(snapshotService, never()).recordProductDetailSnapshot(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void recoveredRunRejectsTerminalLatestAndTaskWrites() {
        CompetitorRefreshExecutionFinalizer finalizer =
                new CompetitorRefreshExecutionFinalizer(
                        mapper, operationalTaskService, leaseGuard
                );

        assertThrows(
                CompetitorRefreshLeaseLostException.class,
                () -> finalizer.fail(
                        150001L,
                        220001L,
                        180001L,
                        "REFRESH_FAILED",
                        "failed",
                        501L
                )
        );

        verify(mapper, never()).failRunningRefreshRun(
                any(), any(), any(), any(), any(), any()
        );
        verify(mapper, never()).updateLatestRefreshRunIfNotOlder(
                any(), any(), any(), any()
        );
        verify(operationalTaskService, never()).fail(
                any(), any(), any()
        );
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180001L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode("ZSELF001");
        return row;
    }

    private static CompetitorProductRow competitorProduct() {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(200001L);
        row.setWatchProductId(180001L);
        row.setNoonProductCode("ZCOMP001");
        row.setReviewStatus("CONFIRMED");
        return row;
    }

    private static CompetitorKeywordRow keyword() {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(190001L);
        row.setWatchProductId(180001L);
        row.setKeyword("laundry basket");
        row.setLocale("en-SA");
        return row;
    }
}
