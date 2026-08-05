package com.nuono.next.datapull.advertising;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingGenerationFactMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingGenerationMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingHeadMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingIdMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingStageMapper;
import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
class NoonAdvertisingFactWriterTest {
    private Dp06AdvertisingStageMapper stage;
    private Dp06AdvertisingGenerationMapper generations;
    private Dp06AdvertisingGenerationFactMapper facts;
    private Dp06AdvertisingHeadMapper heads;
    private Dp06AdvertisingIdMapper ids;
    private NoonAdvertisingFactWriter writer;

    @BeforeEach
    void setUp() {
        stage = mock(Dp06AdvertisingStageMapper.class);
        generations = mock(Dp06AdvertisingGenerationMapper.class);
        facts = mock(Dp06AdvertisingGenerationFactMapper.class);
        heads = mock(Dp06AdvertisingHeadMapper.class);
        ids = mock(Dp06AdvertisingIdMapper.class);
        writer = new NoonAdvertisingFactWriter(stage, generations, facts, heads, ids);
        when(stage.selectTaskForUpdate(6001L)).thenReturn(taskFence(true));
        when(stage.countLiveFence(6001L, 7L, "worker-1")).thenReturn(1);
    }
    @Test
    void initializesInvisibleGenerationWhenActiveCampaignFactIsBusinessSkipped() {
        AdvertisingApplyCommand command = command();
        AdvertisingStageManifestRow manifest = manifest(command);
        manifest.setDashboardItemCount(0L); manifest.setDashboardBusinessSkippedItemCount(1L);
        manifest.setStagedItemCount(2L); manifest.setSourceItemCount(3L);
        manifest.setBusinessSkippedItemCount(1L);
        when(stage.selectManifest(6001L)).thenReturn(manifest);
        when(generations.selectForUpdate(6001L)).thenReturn(null);
        when(generations.insertIfAbsent(any())).thenReturn(1);
        doAnswer(invocation -> {
            AdvertisingIdBlockCommand block = invocation.getArgument(0);
            block.setAllocatedEnd(block.getInitialValue() + block.getBlockSize());
            return null;
        }).when(ids).reserve(any());
        assertEquals(AdvertisingFactWriter.ApplyResult.MORE_WORK,
        writer.applyComplete(command));
        verify(generations).insertIfAbsent(any());
        verify(facts, never()).insertCampaigns(anyList());
        verify(heads, never()).upsert(any(), any());
    }
    @Test
    void rejectsDashboardPageCountThatDiffersFromProviderDeclaration() {
        AdvertisingApplyCommand command = command();
        AdvertisingStageManifestRow manifest = manifest(command);
        manifest.setDashboardItemCount(0L);
        when(stage.selectManifest(6001L)).thenReturn(manifest);
        when(generations.selectForUpdate(6001L)).thenReturn(null);
        assertThrows(NoonAdvertisingFactWriter.AdvertisingApplyContractException.class,
                () -> writer.applyComplete(command));
        verify(ids, never()).reserve(any());
        verify(generations, never()).insertIfAbsent(any());
    }
    @Test
    void normalizedIdentityConflictsKeepFirstRowAndAccountLaterRows() {
        AdvertisingApplyCommand command = command(2L, List.of(
                new AdvertisingCampaignRef("C-LIVE-1", "Campaign")
        ));
        AdvertisingGenerationRow generation = generation(command, false);
        generation.setDeclaredCampaignCount(2L);
        generation.setStagedCampaignItemCount(2L);
        generation.setStagedItemCount(5L);
        generation.setSourceItemCount(5L);
        NoonAdvertisingCampaignFact laterCampaign = campaign();
        laterCampaign.setCampaignName("later campaign");
        NoonAdvertisingQueryFact firstQuery = query();
        firstQuery.setPartnerSku(" ZSKU-1 ");
        firstQuery.setAdSkuCode("");
        NoonAdvertisingQueryFact laterQuery = query();
        laterQuery.setPartnerSku("");
        laterQuery.setAdSkuCode("ZSKU-1");
        laterQuery.setSpendAmount(new BigDecimal("99.00"));
        AdvertisingFactChunk chunk = new AdvertisingFactChunkPreparer().prepare(
                command,
                generation,
                List.of(
                        raw(1, 0, 2, AdvertisingStagedFact.campaign(campaign())),
                        raw(1, 1, 2, AdvertisingStagedFact.campaign(laterCampaign)),
                        raw(2, 0, 3, AdvertisingStagedFact.queryPageProof(
                                new AdvertisingCampaignRef("C-LIVE-1", "Campaign"))),
                        raw(2, 1, 3, AdvertisingStagedFact.query(firstQuery)),
                        raw(2, 2, 3, AdvertisingStagedFact.query(laterQuery))
                ),
                java.util.Set.of()
        );
        assertEquals(5, chunk.getProcessedCount());
        assertEquals(1, chunk.getQueryPageProofCount());
        assertEquals(2, chunk.getSkippedIdentityCount());
        assertEquals(1, chunk.getCampaignSkippedIdentityCount());
        assertEquals(1, chunk.getCampaigns().size());
        assertEquals(1, chunk.getQueries().size());
        assertEquals("Campaign", chunk.getCampaigns().get(0).getCampaign().getCampaignName());
        assertEquals(new BigDecimal("2.00"),
                chunk.getQueries().get(0).getQuery().getSpendAmount());
        assertTrue(chunk.getQueries().get(0).getQuery().getPartnerSku().isEmpty());
        generation.setProcessedItemCount(5L); generation.setCampaignFactCount(1L);
        generation.setQueryFactCount(1L); generation.setIdentitySkippedItemCount(2L);
        generation.setCampaignIdentitySkippedItemCount(1L);
        generation.setQueryPageProofCount(1);
        generation.setMatchedActiveCampaignCount(1);
        new AdvertisingGenerationGuard().requireCompleteAccounting(generation);
    }
    @Test
    void preparesAtMostOneSourceFactChunkWithoutMovingHead() {
        AdvertisingApplyCommand command = command();
        AdvertisingGenerationRow generation = generation(command, false);
        List<AdvertisingRawStageRow> rows = List.of(
                raw(1, 0, 1, AdvertisingStagedFact.campaign(campaign())),
                raw(2, 0, 2, AdvertisingStagedFact.queryPageProof(
                        new AdvertisingCampaignRef("C-LIVE-1", "Campaign"))),
                raw(2, 1, 2, AdvertisingStagedFact.query(query()))
        );
        when(generations.selectForUpdate(6001L)).thenReturn(generation);
        when(stage.selectRawChunk(6001L, 0, -1, 200)).thenReturn(rows);
        when(facts.selectExistingIdentities(eq(6001L), anyList())).thenReturn(List.of());
        when(facts.insertCampaigns(anyList())).thenReturn(1);
        when(facts.insertQueries(anyList())).thenReturn(1);
        when(generations.advance(eq(command), eq(generation), any())).thenReturn(1);
        assertEquals(AdvertisingFactWriter.ApplyResult.MORE_WORK,
                writer.applyComplete(command));
        verify(facts).insertQueries(anyList());
        verify(heads, never()).upsert(any(), any());
    }
    @Test
    void sealsByOneHeadMoveAfterAccountingAndBoundedRawCleanup() {
        AdvertisingApplyCommand command = command();
        AdvertisingGenerationRow generation = generation(command, true);
        AdvertisingGenerationHeadRow winner = head(command, generation);
        when(generations.selectForUpdate(6001L)).thenReturn(generation);
        when(stage.selectRawChunk(6001L, 2, 1, 200)).thenReturn(List.of());
        when(heads.selectForUpdate(command)).thenReturn(null, winner);
        when(heads.upsert(command, generation)).thenReturn(1);
        when(generations.seal(command)).thenReturn(1);
        assertEquals(AdvertisingFactWriter.ApplyResult.APPLIED,
                writer.applyComplete(command));
        verify(heads).upsert(command, generation);
        verify(generations).seal(command);
        verify(facts, never()).insertCampaigns(anyList());
        verify(facts, never()).insertQueries(anyList());
    }
    @Test
    void staleOrExpiredFenceCannotCreateGenerationAndLateExpiryRollsBack() {
        AdvertisingApplyCommand command = command();
        when(stage.selectTaskForUpdate(6001L)).thenReturn(taskFence(false));
        assertEquals(AdvertisingFactWriter.ApplyResult.STALE_FENCE,
                writer.applyComplete(command));
        verify(generations, never()).insertIfAbsent(any());
        when(stage.selectTaskForUpdate(6001L)).thenReturn(taskFence(true));
        when(generations.selectForUpdate(6001L)).thenReturn(generation(command, false));
        when(stage.selectRawChunk(6001L, 0, -1, 200)).thenReturn(List.of(
                raw(1, 0, 1, AdvertisingStagedFact.campaign(campaign()))
        ));
        when(facts.selectExistingIdentities(eq(6001L), anyList())).thenReturn(List.of());
        when(facts.insertCampaigns(anyList())).thenReturn(1);
        when(generations.advance(any(), any(), any())).thenReturn(1);
        when(stage.countLiveFence(6001L, 7L, "worker-1")).thenReturn(0);
        assertThrows(NoonAdvertisingFactWriter.AdvertisingApplyLeaseExpiredException.class,
                () -> writer.applyComplete(command));
    }
    private AdvertisingApplyCommand command() {
        return command(1L, List.of(new AdvertisingCampaignRef("C-LIVE-1", "Campaign")));
    }
    private AdvertisingApplyCommand command(
            long declaredCampaignCount,
            List<AdvertisingCampaignRef> activeCampaigns
    ) {
        AdvertisingCampaignEnumerationAuthority authority =
                AdvertisingCampaignEnumerationAuthority.fromProviderFields(
                        "generation-1", LocalDateTime.of(2026, 8, 2, 0, 0),
                        declaredCampaignCount, true
                );
        DataPullTask task = task();
        return new AdvertisingApplyCommand(
                6001L, 7L, "worker-1", task.getScheduleSlot(),
                AdvertisingPullRequest.from(task), task.getBusinessWindowKey(), authority,
                activeCampaigns
        );
    }
    private AdvertisingStageManifestRow manifest(AdvertisingApplyCommand command) {
        AdvertisingStageManifestRow row = new AdvertisingStageManifestRow();
        row.setTaskId(6001L); row.setActiveFenceEpoch(7L);
        row.setDeclaredTotalPages(2); row.setKnownLastPage(2); row.setPageCount(2L);
        row.setFirstPage(1); row.setLastPage(2); row.setAuthorityKind("COMPLETE_EXPORT");
        row.setDashboardItemCount(1L); row.setDashboardSourceItemCount(1L);
        row.setDashboardBusinessSkippedItemCount(0L);
        row.setAuthorityTokenSha256(command.getAuthority().getGenerationTokenSha256());
        row.setSnapshotAsOfUtc(command.getAuthority().getProviderAsOfUtc());
        row.setDeclaredCampaignCount(1L); row.setStagedItemCount(3L);
        row.setSourceItemCount(3L); row.setBusinessSkippedItemCount(0L);
        return row;
    }
    private AdvertisingGenerationRow generation(
            AdvertisingApplyCommand command, boolean complete
    ) {
        AdvertisingGenerationRow row = new AdvertisingGenerationRow();
        row.setTaskId(6001L); row.setActiveFenceEpoch(7L); row.setState("PREPARING");
        row.setOwnerUserId(307L); row.setProjectCode("PRJ108065");
        row.setStoreCode("STR108065-NSA"); row.setSiteCode("SA");
        row.setReportDate(command.getReportDate()); row.setScheduleSlot(command.getScheduleSlot());
        row.setBusinessWindowKey(command.getBusinessWindowKey());
        row.setAuthorityTokenSha256(command.getAuthority().getGenerationTokenSha256());
        row.setActiveCampaignDigestSha256(AdvertisingDigestChain.activeCampaignDigest(command));
        row.setProviderAsOfUtc(command.getAuthority().getProviderAsOfUtc());
        row.setDeclaredCampaignCount(1L); row.setActiveCampaignCount(1); row.setLastPage(2);
        row.setStagedCampaignItemCount(1L); row.setCampaignBusinessSkippedItemCount(0L);
        row.setStagedItemCount(3L); row.setSourceItemCount(3L);
        row.setBusinessSkippedItemCount(0L); row.setBatchId(200001L);
        row.setCampaignIdStart(210001L); row.setQueryIdStart(220001L);
        row.setCursorPageNo(complete ? 2 : 0); row.setCursorItemOrdinal(complete ? 1 : -1);
        row.setProcessedItemCount(complete ? 3L : 0L);
        row.setCampaignFactCount(complete ? 1L : 0L);
        row.setQueryFactCount(complete ? 1L : 0L); row.setIdentitySkippedItemCount(0L);
        row.setCampaignIdentitySkippedItemCount(0L);
        row.setQueryPageProofCount(complete ? 1 : 0);
        row.setMatchedActiveCampaignCount(complete ? 1 : 0);
        row.setDigestChainSha256(AdvertisingDigestChain.seed(command, manifest(command)));
        return row;
    }
    private AdvertisingGenerationHeadRow head(
            AdvertisingApplyCommand command, AdvertisingGenerationRow generation
    ) {
        AdvertisingGenerationHeadRow row = new AdvertisingGenerationHeadRow();
        row.setOwnerUserId(command.getOwnerUserId()); row.setProjectCode(command.getProjectCode());
        row.setStoreCode(command.getStoreCode()); row.setSiteCode(command.getSiteCode());
        row.setReportDate(command.getReportDate()); row.setTaskId(command.getTaskId());
        row.setBatchId(generation.getBatchId()); row.setScheduleSlot(command.getScheduleSlot());
        return row;
    }
    private AdvertisingRawStageRow raw(
            int page, int ordinal, int count, AdvertisingStagedFact fact
    ) {
        AdvertisingStagedFactCodec codec = new AdvertisingStagedFactCodec();
        AdvertisingRawStageRow row = new AdvertisingRawStageRow();
        row.setTaskId(6001L); row.setPageNo(page); row.setItemOrdinal(ordinal);
        row.setPageItemCount(count); row.setStableIdentity(codec.stableIdentity(fact));
        row.setContentFingerprint(codec.stableContentFingerprint(fact));
        row.setPayload(codec.encode(fact));
        return row;
    }

    private AdvertisingTaskFenceRow taskFence(boolean live) {
        AdvertisingTaskFenceRow row = new AdvertisingTaskFenceRow();
        row.setTaskId(6001L); row.setOperationCode("DP06"); row.setOwnerUserId(307L);
        row.setProjectCode("PRJ108065"); row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA"); row.setBusinessWindowKey(task().getBusinessWindowKey());
        row.setScheduleSlot(task().getScheduleSlot()); row.setFenceEpoch(7L);
        row.setState("RUNNING"); row.setLeaseOwner("worker-1"); row.setLeaseValid(live);
        return row;
    }

    private DataPullTask task() {
        return DataPullTask.queued(6001L, OperationCode.DP06, "noon-admanager", 307L,
                108065L, "PRJ108065", null, "PRJ108065", "STR108065-NSA", "SA",
                "scope-sa", LocalDateTime.of(2026, 8, 1, 22, 30),
                "DP06:date-range:2026-08-01..2026-08-01", "ADS_ADVERTISER",
                LocalDateTime.of(2026, 8, 1, 22, 0));
    }

    private NoonAdvertisingCampaignFact campaign() {
        NoonAdvertisingCampaignFact fact = new NoonAdvertisingCampaignFact();
        fact.setCampaignCode("C-LIVE-1"); fact.setCampaignName("Campaign");
        fact.setCampaignStatus("live"); fact.setSpendAmount(new BigDecimal("10.00"));
        return fact;
    }

    private NoonAdvertisingQueryFact query() {
        NoonAdvertisingQueryFact fact = new NoonAdvertisingQueryFact();
        fact.setCampaignCode("C-LIVE-1"); fact.setCampaignName("Campaign");
        fact.setAdSkuCode("ZSKU-1"); fact.setQueryText("paper towel");
        fact.setQueryKind("search_term"); fact.setSpendAmount(new BigDecimal("2.00"));
        return fact;
    }
}
