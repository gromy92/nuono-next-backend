package com.nuono.next.datapull.leader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.DataPullRuntimeLeaderMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyBatisDataPullRuntimeLeaderStoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 4, 0);

    @Mock
    private DataPullRuntimeLeaderMapper mapper;

    @Test
    void returnsOnlyTheLiveDatabaseRowAfterAtomicAcquire() {
        DataPullRuntimeLeaderRow row = row("jvm-a", 17L, NOW.plusSeconds(120));
        when(mapper.acquireOrRenew("jvm-a", 120)).thenReturn(1);
        when(mapper.selectOwnedLive("jvm-a")).thenReturn(row);
        MyBatisDataPullRuntimeLeaderStore store = new MyBatisDataPullRuntimeLeaderStore(mapper);

        DataPullRuntimeLeaderLease lease = store.acquireOrRenew(
                "jvm-a", Duration.ofSeconds(120)
        ).orElseThrow();

        assertEquals(17L, lease.getEpoch());
        assertEquals(NOW, lease.getDatabaseTime());
    }

    @Test
    void zeroRowAcquireIsANormalFollowerOutcome() {
        when(mapper.acquireOrRenew("jvm-b", 120)).thenReturn(0);
        MyBatisDataPullRuntimeLeaderStore store = new MyBatisDataPullRuntimeLeaderStore(mapper);

        assertFalse(store.acquireOrRenew("jvm-b", Duration.ofSeconds(120)).isPresent());

        verify(mapper).selectOwnedLive("jvm-b");
    }

    @Test
    void rejectsImpossibleMultiplicityAndValidatesCurrentEpoch() {
        DataPullRuntimeLeaderLease lease = row(
                "jvm-a", 17L, NOW.plusSeconds(120)
        ).toLease();
        MyBatisDataPullRuntimeLeaderStore store = new MyBatisDataPullRuntimeLeaderStore(mapper);
        when(mapper.countCurrent("jvm-a", 17L)).thenReturn(1).thenReturn(0).thenReturn(2);

        assertTrue(store.isCurrent(lease));
        assertFalse(store.isCurrent(lease));
        assertThrows(IllegalStateException.class, () -> store.isCurrent(lease));
    }

    private DataPullRuntimeLeaderRow row(String owner, long epoch, LocalDateTime leaseUntil) {
        DataPullRuntimeLeaderRow row = new DataPullRuntimeLeaderRow();
        row.setOwner(owner);
        row.setEpoch(epoch);
        row.setLeaseUntil(leaseUntil);
        row.setDatabaseTime(NOW);
        return row;
    }
}
