package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.NoonPullSmokeRunMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("local-db")
public class MyBatisNoonPullSmokeRunRepository implements NoonPullSmokeRunRepository {
    private static final String RUN_SEQUENCE = "noon_pull_smoke_run";
    private static final String EVIDENCE_SEQUENCE = "noon_pull_smoke_evidence";
    private static final long RUN_INITIAL_ID = 140000L;
    private static final long EVIDENCE_INITIAL_ID = 141000L;

    private final NoonPullSmokeRunMapper mapper;
    private final AtomicBoolean schemaEnsured = new AtomicBoolean(false);

    public MyBatisNoonPullSmokeRunRepository(NoonPullSmokeRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public NoonPullSmokeRunRecord save(NoonPullSmokeRunRecord run) {
        ensureSchema();
        NoonPullSmokeRunRecord persisted = run.copy();
        LocalDateTime now = LocalDateTime.now();
        if (persisted.getId() == null) {
            persisted.setId(nextId(RUN_SEQUENCE, RUN_INITIAL_ID));
        }
        if (persisted.getCreatedAt() == null) {
            persisted.setCreatedAt(now);
        }
        persisted.setUpdatedAt(now);
        mapper.insertRun(persisted);

        List<NoonPullSmokeEvidenceRecord> evidence = new ArrayList<>();
        int sequence = 1;
        for (NoonPullSmokeEvidenceRecord item : persisted.getEvidence()) {
            NoonPullSmokeEvidenceRecord evidenceRecord = item.copy();
            if (evidenceRecord.getId() == null) {
                evidenceRecord.setId(nextId(EVIDENCE_SEQUENCE, EVIDENCE_INITIAL_ID));
            }
            evidenceRecord.setRunId(persisted.getId());
            evidenceRecord.setSequenceNo(sequence++);
            if (evidenceRecord.getCreatedAt() == null) {
                evidenceRecord.setCreatedAt(now);
            }
            evidenceRecord.setUpdatedAt(now);
            mapper.insertEvidence(evidenceRecord);
            evidence.add(evidenceRecord);
        }
        persisted.setEvidence(evidence);
        return persisted.copy();
    }

    @Override
    public List<NoonPullSmokeRunRecord> listRecent(int limit) {
        ensureSchema();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<NoonPullSmokeRunRecord> runs = mapper.selectRecentRuns(safeLimit);
        for (NoonPullSmokeRunRecord run : runs) {
            run.setEvidence(mapper.selectEvidenceByRunId(run.getId()));
        }
        return runs;
    }

    private Long nextId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        mapper.nextId(command);
        Long allocatedId = command.getAllocatedId();
        if (allocatedId == null || allocatedId <= 0) {
            throw new IllegalStateException("Noon smoke evidence ID allocation failed.");
        }
        return allocatedId;
    }

    private void ensureSchema() {
        if (!schemaEnsured.compareAndSet(false, true)) {
            return;
        }
        mapper.ensureNoonPullIdSequence();
        mapper.ensureSmokeRunTable();
        mapper.ensureSmokeEvidenceTable();
    }
}
