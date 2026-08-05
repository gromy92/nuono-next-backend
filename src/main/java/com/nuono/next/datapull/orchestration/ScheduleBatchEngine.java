package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullSchedule;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Deep seam used by the runtime kernel to advance persisted schedule batches. */
public interface ScheduleBatchEngine {

    List<OperationCode> reserveOperations(List<OperationCode> available);

    Advance advance(DataPullJob job, DataPullSchedule schedule, Instant observedAt);

    /** One transactional schedule advance; a rejected cohort never exposes partial tasks. */
    final class Advance {
        private final List<DataPullTask> tasks;
        private final boolean failed;

        private Advance(List<DataPullTask> tasks, boolean failed) {
            this.tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
            if (failed && !this.tasks.isEmpty()) {
                throw new IllegalArgumentException(
                        "failed schedule advance cannot return tasks"
                );
            }
            this.failed = failed;
        }

        public static Advance succeeded(List<DataPullTask> tasks) {
            return new Advance(tasks, false);
        }

        public static Advance failed() {
            return new Advance(List.of(), true);
        }

        public List<DataPullTask> getTasks() {
            return tasks;
        }

        public boolean isFailed() {
            return failed;
        }
    }
}
