package com.nuono.next.noonsync;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import java.time.LocalDate;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/noon-sync-admin")
@Deprecated // 未接入：前端 0 引用。保留待删除（#9）
public class NoonSyncAdminDiagnosticsController {

    private final NoonSyncAdminDiagnosticsService service;
    private final BusinessAccessResolver businessAccessResolver;

    public NoonSyncAdminDiagnosticsController(
            NoonSyncAdminDiagnosticsService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/diagnostics")
    public NoonSyncAdminDiagnosticsView diagnostics(
            @RequestParam(required = false) NoonSyncDataDomain dataDomain,
            @RequestParam(required = false) NoonSyncTaskStatus status,
            @RequestParam(required = false) NoonSyncTriggerMode triggerMode,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            HttpServletRequest request
    ) {
        return execute(request, context -> service.overview(context, new NoonSyncAdminDiagnosticFilter(
                dataDomain,
                status,
                triggerMode,
                parseDate(dateFrom),
                parseDate(dateTo)
        )));
    }

    @GetMapping("/diagnostics/tasks/{taskId}")
    public NoonSyncAdminTaskSummary taskDetail(
            @PathVariable long taskId,
            @RequestParam Long ownerUserId,
            HttpServletRequest request
    ) {
        return execute(request, context -> service.taskDetail(context, taskId, ownerUserId));
    }

    @PostMapping("/plans/{planId}/pause")
    public NoonSyncPlan pausePlan(
            @PathVariable long planId,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        String reason = body == null ? null : body.get("reason");
        return execute(request, context -> service.pausePlan(context, planId, reason));
    }

    @PostMapping("/plans/{planId}/resume")
    public NoonSyncPlan resumePlan(@PathVariable long planId, HttpServletRequest request) {
        return execute(request, context -> service.resumePlan(context, planId));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public NoonSyncTask retryTask(@PathVariable long taskId, HttpServletRequest request) {
        return execute(request, context -> service.retryTask(context, taskId));
    }

    private <T> T execute(HttpServletRequest request, DiagnosticAction<T> action) {
        BusinessAccessContext context = businessAccessResolver.resolve(request);
        try {
            return action.run(context);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private interface DiagnosticAction<T> {
        T run(BusinessAccessContext context);
    }
}
