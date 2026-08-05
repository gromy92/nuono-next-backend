package com.nuono.next.datapull.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DataPullTaskTransitionContractTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0);

    @Test
    void retryTimeIsPresentExactlyForTimedWaitingStates() {
        assertDoesNotThrow(() -> transition(TaskState.WAITING_REMOTE, NOW.plusMinutes(1), "REMOTE"));
        assertThrows(
                IllegalArgumentException.class,
                () -> transition(TaskState.WAITING_REMOTE, null, "REMOTE")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> transition(TaskState.QUEUED, NOW.plusMinutes(1), null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> transition(TaskState.FAILED, NOW.plusMinutes(1), "FAILED")
        );
    }

    @Test
    void failureCodeIsPresentExactlyForFailureAndWaitingStates() {
        assertDoesNotThrow(() -> transition(TaskState.FAILED, null, "FAILED"));
        assertDoesNotThrow(() -> transition(TaskState.SUCCEEDED, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> transition(TaskState.WAITING_BACKOFF, NOW.plusMinutes(1), null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> transition(TaskState.QUEUED, null, "UNEXPECTED")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> transition(TaskState.SUCCEEDED, null, "UNEXPECTED")
        );
    }

    private DataPullTaskTransition transition(
            TaskState state,
            LocalDateTime retryNotBefore,
            String failureCode
    ) {
        return new DataPullTaskTransition(
                1L,
                1L,
                1L,
                "worker-1",
                state,
                "STEP",
                null,
                "checkpoint",
                retryNotBefore,
                failureCode,
                state.isTerminal() ? NOW : null,
                NOW
        );
    }
}
