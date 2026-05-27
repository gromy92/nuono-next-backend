package com.nuono.next.noonpull;

import com.nuono.next.auth.RoleAccessSupport;
import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/noon-pull")
public class NoonPullDiagnosticsController {
    private final ObjectProvider<NoonPullFoundationService> serviceProvider;
    private final AuthSessionTokenService sessionTokenService;
    private final ObjectProvider<NoonPullSmokeRunRepository> smokeRunRepositoryProvider;

    public NoonPullDiagnosticsController(
            ObjectProvider<NoonPullFoundationService> serviceProvider,
            AuthSessionTokenService sessionTokenService,
            ObjectProvider<NoonPullSmokeRunRepository> smokeRunRepositoryProvider
    ) {
        this.serviceProvider = serviceProvider;
        this.sessionTokenService = sessionTokenService;
        this.smokeRunRepositoryProvider = smokeRunRepositoryProvider;
    }

    @GetMapping("/diagnostics")
    public NoonPullDiagnosticsView overview(HttpServletRequest request) {
        NoonPullFoundationService service = requireService();
        requireSystemAdmin(request);
        NoonPullDiagnosticsView view = new NoonPullDiagnosticsView();
        view.setPlans(service.listPlans().stream().map(this::planView).collect(Collectors.toList()));
        view.setTasks(service.listTasks().stream().map(this::taskView).collect(Collectors.toList()));
        NoonPullSmokeRunRepository smokeRunRepository = smokeRunRepositoryProvider.getIfAvailable();
        if (smokeRunRepository != null) {
            view.setSmokeRuns(smokeRunRepository.listRecent(20).stream()
                    .map(this::smokeRunView)
                    .collect(Collectors.toList()));
        }
        return view;
    }

    @PostMapping("/plans/{planId}/pause")
    public NoonPullDiagnosticsView.PlanView pausePlan(
            @PathVariable Long planId,
            @RequestBody(required = false) NoonPullPauseCommand command,
            HttpServletRequest request
    ) {
        NoonPullFoundationService service = requireService();
        requireSystemAdmin(request);
        String reason = command == null ? null : command.getReason();
        return planView(service.pausePlan(planId, reason));
    }

    @PostMapping("/plans/{planId}/resume")
    public NoonPullDiagnosticsView.PlanView resumePlan(@PathVariable Long planId, HttpServletRequest request) {
        NoonPullFoundationService service = requireService();
        requireSystemAdmin(request);
        return planView(service.resumePlan(planId));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public NoonPullDiagnosticsView.TaskView retryTask(@PathVariable Long taskId, HttpServletRequest request) {
        NoonPullFoundationService service = requireService();
        requireSystemAdmin(request);
        try {
            return taskView(service.retryTask(taskId));
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    private NoonPullFoundationService requireService() {
        NoonPullFoundationService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon pull foundation is not available.");
        }
        return service;
    }

    private void requireSystemAdmin(HttpServletRequest request) {
        AuthenticatedSession session = sessionTokenService.requireSession(request);
        if (!RoleAccessSupport.isSystemAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Noon pull diagnostics require system administrator access.");
        }
    }

    private NoonPullDiagnosticsView.PlanView planView(NoonPullPlanRecord record) {
        NoonPullDiagnosticsView.PlanView view = new NoonPullDiagnosticsView.PlanView();
        view.setId(record.getId());
        view.setOwnerUserId(record.getOwnerUserId());
        view.setStoreCode(record.getStoreCode());
        view.setSiteCode(record.getSiteCode());
        view.setPullType(enumName(record.getPullType()));
        view.setDataDomain(enumName(record.getDataDomain()));
        view.setTriggerMode(enumName(record.getTriggerMode()));
        view.setEnabled(record.isEnabled());
        view.setPaused(record.isPaused());
        view.setPauseReason(record.getPauseReason());
        view.setLatestSuccessAt(record.getLatestSuccessAt());
        view.setLatestFailureAt(record.getLatestFailureAt());
        view.setLatestFailureType(record.getLatestFailureType());
        view.setNextRetryAt(record.getNextRetryAt());
        return view;
    }

    private NoonPullDiagnosticsView.TaskView taskView(NoonPullTaskRecord record) {
        NoonPullDiagnosticsView.TaskView view = new NoonPullDiagnosticsView.TaskView();
        view.setId(record.getId());
        view.setPlanId(record.getPlanId());
        view.setOwnerUserId(record.getOwnerUserId());
        view.setStoreCode(record.getStoreCode());
        view.setSiteCode(record.getSiteCode());
        view.setPullType(enumName(record.getPullType()));
        view.setDataDomain(enumName(record.getDataDomain()));
        view.setTriggerMode(enumName(record.getTriggerMode()));
        view.setTargetIdentity(record.getTargetIdentity());
        view.setStatus(enumName(record.getStatus()));
        view.setSourceBatchId(record.getSourceBatchId());
        view.setFailureType(record.getFailureType());
        view.setRetryAction(record.getRetryAction());
        view.setRetryable(record.getRetryable());
        view.setRequiresManualAction(record.getRequiresManualAction());
        view.setDiagnosticSummary(NoonPullSafeText.redact(record.getDiagnosticSummary()));
        view.setLockedBy(record.getLockedBy());
        view.setQueuedAt(record.getQueuedAt());
        view.setStartedAt(record.getStartedAt());
        view.setFinishedAt(record.getFinishedAt());
        return view;
    }

    private NoonPullDiagnosticsView.SmokeRunView smokeRunView(NoonPullSmokeRunRecord record) {
        NoonPullDiagnosticsView.SmokeRunView view = new NoonPullDiagnosticsView.SmokeRunView();
        view.setId(record.getId());
        view.setTargetEnvironment(record.getTargetEnvironment());
        view.setOwnerUserId(record.getOwnerUserId());
        view.setProjectCode(record.getProjectCode());
        view.setProjectName(record.getProjectName());
        view.setStoreCode(record.getStoreCode());
        view.setSiteCode(record.getSiteCode());
        view.setRollbackOrGlobalPauseStrategy(NoonPullSafeText.redact(record.getRollbackOrGlobalPauseStrategy()));
        view.setRequestedDataDomains(record.requestedDataDomains());
        view.setMissingRequirements(record.getMissingRequirements());
        view.setEvidenceGateSatisfied(record.isEvidenceGateSatisfied());
        view.setProductionSchedulingAllowed(record.isProductionSchedulingAllowed());
        view.setCreatedAt(record.getCreatedAt());
        view.setUpdatedAt(record.getUpdatedAt());
        view.setEvidence(record.getEvidence().stream().map(this::smokeEvidenceView).collect(Collectors.toList()));
        return view;
    }

    private NoonPullSmokeEvidenceView smokeEvidenceView(NoonPullSmokeEvidenceRecord record) {
        NoonPullSmokeEvidenceView view = new NoonPullSmokeEvidenceView();
        view.setDataDomain(record.getDataDomain());
        view.setTargetIdentity(record.getTargetIdentity());
        view.setDateFrom(record.getDateFrom());
        view.setDateTo(record.getDateTo());
        view.setRowOrItemCount(record.getRowOrItemCount());
        view.setTaskId(record.getTaskId());
        view.setSourceBatchId(record.getSourceBatchId());
        view.setFileDigestSha256(record.getFileDigestSha256());
        view.setRequestCount(record.getRequestCount());
        view.setElapsedMillis(record.getElapsedMillis() == null ? 0L : record.getElapsedMillis());
        view.setLatestFactDate(record.getLatestFactDate());
        view.setStatus(record.getStatus());
        view.setQualityState(record.getQualityState());
        view.setFailureClassification(record.getFailureClassification());
        return view;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

}
