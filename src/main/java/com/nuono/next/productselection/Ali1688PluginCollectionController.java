package com.nuono.next.productselection;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-selection")
public class Ali1688PluginCollectionController {

    private final ObjectProvider<Ali1688PluginCollectionService> pluginCollectionServiceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public Ali1688PluginCollectionController(
            ObjectProvider<Ali1688PluginCollectionService> pluginCollectionServiceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.pluginCollectionServiceProvider = pluginCollectionServiceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @PostMapping("/ali1688-collections/{taskId}/plugin-assignment")
    public Ali1688PluginAssignmentView createAssignment(
            @PathVariable String taskId,
            @RequestBody(required = false) Ali1688PluginAssignmentCreateCommand command,
            HttpServletRequest request
    ) {
        AuthenticatedSession session = sessionTokenService.requireSession(request);
        if (command != null && command.hasIdentityFields()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "插件采集任务归属由后端会话和 1688 任务推导，不能由前端传入。"
            );
        }
        try {
            return service().createAssignment(taskId, session.getUserId());
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/ali1688-plugin/assignments/{assignmentCode}")
    public Ali1688PluginAssignmentView getAssignment(
            @PathVariable String assignmentCode,
            HttpServletRequest request
    ) {
        return withPluginSession(request, session -> service().getAssignment(assignmentCode, session));
    }

    @GetMapping("/ali1688-plugin/assignments")
    public Ali1688PluginAssignmentListView listAssignments(HttpServletRequest request) {
        return withPluginSession(request, session -> service().listCurrentAssignments(session));
    }

    @PostMapping("/ali1688-plugin/assignments/{assignmentCode}/start")
    public Ali1688PluginAssignmentView startAssignment(
            @PathVariable String assignmentCode,
            HttpServletRequest request
    ) {
        return withPluginSession(request, session -> service().startAssignment(assignmentCode, session));
    }

    @PostMapping("/ali1688-plugin/assignments/{assignmentCode}/fail")
    public Ali1688PluginAssignmentView failAssignment(
            @PathVariable String assignmentCode,
            @RequestBody(required = false) Ali1688PluginAssignmentFailureCommand command,
            HttpServletRequest request
    ) {
        return withPluginSession(request, session -> service().failAssignment(assignmentCode, session, command));
    }

    @PostMapping("/ali1688-plugin/assignments/{assignmentCode}/submit")
    public Ali1688PluginSubmissionView submitCandidates(
            @PathVariable String assignmentCode,
            @RequestBody(required = false) Ali1688PluginSubmissionCommand command,
            HttpServletRequest request
    ) {
        return withPluginSession(request, session -> service().submitCandidates(assignmentCode, session, command));
    }

    @PostMapping("/ali1688-plugin/assignments/{assignmentCode}/cancel")
    public Ali1688PluginAssignmentView cancelAssignment(
            @PathVariable String assignmentCode,
            HttpServletRequest request
    ) {
        return withPluginSession(request, session -> service().cancelAssignment(assignmentCode, session.getUserId()));
    }

    private <T> T withPluginSession(
            HttpServletRequest request,
            PluginSessionAction<T> action
    ) {
        requireBearerSession(request);
        AuthenticatedSession session = sessionTokenService.requireSession(request);
        try {
            return action.apply(session);
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (Ali1688PluginAssignmentException exception) {
            throw assignmentStatus(exception);
        }
    }

    private ResponseStatusException assignmentStatus(Ali1688PluginAssignmentException exception) {
        HttpStatus status = "assignment_not_found".equals(exception.getErrorCode())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return new ResponseStatusException(status, exception.getMessage(), exception);
    }

    private Ali1688PluginCollectionService service() {
        Ali1688PluginCollectionService service = pluginCollectionServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "1688 插件采集任务服务未启用。");
        }
        return service;
    }

    private void requireBearerSession(HttpServletRequest request) {
        String authorization = request == null ? null : request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith("Bearer ")
                || !StringUtils.hasText(authorization.substring("Bearer ".length()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "插件接口需要 Bearer 登录态。");
        }
    }

    private interface PluginSessionAction<T> {
        T apply(AuthenticatedSession session);
    }
}
