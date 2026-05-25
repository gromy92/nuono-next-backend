package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonProductInitializationStatusServiceTest {

    private InMemoryNoonPullRepository repository;
    private NoonProductInitializationStatusService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryNoonPullRepository();
        service = new NoonProductInitializationStatusService(repository);
    }

    @Test
    void shouldReportInitializationNeededWhenNoProductPullTaskExists() {
        NoonProductInitializationStatusView view = service.status(307L, "STR245027", "AE");

        assertEquals("INITIALIZATION_NEEDED", view.getState());
    }

    @Test
    void shouldMapRunningSucceededAndTypedFailuresForProductManagement() {
        insertTask(1L, NoonPullTaskStatus.RUNNING, null);
        assertEquals("RUNNING", service.status(307L, "STR245027", "AE").getState());

        repository.tasks.clear();
        insertTask(5L, NoonPullTaskStatus.PARTIAL, null);
        assertEquals("LARGE_STORE_BACKFILL_IN_PROGRESS", service.status(307L, "STR245027", "AE").getState());

        repository.tasks.clear();
        insertTask(2L, NoonPullTaskStatus.SUCCEEDED, null);
        assertEquals("SUCCEEDED", service.status(307L, "STR245027", "AE").getState());

        repository.tasks.clear();
        insertTask(3L, NoonPullTaskStatus.FAILED, "auth_required");
        assertEquals("AUTH_REQUIRED", service.status(307L, "STR245027", "AE").getState());

        repository.tasks.clear();
        insertTask(4L, NoonPullTaskStatus.FAILED, "provider_unavailable");
        assertEquals("PROVIDER_UNAVAILABLE", service.status(307L, "STR245027", "AE").getState());
    }

    private void insertTask(Long id, NoonPullTaskStatus status, String failureType) {
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setId(id);
        task.setOwnerUserId(307L);
        task.setStoreCode("STR245027");
        task.setSiteCode("AE");
        task.setPullType(NoonPullType.INTERFACE);
        task.setDataDomain(NoonPullDataDomain.PRODUCT);
        task.setTriggerMode(NoonPullTriggerMode.ONBOARDING);
        task.setTargetIdentity("catalog:list");
        task.setStatus(status);
        task.setFailureType(failureType);
        repository.insertTask(task);
    }
}
