package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Ali1688PluginAssignmentServiceTest {

    @Mock
    private Ali1688CollectionMapper ali1688CollectionMapper;

    @Mock
    private Ali1688CandidateAiAssessmentService aiAssessmentService;

    private Ali1688PluginAssignmentService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new Ali1688PluginAssignmentService(
                ali1688CollectionMapper,
                new Ali1688CandidateScoringService(objectMapper),
                aiAssessmentService,
                objectMapper
        );
    }

    @Test
    void listAssignmentsReturnsSystemQueueForBusinessScope() {
        Ali1688CollectionRecords.TaskRecord task = new Ali1688CollectionRecords.TaskRecord();
        task.id = 87001L;
        task.sourceCollectionId = 86001L;
        task.currentTaskKey = "86001";
        task.ownerUserId = 307L;
        task.logicalStoreId = 50005L;
        task.taskNo = "ALI1688-87001";
        task.status = "queued";
        task.sourceImageUrl = "https://images.example.com/source.jpg";
        task.sourceTitle = "Artificial Flowers";
        task.sourceTitleCn = "仿真花";
        task.sourceUrl = "https://www.noon.com/item/Z123/p/";
        task.pageUrl = "https://www.noon.com/item/Z123/p/";
        task.sourceSpecHintsJson = "[\"规格: 2只装\",\"尺寸: 14cm\"]";
        task.sourceSelectedText = "蓝色 14cm";
        task.storeName = "canman";
        task.storeCode = "STR108065-NAE";

        when(ali1688CollectionMapper.listPluginAssignmentTasks(eq(307L), eq(List.of("STR108065-NAE")), eq(50)))
                .thenReturn(List.of(task));

        Ali1688PluginAssignmentListView result = service.listAssignments(access());

        assertEquals(1, result.summary.total);
        assertEquals(1, result.summary.pending);
        assertEquals("ALI1688-PLUGIN-87001", result.items.get(0).assignmentCode);
        assertEquals("created", result.items.get(0).status);
        assertEquals("CANDIDATE_COLLECTION", result.items.get(0).assignmentType);
        assertEquals("target_spec_available", result.items.get(0).targetSkuSelection.get("status"));
        assertEquals("蓝色 14cm", result.items.get(0).targetSkuSelection.get("sourceSku"));
        verify(ali1688CollectionMapper).listPluginAssignmentTasks(307L, List.of("STR108065-NAE"), 50);
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(991L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR108065-NAE"))
                .build();
    }
}
