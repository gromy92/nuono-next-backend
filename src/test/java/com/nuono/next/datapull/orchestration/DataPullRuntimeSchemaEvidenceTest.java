package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.DataPullReleaseDatabaseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

class DataPullRuntimeSchemaEvidenceTest {

    @Test
    void exactDatabaseLivecheckBindingIsRequired() {
        DataPullReleaseDatabaseMapper mapper = Mockito.mock(
                DataPullReleaseDatabaseMapper.class
        );
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(
                DataPullManagedReleaseProvenanceEvidence.SCHEMA_BINDING,
                "a".repeat(64)
        );
        DataPullReleaseDatabaseBinding binding = new DataPullReleaseDatabaseBinding();
        binding.setSchemaBindingSha256("a".repeat(64));
        when(mapper.selectBinding()).thenReturn(binding);
        DataPullRuntimeSchemaEvidence evidence =
                new DataPullRuntimeSchemaEvidence(mapper, environment);

        assertEquals(DataPullRuntimeReleaseRequirement.RUNTIME_SCHEMA, evidence.requirement());
        assertTrue(evidence.verified());
        binding.setSchemaBindingSha256("b".repeat(64));
        assertFalse(evidence.verified());
        when(mapper.selectBinding()).thenReturn(null);
        assertFalse(evidence.verified());
    }
}
