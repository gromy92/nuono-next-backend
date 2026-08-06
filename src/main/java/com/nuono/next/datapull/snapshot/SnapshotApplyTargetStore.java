package com.nuono.next.datapull.snapshot;

import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Binds one idempotent domain staging target to a snapshot apply task. */
public final class SnapshotApplyTargetStore {
    private final SnapshotFactApplyMapper mapper;
    private final Clock clock;

    public SnapshotApplyTargetStore(SnapshotFactApplyMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    SnapshotApplyTargetStore(SnapshotFactApplyMapper mapper, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public long resolve(
            CompleteSnapshot<?> snapshot,
            String targetRefType,
            LongSupplier allocateTargetId
    ) {
        CompleteSnapshot<?> value = Objects.requireNonNull(snapshot, "snapshot");
        String type = requireType(targetRefType);
        LongSupplier allocator = Objects.requireNonNull(allocateTargetId, "allocateTargetId");
        SnapshotApplyProgressRow progress = requireProgress(value);
        if (progress.getTargetRefId() != null || progress.getTargetRefType() != null) {
            return requireBoundTarget(progress, type);
        }
        if (!"PREPARING".equals(progress.getState())) {
            throw new IllegalStateException("snapshot carry cannot allocate a new target");
        }
        long targetId = allocator.getAsLong();
        if (targetId < 1L) {
            throw new IllegalStateException("snapshot apply target id must be positive");
        }
        int updated = mapper.bindTargetRef(
                value.getTaskId(), value.getFenceEpoch(), type, targetId, nowUtc()
        );
        if (updated != 1) {
            throw new IllegalStateException("snapshot apply target binding lost its fence");
        }
        return requireBoundTarget(requireProgress(value), type);
    }

    private SnapshotApplyProgressRow requireProgress(CompleteSnapshot<?> snapshot) {
        SnapshotApplyProgressRow progress = mapper.selectProgressForUpdate(snapshot.getTaskId());
        if (progress == null
                || !Objects.equals(progress.getTaskId(), snapshot.getTaskId())
                || !Objects.equals(progress.getActiveFenceEpoch(), snapshot.getFenceEpoch())
                || !("PREPARING".equals(progress.getState())
                        || "CARRYING".equals(progress.getState()))) {
            throw new IllegalStateException("snapshot apply target has no current progress fence");
        }
        return progress;
    }

    private long requireBoundTarget(SnapshotApplyProgressRow progress, String expectedType) {
        if (!Objects.equals(progress.getTargetRefType(), expectedType)
                || progress.getTargetRefId() == null
                || progress.getTargetRefId() < 1L) {
            throw new IllegalStateException("snapshot apply target identity drift");
        }
        return progress.getTargetRefId();
    }

    private String requireType(String value) {
        if (value == null || value.isEmpty() || !value.equals(value.trim())
                || !value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("snapshot apply target type is invalid");
        }
        return value;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
