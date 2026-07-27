package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonAuthTransientBackoffMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MyBatisNoonAuthTransientBackoffRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 25, 6, 0);

    @Test
    void missingLogicalStoreIsCreatedFromTheStableOwnerProjectIdentity() {
        NoonAuthTransientBackoffMapper mapper = mock(NoonAuthTransientBackoffMapper.class);
        ProductManagementMapper productMapper = mock(ProductManagementMapper.class);
        MyBatisNoonAuthTransientBackoffRepository repository =
                new MyBatisNoonAuthTransientBackoffRepository(mapper, productMapper);

        when(mapper.resolveLogicalStoreId(307L, "PRJ307"))
                .thenReturn(null, 7001L);
        when(productMapper.nextLogicalStoreId()).thenReturn(7001L);

        assertEquals(
                7001L,
                repository.resolveLogicalStoreId(307L, "PRJ307")
        );
        verify(mapper).insertLogicalStoreIfAbsent(
                7001L,
                307L,
                "PRJ307"
        );
    }

    @Test
    void deletedLogicalStoreNaturalKeyIsNeverSilentlyReactivated() {
        NoonAuthTransientBackoffMapper mapper = mock(NoonAuthTransientBackoffMapper.class);
        ProductManagementMapper productMapper = mock(ProductManagementMapper.class);
        MyBatisNoonAuthTransientBackoffRepository repository =
                new MyBatisNoonAuthTransientBackoffRepository(mapper, productMapper);

        when(mapper.resolveLogicalStoreId(307L, "PRJ307")).thenReturn(null);
        when(productMapper.nextLogicalStoreId()).thenReturn(7002L);
        when(mapper.insertLogicalStoreIfAbsent(7002L, 307L, "PRJ307")).thenReturn(0);

        assertNull(repository.resolveLogicalStoreId(307L, "PRJ307"));
        verify(mapper).insertLogicalStoreIfAbsent(7002L, 307L, "PRJ307");
    }

    @Test
    void staleFenceCannotIncrementOrResetBackoff() {
        NoonAuthTransientBackoffMapper mapper = mock(NoonAuthTransientBackoffMapper.class);
        MyBatisNoonAuthTransientBackoffRepository repository =
                new MyBatisNoonAuthTransientBackoffRepository(mapper);
        NoonAuthTransientBackoffWriteFence fence = fence();
        NoonAuthTransientBackoffState failure = failure();

        when(mapper.lockRecoveryById(177L)).thenReturn(177L);
        when(mapper.countCurrentRecoveryFence(fence)).thenReturn(0);

        assertNull(repository.incrementFailure(failure, fence, NOW));
        assertFalse(repository.resetForRecovery(7001L, 177L, fence, NOW));

        verify(mapper, never()).incrementFailure(any());
        verify(mapper, never()).resetForRecovery(anyLong(), anyLong(), any());
    }

    @Test
    void validFenceIsLockedAndRecheckedBeforeEachMutation() {
        NoonAuthTransientBackoffMapper mapper = mock(NoonAuthTransientBackoffMapper.class);
        MyBatisNoonAuthTransientBackoffRepository repository =
                new MyBatisNoonAuthTransientBackoffRepository(mapper);
        NoonAuthTransientBackoffWriteFence fence = fence();
        NoonAuthTransientBackoffState failure = failure();
        NoonAuthTransientBackoffState persisted = failure();

        when(mapper.lockRecoveryById(177L)).thenReturn(177L);
        when(mapper.countCurrentRecoveryFence(fence)).thenReturn(1);
        when(mapper.selectState(7001L, NoonTransientErrorType.NETWORK_EOF))
                .thenReturn(persisted);

        assertSame(persisted, repository.incrementFailure(failure, fence, NOW));
        assertTrue(repository.resetForRecovery(7001L, 177L, fence, NOW));

        InOrder order = inOrder(mapper);
        order.verify(mapper).lockRecoveryById(177L);
        order.verify(mapper).countCurrentRecoveryFence(fence);
        order.verify(mapper).incrementFailure(failure);
        order.verify(mapper).selectState(7001L, NoonTransientErrorType.NETWORK_EOF);
        order.verify(mapper).lockRecoveryById(177L);
        order.verify(mapper).countCurrentRecoveryFence(fence);
        order.verify(mapper).resetForRecovery(7001L, 177L, NOW);
    }

    private NoonAuthTransientBackoffWriteFence fence() {
        return new NoonAuthTransientBackoffWriteFence(
                177L,
                NoonAuthRecoveryStatus.APPLYING_PROJECTS,
                9L,
                "lease-177"
        );
    }

    private NoonAuthTransientBackoffState failure() {
        NoonAuthTransientBackoffState state = new NoonAuthTransientBackoffState();
        state.setLogicalStoreId(7001L);
        state.setErrorType(NoonTransientErrorType.NETWORK_EOF);
        return state;
    }
}
