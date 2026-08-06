package com.nuono.next.datapull.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleTaskPayloadBinderRegistryTest {

    @Test
    void missingOperationFailsClosed() {
        ScheduleTaskPayloadBinderRegistry registry =
                new ScheduleTaskPayloadBinderRegistry(List.of());

        assertThatThrownBy(() -> registry.require(OperationCode.DP08A))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DP_SCHEDULE_PAYLOAD_BINDER_MISSING:DP08A");
    }

    @Test
    void duplicateOperationRegistrationFailsClosed() {
        ScheduleTaskPayloadBinder first = binder(OperationCode.DP08B);
        ScheduleTaskPayloadBinder second = binder(OperationCode.DP08B);

        assertThatThrownBy(() -> new ScheduleTaskPayloadBinderRegistry(
                List.of(first, second)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate schedule task payload binder for DP08B");
    }

    @Test
    void registeredOperationResolvesItsSingleAdapter() {
        ScheduleTaskPayloadBinder binder = binder(OperationCode.DP08A);
        ScheduleTaskPayloadBinderRegistry registry =
                new ScheduleTaskPayloadBinderRegistry(List.of(binder));

        assertThat(registry.require(OperationCode.DP08A)).isSameAs(binder);
    }

    private ScheduleTaskPayloadBinder binder(OperationCode operation) {
        return new ScheduleTaskPayloadBinder() {
            @Override public Set<OperationCode> operations() { return Set.of(operation); }
            @Override public void bind(
                    OperationCode ignored,
                    List<DataPullTask> tasks,
                    List<ScheduleTaskBindingRow> bindings
            ) {
                throw new AssertionError("registry test never binds tasks");
            }
        };
    }
}
