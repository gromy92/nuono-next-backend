package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10ApplyStageMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688Dp10BatchVerifierTest extends Ali1688Dp10JobTestSupport {

    @Test
    void readyPageVerificationLoadsOnlyThatPageAndMovesToVerifying() {
        Fixture fixture = fixture("GOOD");
        fixture.page.setState("READY");
        when(fixture.mapper.selectNextVerificationPageForUpdate(
                fixture.task.getId(), 1L)).thenReturn(fixture.page);
        when(fixture.mapper.selectPageItemsForUpdate(
                fixture.task.getId(), 1L, "CURRENT", 1)).thenReturn(List.of(fixture.item));
        when(fixture.mapper.markPageVerifying(
                fixture.task.getId(), 1L, "CURRENT", 1,
                fixture.task.getFenceEpoch())).thenReturn(1);

        assertThat(fixture.verifier.verifyNext(fixture.task, fixture.command))
                .isEqualTo(Ali1688Dp10BatchVerifier.Advance.PROGRESSED);

        verify(fixture.mapper).selectPageItemsForUpdate(
                fixture.task.getId(), 1L, "CURRENT", 1);
        verify(fixture.mapper, never()).selectNextVerificationItemForUpdate(
                fixture.task.getId(), 1L, "CURRENT", 1);
    }

    @Test
    void persistenceInvalidItemIsSkippedBeforeItCanReserveIdentity() {
        Fixture fixture = fixture("SAME");
        Ali1688HistoricalOrderProvider.OrderSnapshot invalid =
                fixture.assembler.decodeComplete(fixture.item);
        invalid.setSupplierName("x".repeat(301));
        replacePayload(fixture, invalid);
        fixture.page.setState("VERIFYING");
        when(fixture.mapper.selectNextVerificationPageForUpdate(
                fixture.task.getId(), 1L)).thenReturn(fixture.page);
        when(fixture.mapper.selectNextVerificationItemForUpdate(
                fixture.task.getId(), 1L, "CURRENT", 1)).thenReturn(fixture.item);
        when(fixture.mapper.completeVerification(any())).thenReturn(1);

        fixture.verifier.verifyNext(fixture.task, fixture.command);

        assertThat(fixture.item.getState()).isEqualTo("SKIP_BUSINESS_ITEM");
        assertThat(fixture.item.getApplyState()).isEqualTo("SKIPPED");
        assertThat(fixture.item.getValidationCode()).isEqualTo("DP10_FACT_ORDER_TEXT_TOO_LONG");
        verify(fixture.mapper, never()).insertIdentityIfAbsent(
                fixture.task.getId(), 1L, "SAME", "CURRENT", 1, 0,
                fixture.task.getFenceEpoch());
    }

    @Test
    void validItemReservesIdentityThenBecomesReadyForSegmentedApply() {
        Fixture fixture = fixture("SAME");
        fixture.page.setState("VERIFYING");
        when(fixture.mapper.selectNextVerificationPageForUpdate(
                fixture.task.getId(), 1L)).thenReturn(fixture.page);
        when(fixture.mapper.selectNextVerificationItemForUpdate(
                fixture.task.getId(), 1L, "CURRENT", 1)).thenReturn(fixture.item);
        when(fixture.mapper.insertIdentityIfAbsent(
                fixture.task.getId(), 1L, "SAME", "CURRENT", 1, 0,
                fixture.task.getFenceEpoch())).thenReturn(1);
        when(fixture.mapper.completeVerification(any())).thenReturn(1);

        fixture.verifier.verifyNext(fixture.task, fixture.command);

        assertThat(fixture.item.getApplyState()).isEqualTo("READY");
        verify(fixture.mapper).completeVerification(fixture.item);
    }

    @Test
    void missingPassTwoPageFailsClosedBeforeApply() {
        Fixture fixture = fixture("GOOD");
        when(fixture.mapper.selectNextVerificationPageForUpdate(
                fixture.task.getId(), 1L)).thenReturn(null);
        when(fixture.mapper.countPassTwoPages(fixture.task.getId(), 1L)).thenReturn(1);

        assertThatThrownBy(() -> fixture.verifier.verifyNext(fixture.task, fixture.command))
                .isInstanceOf(Ali1688Dp10PageContractException.class)
                .hasMessage("DP10_STAGE_PAGE_COUNT_INVALID");
    }

    @Test
    void tamperedStagePageAbove100FailsBeforeItemsOrFactsAreRead() {
        Fixture fixture = fixture("GOOD");
        fixture.page.setState("READY");
        fixture.page.setPageSize(1_000);
        when(fixture.mapper.selectNextVerificationPageForUpdate(
                fixture.task.getId(), 1L)).thenReturn(fixture.page);

        assertThatThrownBy(() -> fixture.verifier.verifyNext(fixture.task, fixture.command))
                .isInstanceOf(Ali1688Dp10PageContractException.class)
                .hasMessage("DP10_STAGE_PAGE_SIZE_INVALID");
        verify(fixture.mapper, never()).selectPageItemsForUpdate(
                fixture.task.getId(), 1L, "CURRENT", 1);
    }

    private Fixture fixture(String orderNo) {
        DataPullTask task = task(authorization());
        task.setStepCode(Ali1688Dp10Job.VERIFY_STEP);
        Ali1688Dp10ApplyStageMapper mapper = mock(Ali1688Dp10ApplyStageMapper.class);
        Ali1688Dp10StageMapper stageMapper = mock(Ali1688Dp10StageMapper.class);
        Ali1688Dp10StageAssembler assembler = new Ali1688Dp10StageAssembler(
                stageMapper, new ObjectMapper().findAndRegisterModules());
        Ali1688HistoricalOrderProvider.OrderSnapshot order = order(orderNo, NEWEST, true);
        Ali1688Dp10ValidatedPage validated = new Ali1688Dp10ValidatedPage(
                Ali1688HistoricalOrderProvider.Partition.CURRENT,
                1, 1, 1L, 1,
                List.of(new Ali1688Dp10ListEntry(
                        0, order, Ali1688Dp10ItemState.COMPLETE, null,
                        Ali1688Dp10RawOrderFingerprint.fingerprint(order))));
        List<Ali1688Dp10StageItemRow> items = assembler.itemRows(task, 1L, 2, validated);
        Ali1688Dp10StagePageRow page = assembler.pageRow(task, 1L, 2, validated, items);
        page.setState("READY");
        Ali1688Dp10ApplyCommand command = new Ali1688Dp10ApplyCommand(
                task, authorization(), 1L, 1L, 1, 0L, 1,
                0L, NOW.toInstant(java.time.ZoneOffset.UTC), NOW);
        return new Fixture(
                task, mapper, assembler,
                new Ali1688Dp10BatchVerifier(mapper, assembler), page, items.get(0), command);
    }

    private void replacePayload(
            Fixture fixture,
            Ali1688HistoricalOrderProvider.OrderSnapshot order
    ) {
        String payload = fixture.assembler.encode(order);
        fixture.item.setPayload(payload);
        fixture.item.setContentFingerprint(fixture.assembler.fingerprint(payload));
    }

    private static final class Fixture {
        private final DataPullTask task;
        private final Ali1688Dp10ApplyStageMapper mapper;
        private final Ali1688Dp10StageAssembler assembler;
        private final Ali1688Dp10BatchVerifier verifier;
        private final Ali1688Dp10StagePageRow page;
        private final Ali1688Dp10StageItemRow item;
        private final Ali1688Dp10ApplyCommand command;

        private Fixture(
                DataPullTask task,
                Ali1688Dp10ApplyStageMapper mapper,
                Ali1688Dp10StageAssembler assembler,
                Ali1688Dp10BatchVerifier verifier,
                Ali1688Dp10StagePageRow page,
                Ali1688Dp10StageItemRow item,
                Ali1688Dp10ApplyCommand command
        ) {
            this.task = task;
            this.mapper = mapper;
            this.assembler = assembler;
            this.verifier = verifier;
            this.page = page;
            this.item = item;
            this.command = command;
        }
    }
}
