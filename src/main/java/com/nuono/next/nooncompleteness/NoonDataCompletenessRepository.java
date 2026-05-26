package com.nuono.next.nooncompleteness;

import java.time.LocalDateTime;
import java.util.List;

public interface NoonDataCompletenessRepository {
    Long nextId(String sequenceName, Long initialValue);

    void insertCompleteness(NoonDataCompletenessRecord record);

    List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query);

    void insertGapWindow(NoonDataGapWindowRecord record);

    List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query);

    default void deleteHistoryBackfillGaps(
            Long completenessId,
            NoonDataCategory category,
            LocalDateTime updatedAt
    ) {
    }
}
