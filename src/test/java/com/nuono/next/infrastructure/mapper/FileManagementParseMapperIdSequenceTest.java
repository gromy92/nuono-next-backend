package com.nuono.next.infrastructure.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicReference;
import org.mockito.Answers;
import org.junit.jupiter.api.Test;

class FileManagementParseMapperIdSequenceTest {

    @Test
    void shouldAllocateDynamicActiveVersionIdsAboveSeededActiveVersionRows() {
        AtomicReference<IdSequenceCommand> capturedCommand = new AtomicReference<>();
        FileManagementParseMapper mapper = mock(FileManagementParseMapper.class, Answers.CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            IdSequenceCommand command = invocation.getArgument(0);
            capturedCommand.set(command);
            command.setAllocatedId(command.getInitialValue() + 1);
            return 1;
        }).when(mapper).allocateFileParseId(any(IdSequenceCommand.class));

        Long allocatedId = mapper.nextActiveVersionId();

        assertEquals(72001L, allocatedId);
        assertNotNull(capturedCommand.get());
        assertEquals("file_mgmt_parse_active_version", capturedCommand.get().getSequenceName());
        assertEquals(72000L, capturedCommand.get().getInitialValue());
    }
}
