package com.nuono.next.nooncompleteness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface NoonDataCompletenessRepository {
    Long nextId(String sequenceName, Long initialValue);

    void insertCompleteness(NoonDataCompletenessRecord record);

    List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query);

    void insertGapWindow(NoonDataGapWindowRecord record);

    List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query);

    default void deleteHistoryBackfillGapsOutsideRange(
            Long completenessId,
            NoonDataCategory category,
            LocalDate dateFrom,
            LocalDate dateTo,
            LocalDateTime updatedAt
    ) {
    }
}
