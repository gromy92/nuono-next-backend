package com.nuono.next.infrastructure.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.SelectKey;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class CompetitorListingObservationMapperIdSequenceTest {

    @Test
    void readsAllocatedIdFromMysqlLastInsertId() throws Exception {
        Method method = CompetitorListingObservationMapper.class.getMethod(
                "allocateCompetitorAnalysisId",
                IdSequenceCommand.class
        );
        SelectKey selectKey = method.getAnnotation(SelectKey.class);

        assertNotNull(selectKey);
        assertEquals("allocatedId", selectKey.keyProperty());
        assertEquals("SELECT LAST_INSERT_ID()", selectKey.statement()[0]);
    }

    @Test
    void returnsTheAllocatedObservationId() {
        CompetitorListingObservationMapper mapper = realDefaultMethods();
        doAnswer(invocation -> {
            IdSequenceCommand command = invocation.getArgument(0);
            command.setAllocatedId(280001L);
            return 1;
        }).when(mapper).allocateCompetitorAnalysisId(any());

        assertEquals(280001L, mapper.nextListingObservationId());
    }

    @Test
    void failsBeforeInsertWhenMysqlDoesNotReturnAnId() {
        CompetitorListingObservationMapper mapper = realDefaultMethods();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                mapper::nextListingObservationId
        );

        assertEquals(
                "竞品列表观察 ID 序列分配失败：operations_competitor_listing_observation",
                error.getMessage()
        );
    }

    private static CompetitorListingObservationMapper realDefaultMethods() {
        return mock(
                CompetitorListingObservationMapper.class,
                withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS)
        );
    }
}
