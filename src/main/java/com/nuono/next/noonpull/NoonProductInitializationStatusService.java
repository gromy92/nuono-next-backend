package com.nuono.next.noonpull;

import java.util.Comparator;
import org.springframework.stereotype.Service;

@Service
public class NoonProductInitializationStatusService {
    private final NoonPullRepository repository;

    public NoonProductInitializationStatusService(NoonPullRepository repository) {
        this.repository = repository;
    }

    public NoonProductInitializationStatusView status(Long ownerUserId, String storeCode, String siteCode) {
        NoonPullTaskRecord latest = repository.listTasks().stream()
                .filter((task) -> ownerUserId != null && ownerUserId.equals(task.getOwnerUserId()))
                .filter((task) -> equalsNormalized(storeCode, task.getStoreCode()))
                .filter((task) -> equalsNormalized(siteCode, task.getSiteCode()))
                .filter((task) -> task.getPullType() == NoonPullType.INTERFACE)
                .filter((task) -> task.getDataDomain() == NoonPullDataDomain.PRODUCT)
                .max(Comparator.comparing(NoonPullTaskRecord::getId))
                .orElse(null);
        NoonProductInitializationStatusView view = new NoonProductInitializationStatusView();
        if (latest == null) {
            view.setState("INITIALIZATION_NEEDED");
            return view;
        }
        view.setTaskStatus(latest.getStatus() == null ? null : latest.getStatus().name());
        view.setFailureType(latest.getFailureType());
        view.setRetryAction(latest.getRetryAction());
        view.setRetryable(latest.getRetryable());
        view.setRequiresManualAction(latest.getRequiresManualAction());
        view.setSourceBatchId(latest.getSourceBatchId());
        view.setDiagnosticSummary(latest.getDiagnosticSummary());
        view.setState(toState(latest));
        return view;
    }

    private String toState(NoonPullTaskRecord task) {
        if (task.getStatus() == NoonPullTaskStatus.QUEUED || task.getStatus() == NoonPullTaskStatus.RUNNING) {
            return "RUNNING";
        }
        if (task.getStatus() == NoonPullTaskStatus.PARTIAL) {
            return "LARGE_STORE_BACKFILL_IN_PROGRESS";
        }
        if (task.getStatus() == NoonPullTaskStatus.SUCCEEDED) {
            return "SUCCEEDED";
        }
        if ("auth_required".equals(task.getFailureType())) {
            return "AUTH_REQUIRED";
        }
        if ("provider_unavailable".equals(task.getFailureType())) {
            return "PROVIDER_UNAVAILABLE";
        }
        return "FAILED";
    }

    private boolean equalsNormalized(String expected, String actual) {
        if (expected == null) {
            return actual == null;
        }
        return expected.trim().equalsIgnoreCase(actual == null ? null : actual.trim());
    }
}
