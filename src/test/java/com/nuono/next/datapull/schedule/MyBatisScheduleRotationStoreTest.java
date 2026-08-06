package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MyBatisScheduleRotationStoreTest {

    @Test
    void elevenOperationsRemainFairWithThreePersistentReservationsPerTick() {
        DataPullScheduleScanMapper mapper = mock(DataPullScheduleScanMapper.class);
        AtomicInteger ordinal = new AtomicInteger();
        AtomicLong version = new AtomicLong();
        when(mapper.lockRotation()).thenAnswer(ignored -> row(ordinal.get(), version.get()));
        when(mapper.advanceRotation(anyInt(), anyLong())).thenAnswer(invocation -> {
            ordinal.set(invocation.getArgument(0));
            version.incrementAndGet();
            return 1;
        });
        MyBatisScheduleRotationStore store = new MyBatisScheduleRotationStore(mapper);
        List<OperationCode> available = Arrays.asList(OperationCode.values());
        List<OperationCode> selected = new ArrayList<>();

        for (int tick = 0; tick < OperationCode.values().length; tick++) {
            selected.addAll(store.reserve(available));
        }

        Map<OperationCode, Integer> counts = new EnumMap<>(OperationCode.class);
        for (OperationCode operation : selected) counts.merge(operation, 1, Integer::sum);
        for (OperationCode operation : OperationCode.values()) {
            assertEquals(3, counts.get(operation));
        }
        Set<OperationCode> firstFourTicks = new HashSet<>(selected.subList(0, 12));
        assertEquals(Set.of(OperationCode.values()), firstFourTicks);
    }

    private static ScheduleRotationRow row(int ordinal, long version) {
        ScheduleRotationRow row = new ScheduleRotationRow();
        row.setNextOperationOrdinal(ordinal);
        row.setVersion(version);
        return row;
    }
}
