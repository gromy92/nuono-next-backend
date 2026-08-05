package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.aop.support.AopUtils;

/** Real MySQL proof for DP10 100-row staging and 20-row-weight fact transactions. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Ali1688Dp10ExactPathMySqlTest {
    private Ali1688Dp10ExactPathMySqlContext context;
    private Ali1688Dp10ExactPathFixture fixture;
    private Ali1688Dp10ExactPathMySqlDatabase database;

    @BeforeAll
    void connect() throws Exception {
        String url = System.getenv("NUONO_DP10_EXACT_MYSQL_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        context = new Ali1688Dp10ExactPathMySqlContext(
                url,
                System.getenv("NUONO_DP10_EXACT_MYSQL_USERNAME"),
                System.getenv("NUONO_DP10_EXACT_MYSQL_PASSWORD"));
        assertThat(AopUtils.isAopProxy(context.stageStore())).isTrue();
        assertThat(AopUtils.isAopProxy(context.factTransaction())).isTrue();
        fixture = new Ali1688Dp10ExactPathFixture(context);
        database = fixture.database();
    }

    @BeforeEach
    void prepare() {
        database.prepare();
    }

    @AfterEach
    void cleanup() {
        if (database != null) database.cleanup();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @AfterAll
    void close() {
        if (context != null) context.close();
    }

    @Test
    void stagesOneHundredRowsIdempotentlyAndRejectsPageDriftBeforeTenSeconds() {
        DataPullTask task = fixture.task("DP10_LIST_CURRENT_PASS1", "stage-100");
        List<Ali1688HistoricalOrderProvider.OrderSnapshot> orders =
                fixture.oneHundredOrders();
        Ali1688Dp10ValidatedPage page = fixture.completePage(
                Ali1688HistoricalOrderProvider.Partition.CURRENT, orders);

        long started = System.nanoTime();
        Ali1688Dp10StagedPage staged = fixture.stage(task, 1L, 1, page);
        assertThat(elapsed(started)).isLessThan(Duration.ofSeconds(10));
        assertThat(staged.getRawRowCount()).isEqualTo(100);
        assertThat(database.count(
                "dp_pull_dp10_stage_page",
                "task_id=? AND generation_no=1 AND scan_pass=1 AND active_fence_epoch=4",
                task.getId())).isEqualTo(1);
        assertThat(database.count(
                "dp_pull_dp10_stage_item",
                "task_id=? AND generation_no=1 AND scan_pass=1",
                task.getId())).isEqualTo(100);
        assertThat(database.count(
                "dp_pull_dp10_stage_fingerprint_count",
                "task_id=? AND generation_no=1 AND pass_one_count=1 AND pass_two_count=0",
                task.getId())).isEqualTo(100);

        fixture.stage(task, 1L, 1, page);
        assertThat(database.count(
                "dp_pull_dp10_stage_page", "task_id=?", task.getId())).isEqualTo(1);
        assertThat(database.count(
                "dp_pull_dp10_stage_item", "task_id=?", task.getId())).isEqualTo(100);
        assertThat(database.count(
                "dp_pull_dp10_stage_fingerprint_count",
                "task_id=? AND pass_one_count=1 AND pass_two_count=0",
                task.getId())).isEqualTo(100);
        List<Ali1688HistoricalOrderProvider.OrderSnapshot> drift =
                new ArrayList<>(orders);
        drift.set(0, fixture.weightedOrder(
                "DRIFT-" + database.suffix(), 1));
        Ali1688Dp10ValidatedPage drifted = fixture.completePage(
                Ali1688HistoricalOrderProvider.Partition.CURRENT, drift);
        assertThatThrownBy(() -> fixture.stage(task, 1L, 1, drifted))
                .isInstanceOf(Ali1688Dp10PageContractException.class)
                .hasMessage("DP10_STAGED_PAGE_DRIFT");
        assertThat(database.count(
                "dp_pull_dp10_stage_item", "task_id=?", task.getId())).isEqualTo(100);
        assertThat(database.count(
                "dp_pull_dp10_stage_page", "task_id=?", task.getId())).isEqualTo(1);
        assertThat(database.count(
                "dp_pull_dp10_stage_fingerprint_count",
                "task_id=? AND pass_one_count=1 AND pass_two_count=0",
                task.getId())).isEqualTo(100);
        assertPoolRecovered();
    }

    @Test
    void appliesExactlyTwentyFactWeightAndCommitsHighWaterOnlyAfterAllPages() {
        String orderNo = "FACT-" + database.suffix();
        DataPullTask task = fixture.task("DP10_VERIFY", "fact-20");
        Ali1688Dp10ApplyCommand command = fixture.stageVerifiedGeneration(
                task, 1L, fixture.weightedOrder(orderNo, 1), 0L);
        assertThat(database.count(
                "dp_pull_dp10_stage_page",
                "task_id=? AND generation_no=1 AND scan_pass=2 "
                        + "AND state='VERIFIED' AND active_fence_epoch=4",
                task.getId())).isEqualTo(2);
        assertThat(database.count(
                "dp_pull_dp10_stage_identity",
                "task_id=? AND generation_no=1 AND active_fence_epoch=4",
                task.getId())).isEqualTo(1);
        assertSealed(fixture.seal(
                task, 1L, Ali1688HistoricalOrderProvider.Partition.CURRENT));
        assertSealed(fixture.seal(
                task, 1L, Ali1688HistoricalOrderProvider.Partition.HISTORY));
        database.switchToApply(task);

        long started = System.nanoTime();
        assertThat(fixture.advance(command, Duration.ofSeconds(10)))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);
        assertThat(elapsed(started)).isLessThan(Duration.ofSeconds(10));
        assertThat(database.applyCursor(task.getId(), 1L)).isEqualTo(10);
        assertThat(database.factCount("procurement_ali1688_order_header")).isEqualTo(1);
        assertThat(database.factCount("procurement_ali1688_order_item")).isEqualTo(10);
        assertThat(database.factCount("procurement_ali1688_order_logistics")).isEqualTo(10);
        assertThat(database.count(
                "dp_pull_dp10_stage_page",
                "task_id=? AND generation_no=1 AND state='APPLIED'", task.getId()))
                .isZero();
        assertThat(database.highWater()).isNull();
        assertThat(database.progressVersion()).isZero();

        assertThat(fixture.advance(command, Duration.ofSeconds(10)))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);
        assertThat(database.applyCursor(task.getId(), 1L)).isEqualTo(11);
        assertThat(database.factCount("procurement_ali1688_order_item")).isEqualTo(11);
        assertThat(database.factCount("procurement_ali1688_order_logistics")).isEqualTo(11);
        assertThat(database.highWater()).isNull();
        assertThat(fixture.advance(command, Duration.ofSeconds(10)))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);
        assertThat(database.highWater()).isNull();
        assertThat(fixture.advance(command, Duration.ofSeconds(10)))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);
        assertThat(database.highWater()).isNull();
        assertThat(fixture.advance(command, Duration.ofSeconds(10)))
                .isEqualTo(Ali1688Dp10FactAdvance.COMPLETE);
        assertThat(database.highWater())
                .isEqualTo(LocalDateTime.of(2026, 8, 4, 4, 0));
        assertThat(database.progressVersion()).isEqualTo(1L);

        DataPullTask replay = fixture.task("DP10_VERIFY", "fact-replay");
        Ali1688Dp10ApplyCommand replayCommand = fixture.stageVerifiedGeneration(
                replay, 2L, fixture.weightedOrder(orderNo, 1), 1L);
        database.switchToApply(replay);
        assertThat(fixture.advance(replayCommand, Duration.ofSeconds(10)))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);
        assertThat(database.applyCursor(replay.getId(), 2L)).isEqualTo(10);
        assertThat(database.factCount("procurement_ali1688_order_header")).isEqualTo(1);
        assertThat(database.factCount("procurement_ali1688_order_item")).isEqualTo(11);
        assertThat(database.factCount("procurement_ali1688_order_logistics")).isEqualTo(11);
        assertThat(database.highWater())
                .isEqualTo(LocalDateTime.of(2026, 8, 4, 4, 0));
        assertThat(database.progressVersion()).isEqualTo(1L);
        assertPoolRecovered();
    }

    @Test
    void lateLogisticsLockRollsBackEarlierFactsAndCursorThenCleanAdvanceRecovers()
            throws Exception {
        String orderNo = "ROLLBACK-" + database.suffix();
        DataPullTask baseline = fixture.task("DP10_VERIFY", "rollback-baseline");
        Ali1688Dp10ApplyCommand baselineCommand = fixture.stageVerifiedGeneration(
                baseline, 3L, fixture.weightedOrder(orderNo, 1), 0L);
        database.switchToApply(baseline);
        fixture.advance(baselineCommand, Duration.ofSeconds(10));
        assertThat(database.quantities(orderNo)).containsOnly(1).hasSize(10);
        assertThat(database.headerStatus(orderNo)).isEqualTo("CI-STATUS-1");
        assertThat(database.logisticsCompanies(orderNo))
                .containsOnly("CI-CARRIER-Q1").hasSize(10);

        DataPullTask retry = fixture.task("DP10_VERIFY", "rollback-retry");
        Ali1688Dp10ApplyCommand retryCommand = fixture.stageVerifiedGeneration(
                retry, 4L, fixture.weightedOrder(orderNo, 9), 0L);
        database.switchToApply(retry);
        try (Connection locker = database.lockLastLogistics(orderNo)) {
            long started = System.nanoTime();
            assertThatThrownBy(() -> fixture.advance(
                    retryCommand, Duration.ofMillis(500)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(elapsed(started)).isLessThan(Duration.ofSeconds(3));
            locker.rollback();
        }

        assertThat(database.quantities(orderNo)).containsOnly(1).hasSize(10);
        assertThat(database.headerStatus(orderNo)).isEqualTo("CI-STATUS-1");
        assertThat(database.logisticsCompanies(orderNo))
                .containsOnly("CI-CARRIER-Q1").hasSize(10);
        assertThat(database.applyCursor(retry.getId(), 4L)).isZero();
        assertThat(database.highWater()).isNull();
        assertPoolRecovered();

        assertThat(fixture.advance(retryCommand, Duration.ofSeconds(10)))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);
        assertThat(database.quantities(orderNo)).containsOnly(9).hasSize(10);
        assertThat(database.headerStatus(orderNo)).isEqualTo("CI-STATUS-9");
        assertThat(database.logisticsCompanies(orderNo))
                .containsOnly("CI-CARRIER-Q9").hasSize(10);
        assertThat(database.applyCursor(retry.getId(), 4L)).isEqualTo(10);
        assertPoolRecovered();
    }

    private void assertPoolRecovered() {
        try (Connection connection = context.pool().getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        } catch (Exception failure) {
            throw new AssertionError("DP10 exact-path pool did not recover", failure);
        }
        assertThat(context.pool().getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(context.pool().getHikariPoolMXBean().getIdleConnections()).isPositive();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    private void assertSealed(Ali1688Dp10SealBatch seal) {
        assertThat(seal.isMatching()).isTrue();
        assertThat(seal.isExhausted()).isTrue();
        assertThat(seal.getMatchedRawRows()).isEqualTo(1L);
        assertThat(seal.getCountRowsRead()).isEqualTo(1);
    }

    private Duration elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }
}
