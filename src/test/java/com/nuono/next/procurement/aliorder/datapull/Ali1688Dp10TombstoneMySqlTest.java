package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.datapull.persistence.DataPullTask;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** Real MySQL proof that one manually deleted DP10 header skips only that order. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Ali1688Dp10TombstoneMySqlTest {
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
    void manualTombstoneSkipsOneOrderThenAppliesNextAndCommitsHighWater() {
        String deletedOrderNo = "DELETED-" + database.suffix();
        String nextOrderNo = "NEXT-" + database.suffix();
        database.insertManualTombstone(deletedOrderNo);
        String tombstoneAudit = database.headerAudit(deletedOrderNo);
        DataPullTask task = fixture.task("DP10_VERIFY", "tombstone-skip");
        Ali1688Dp10ApplyCommand command = fixture.stageVerifiedGeneration(
                task,
                5L,
                List.of(
                        fixture.singleItemOrder(deletedOrderNo, 1),
                        fixture.singleItemOrder(nextOrderNo, 1)),
                0L);
        database.switchToApply(task);

        assertThat(fixture.advance(command, Duration.ofSeconds(10)))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);
        assertThat(database.stageOutcome(task.getId(), 5L, deletedOrderNo))
                .isEqualTo("SKIP_BUSINESS_ITEM|VERIFIED|SKIPPED|"
                        + "DP10_ORDER_HEADER_MANUALLY_DELETED|0");
        assertThat(database.headerAudit(deletedOrderNo)).isEqualTo(tombstoneAudit);
        assertThat(database.activeFactCountForOrder(
                "procurement_ali1688_order_header", deletedOrderNo)).isZero();
        assertThat(database.factRowCountForOrder(
                "procurement_ali1688_order_item", deletedOrderNo)).isZero();
        assertThat(database.factRowCountForOrder(
                "procurement_ali1688_order_logistics", deletedOrderNo)).isZero();

        assertThat(fixture.advance(command, Duration.ofSeconds(10)))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);
        assertThat(database.stageOutcome(task.getId(), 5L, nextOrderNo))
                .isEqualTo("COMPLETE|VERIFIED|APPLIED|<null>|1");
        assertThat(database.activeFactCountForOrder(
                "procurement_ali1688_order_header", nextOrderNo)).isEqualTo(1);
        assertThat(database.activeFactCountForOrder(
                "procurement_ali1688_order_item", nextOrderNo)).isEqualTo(1);

        Ali1688Dp10FactAdvance outcome = Ali1688Dp10FactAdvance.APPLYING;
        for (int advance = 0;
             advance < 4 && outcome != Ali1688Dp10FactAdvance.COMPLETE;
             advance++) {
            outcome = fixture.advance(command, Duration.ofSeconds(10));
        }
        assertThat(outcome).isEqualTo(Ali1688Dp10FactAdvance.COMPLETE);
        assertThat(database.headerAudit(deletedOrderNo)).isEqualTo(tombstoneAudit);
        assertThat(database.highWater())
                .isEqualTo(LocalDateTime.of(2026, 8, 4, 4, 0));
        assertThat(database.progressVersion()).isEqualTo(1L);
        assertThat(database.count(
                "dp_pull_dp10_stage_page",
                "task_id=? AND generation_no=5 AND state='APPLIED'",
                task.getId())).isEqualTo(2);
        assertThat(context.pool().getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(context.pool().getHikariPoolMXBean().getIdleConnections()).isPositive();
    }
}
