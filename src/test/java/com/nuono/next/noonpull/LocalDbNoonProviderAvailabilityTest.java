package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncStoreRecord;
import org.junit.jupiter.api.Test;

class LocalDbNoonProviderAvailabilityTest {

    @Test
    void blocksPlanBeforeTaskCreationWhenProjectAuthIsBlocked() {
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
        LocalDbNoonProviderAvailability availability =
                new LocalDbNoonProviderAvailability(storeSyncMapper, authGate);
        NoonPullPlanRecord plan = plan(307L, "STR108065-NSA");
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ108065");
        when(storeSyncMapper.selectOwnerProject(307L, "STR108065-NSA")).thenReturn(project);
        when(authGate.isBlocked(307L, "PRJ108065")).thenReturn(true);

        assertFalse(availability.isAvailable(plan));

        verify(authGate).isBlocked(307L, "PRJ108065");
    }

    @Test
    void keepsExistingProviderFailureClassificationWhenProjectBindingCannotBeResolved() {
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
        LocalDbNoonProviderAvailability availability =
                new LocalDbNoonProviderAvailability(storeSyncMapper, authGate);

        assertTrue(availability.isAvailable(plan(307L, "UNKNOWN")));
    }

    private NoonPullPlanRecord plan(Long ownerUserId, String storeCode) {
        NoonPullPlanRecord plan = new NoonPullPlanRecord();
        plan.setOwnerUserId(ownerUserId);
        plan.setStoreCode(storeCode);
        plan.setSiteCode("SA");
        return plan;
    }
}
