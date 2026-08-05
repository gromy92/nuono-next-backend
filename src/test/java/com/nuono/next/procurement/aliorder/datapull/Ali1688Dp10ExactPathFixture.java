package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.orchestration.DataPullAdvanceDeadline;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.procurement.aliorder.Ali1688Dp10FactTransaction;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Public-interface fixture that drives the exact production stage and fact services. */
final class Ali1688Dp10ExactPathFixture {
    private final Ali1688Dp10ExactPathMySqlDatabase database;
    private final Ali1688Dp10MyBatisPageStageStore stageStore;
    private final Ali1688Dp10FactTransaction facts;

    Ali1688Dp10ExactPathFixture(Ali1688Dp10ExactPathMySqlContext context) {
        database = new Ali1688Dp10ExactPathMySqlDatabase(context.pool());
        stageStore = context.stageStore();
        facts = context.factTransaction();
    }

    Ali1688Dp10ExactPathMySqlDatabase database() {
        return database;
    }

    DataPullTask task(String step, String label) {
        return database.task(step, label);
    }

    Ali1688Dp10StagedPage stage(
            DataPullTask task,
            long generationNo,
            int pass,
            Ali1688Dp10ValidatedPage page
    ) {
        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(10))) {
            return stageStore.stageList(task, generationNo, pass, page, now());
        }
    }

    Ali1688Dp10SealBatch seal(
            DataPullTask task,
            long generationNo,
            Ali1688HistoricalOrderProvider.Partition partition
    ) {
        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(10))) {
            return stageStore.readSealBatch(task, generationNo, partition, null, now());
        }
    }

    Ali1688Dp10ApplyCommand stageVerifiedGeneration(
            DataPullTask task,
            long generationNo,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            long expectedProgressVersion
    ) {
        return stageVerifiedGeneration(
                task, generationNo, List.of(order), expectedProgressVersion);
    }

    Ali1688Dp10ApplyCommand stageVerifiedGeneration(
            DataPullTask task,
            long generationNo,
            List<Ali1688HistoricalOrderProvider.OrderSnapshot> orders,
            long expectedProgressVersion
    ) {
        List<Ali1688Dp10ListEntry> currentEntries = new ArrayList<>();
        for (int index = 0; index < orders.size(); index++) {
            currentEntries.add(entry(
                    index, orders.get(index), Ali1688Dp10ItemState.COMPLETE, null));
        }
        Ali1688Dp10ValidatedPage current = page(
                Ali1688HistoricalOrderProvider.Partition.CURRENT,
                currentEntries);
        Ali1688HistoricalOrderProvider.OrderSnapshot skipped =
                oneItemOrder("SKIP-" + generationNo + "-" + database.suffix(), 1);
        Ali1688Dp10ValidatedPage history = page(
                Ali1688HistoricalOrderProvider.Partition.HISTORY,
                List.of(entry(0, skipped, Ali1688Dp10ItemState.SKIP_BUSINESS_ITEM,
                        "DP10_EXACT_FIXTURE_SKIP")));
        for (int pass = 1; pass <= 2; pass++) {
            stage(task, generationNo, pass, current);
            stage(task, generationNo, pass, history);
        }
        Ali1688Dp10ApplyCommand command = command(
                task, generationNo, orders.size(), expectedProgressVersion);
        for (int advance = 0; advance < 10; advance++) {
            Ali1688Dp10FactAdvance result = advance(command, Duration.ofSeconds(10));
            if (result == Ali1688Dp10FactAdvance.APPLYING) return command;
        }
        throw new IllegalStateException("DP10 exact fixture did not finish verification");
    }

    Ali1688Dp10FactAdvance advance(
            Ali1688Dp10ApplyCommand command, Duration budget
    ) {
        try (DataPullAdvanceDeadline ignored = DataPullAdvanceDeadline.open(budget)) {
            return facts.advance(command);
        }
    }

    Ali1688Dp10ValidatedPage completePage(
            Ali1688HistoricalOrderProvider.Partition partition,
            List<Ali1688HistoricalOrderProvider.OrderSnapshot> orders
    ) {
        List<Ali1688Dp10ListEntry> entries = new ArrayList<>();
        for (int index = 0; index < orders.size(); index++) {
            entries.add(entry(
                    index, orders.get(index), Ali1688Dp10ItemState.COMPLETE, null));
        }
        return page(partition, entries);
    }

    List<Ali1688HistoricalOrderProvider.OrderSnapshot> oneHundredOrders() {
        List<Ali1688HistoricalOrderProvider.OrderSnapshot> orders = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            orders.add(oneItemOrder(
                    "STAGE-" + database.suffix() + "-" + index, index + 1));
        }
        return orders;
    }

    Ali1688HistoricalOrderProvider.OrderSnapshot weightedOrder(
            String providerOrderNo, int quantity
    ) {
        Ali1688HistoricalOrderProvider.OrderSnapshot order = baseOrder(providerOrderNo);
        applyVariant(order, quantity);
        List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> items = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            Ali1688HistoricalOrderProvider.OrderItemSnapshot item = item(
                    providerOrderNo, index, quantity);
            item.setLogisticsCompany("CI-CARRIER-Q" + quantity);
            item.setTrackingNo("CI-TRACK-" + providerOrderNo + "-" + index);
            items.add(item);
        }
        order.setItems(items);
        return order;
    }

    Ali1688HistoricalOrderProvider.OrderSnapshot singleItemOrder(
            String providerOrderNo, int quantity
    ) {
        return oneItemOrder(providerOrderNo, quantity);
    }

    private Ali1688HistoricalOrderProvider.OrderSnapshot oneItemOrder(
            String providerOrderNo, int quantity
    ) {
        Ali1688HistoricalOrderProvider.OrderSnapshot order = baseOrder(providerOrderNo);
        applyVariant(order, quantity);
        order.setItems(List.of(item(providerOrderNo, 0, quantity)));
        return order;
    }

    private void applyVariant(
            Ali1688HistoricalOrderProvider.OrderSnapshot order, int quantity
    ) {
        order.setOrderStatus("CI-STATUS-" + quantity);
        order.setAmountText(String.valueOf(quantity * 100));
        order.setRawSnapshotJson(
                "{\"fixture\":\"" + order.getProviderOrderNo()
                        + "\",\"variant\":" + quantity + "}");
    }

    private Ali1688HistoricalOrderProvider.OrderSnapshot baseOrder(String providerOrderNo) {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo(providerOrderNo);
        order.setProviderModifiedAt(Instant.parse("2026-08-04T03:00:00Z"));
        order.setOrderTime("2026-08-04 03:00:00");
        order.setOrderStatus("SUCCESS");
        order.setAmountText("100.00");
        order.setCurrency("CNY");
        order.setRawSnapshotJson("{\"fixture\":\"" + providerOrderNo + "\"}");
        return order;
    }

    private Ali1688HistoricalOrderProvider.OrderItemSnapshot item(
            String providerOrderNo, int index, int quantity
    ) {
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
        item.setProviderSubOrderId(providerOrderNo + "-SUB-" + index);
        item.setOfferId("OFFER-" + index);
        item.setSkuId("SKU-" + index);
        item.setTitle("DP10 exact item " + index);
        item.setQuantity(quantity);
        item.setUnit("piece");
        item.setUnitPriceText("10.00");
        item.setAmountText(String.valueOf(quantity * 10));
        item.setRawSnapshotJson("{\"item\":" + index + "}");
        return item;
    }

    private Ali1688Dp10ListEntry entry(
            int ordinal,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            Ali1688Dp10ItemState state,
            String code
    ) {
        return new Ali1688Dp10ListEntry(
                ordinal, order, state, code, Ali1688Dp10RawOrderFingerprint.fingerprint(order));
    }

    private Ali1688Dp10ValidatedPage page(
            Ali1688HistoricalOrderProvider.Partition partition,
            List<Ali1688Dp10ListEntry> entries
    ) {
        return new Ali1688Dp10ValidatedPage(
                partition, 1, entries.size(), entries.size(), 1, entries);
    }

    private Ali1688Dp10ApplyCommand command(
            DataPullTask task,
            long generationNo,
            long currentExpectedTotal,
            long expectedProgressVersion
    ) {
        return new Ali1688Dp10ApplyCommand(
                task, database.authorization(), generationNo,
                currentExpectedTotal, 1, 1L, 1, expectedProgressVersion,
                Instant.parse("2026-08-04T04:00:00Z"), now());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
