package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorProductChangeMapper;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.Map;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorProductChangeServiceTest {

    @Mock
    private CompetitorAnalysisMapper analysisMapper;

    @Mock
    private CompetitorProductChangeMapper changeMapper;

    @Test
    void productChangesGroupsFieldEventsByProductAndDate() {
        CompetitorProductChangeService service = new CompetitorProductChangeService(changeMapper, analysisMapper);
        when(analysisMapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct(180123L, "ZSELF001"));
        when(changeMapper.listProductChangeEvents(501L, 180123L, 50)).thenReturn(List.of(
                changeRow(270001L, "N51360862A", "COMPETITOR", "2026-06-08", "price", "价格", "VALUE_CHANGED", "99.90", "89.90", "WARNING"),
                changeRow(270002L, "N51360862A", "COMPETITOR", "2026-06-08", "mainImage", "主图资产", "VALUE_CHANGED", "\"old.jpg\"", "\"new.jpg\"", "INFO"),
                changeRow(270003L, "ZSELF001", "SELF", "2026-06-07", "title", "标题", "VALUE_CHANGED", "\"Old title\"", "\"New title\"", "INFO")
        ));

        CompetitorProductChangeListView view = service.productChanges(context(), 180123L, 50);

        assertEquals(2, view.getItems().size());
        assertEquals("2026-06-08", view.getItems().get(0).getFactDate());
        assertEquals("N51360862A", view.getItems().get(0).getNoonProductCode());
        assertEquals("competitor", view.getItems().get(0).getSubjectType());
        assertEquals(2, view.getItems().get(0).getChanges().size());
        assertEquals("price", view.getItems().get(0).getChanges().get(0).getFieldKey());
        assertEquals("warning", view.getItems().get(0).getChanges().get(0).getSeverity());
        assertEquals("ZSELF001", view.getItems().get(1).getNoonProductCode());
        assertEquals("self", view.getItems().get(1).getSubjectType());
        verify(changeMapper).listProductChangeEvents(501L, 180123L, 50);
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 501L))
                .build();
    }

    private static CompetitorWatchProductRow watchProduct(Long id, String selfCode) {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(id);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode(selfCode);
        return row;
    }

    private static CompetitorProductChangeEventRow changeRow(
            Long id,
            String noonProductCode,
            String subjectType,
            String factDate,
            String fieldKey,
            String fieldLabel,
            String changeType,
            String oldValueJson,
            String newValueJson,
            String severity
    ) {
        CompetitorProductChangeEventRow row = new CompetitorProductChangeEventRow();
        row.setId(id);
        row.setFactDate(LocalDate.parse(factDate));
        row.setNoonProductCode(noonProductCode);
        row.setProductName(noonProductCode);
        row.setSubjectType(subjectType);
        row.setFieldKey(fieldKey);
        row.setFieldLabel(fieldLabel);
        row.setChangeType(changeType);
        row.setOldValueJson(oldValueJson);
        row.setNewValueJson(newValueJson);
        row.setSeverity(severity);
        return row;
    }
}
