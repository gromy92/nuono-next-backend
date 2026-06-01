package com.nuono.next.productselection;

import com.nuono.next.auth.AuthSessionTokenService;
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
@RequestMapping("/api/product-selection/ali1688-plugin")
public class Ali1688PluginExecutionAssignmentController {

    private final ObjectProvider<Ali1688PluginExecutionAssignmentService> assignmentServiceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public Ali1688PluginExecutionAssignmentController(
            ObjectProvider<Ali1688PluginExecutionAssignmentService> assignmentServiceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.assignmentServiceProvider = assignmentServiceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @GetMapping("/assignments")
    public Ali1688PluginExecutionAssignmentListView listAssignments(HttpServletRequest request) {
        return withErrors(() -> assignmentService().listAssignments(operatorUserId(request)));
    }

    @PostMapping("/assignments")
    public Ali1688PluginExecutionAssignmentView createAssignment(
            @RequestBody Ali1688PluginExecutionAssignmentCreateCommand command,
            HttpServletRequest request
    ) {
        return withErrors(() -> assignmentService().createAssignment(command, operatorUserId(request)));
    }

    @GetMapping("/assignments/{locator}")
    public Ali1688PluginExecutionAssignmentView getAssignment(
            @PathVariable String locator,
            HttpServletRequest request
    ) {
        return withErrors(() -> assignmentService().getAssignment(locator, operatorUserId(request)));
    }

    @PostMapping("/assignments/{locator}/start")
    public Ali1688PluginExecutionAssignmentView startAssignment(
            @PathVariable String locator,
            HttpServletRequest request
    ) {
        return withErrors(() -> assignmentService().startAssignment(locator, operatorUserId(request)));
    }

    @PostMapping("/assignments/{locator}/fail")
    public Ali1688PluginExecutionAssignmentView failAssignment(
            @PathVariable String locator,
            @RequestBody(required = false) Ali1688PluginExecutionAssignmentFailureCommand command,
            HttpServletRequest request
    ) {
        return withErrors(() -> assignmentService().failAssignment(locator, command, operatorUserId(request)));
    }

    @PostMapping("/assignments/{locator}/submit")
    public Ali1688PluginExecutionAssignmentView submitAssignment(
            @PathVariable String locator,
            @RequestBody(required = false) Ali1688PluginExecutionAssignmentSubmitCommand command,
            HttpServletRequest request
    ) {
        return withErrors(() -> assignmentService().submitResult(locator, command, operatorUserId(request)));
    }

    private Long operatorUserId(HttpServletRequest request) {
        return sessionTokenService.requireSession(request).getUserId();
    }

    private Ali1688PluginExecutionAssignmentService assignmentService() {
        Ali1688PluginExecutionAssignmentService service = assignmentServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "1688 插件执行任务服务未启用。");
        }
        return service;
    }

    private <T> T withErrors(SupplierWithException<T> supplier) {
        try {
            return supplier.get();
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private interface SupplierWithException<T> {
        T get();
    }
}
