package com.nuono.next.productselection;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.productselection.Ali1688PluginCommands.AssignmentResultCommand;
import com.nuono.next.productselection.Ali1688PluginCommands.CandidateSubmissionCommand;
import com.nuono.next.productselection.Ali1688PluginCommands.CreateAssignmentCommand;
import com.nuono.next.productselection.Ali1688PluginCommands.FailureCommand;
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
@RequestMapping("/api/product-selection/ali1688-plugin/assignments")
public class Ali1688PluginAssignmentController {

    private final ObjectProvider<Ali1688PluginAssignmentService> serviceProvider;
    private final BusinessAccessResolver accessResolver;

    public Ali1688PluginAssignmentController(
            ObjectProvider<Ali1688PluginAssignmentService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.accessResolver = accessResolver;
    }

    @GetMapping
    public Ali1688PluginAssignmentListView listAssignments(HttpServletRequest request) {
        try {
            return service().listAssignments(requireAccess(request));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw forbidden(exception);
        }
    }

    @GetMapping("/{assignmentCode}")
    public Ali1688PluginAssignmentView getAssignment(
            @PathVariable String assignmentCode,
            HttpServletRequest request
    ) {
        try {
            return service().getAssignment(requireAccess(request), assignmentCode);
        } catch (ProductSelectionAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping
    public Ali1688PluginAssignmentView createAssignment(
            @RequestBody(required = false) CreateAssignmentCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().createAssignment(requireAccess(request), command);
        } catch (ProductSelectionAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{assignmentCode}/start")
    public Ali1688PluginAssignmentView startAssignment(
            @PathVariable String assignmentCode,
            HttpServletRequest request
    ) {
        try {
            return service().startAssignment(requireAccess(request), assignmentCode);
        } catch (ProductSelectionAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{assignmentCode}/fail")
    public Ali1688PluginAssignmentView failAssignment(
            @PathVariable String assignmentCode,
            @RequestBody(required = false) FailureCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().failAssignment(requireAccess(request), assignmentCode, command);
        } catch (ProductSelectionAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{assignmentCode}/submit")
    public Object submitAssignment(
            @PathVariable String assignmentCode,
            @RequestBody(required = false) CandidateSubmissionCommand command,
            HttpServletRequest request
    ) {
        try {
            if (command != null && (command.candidates == null || command.candidates.isEmpty()) && command.resultSnapshot != null) {
                AssignmentResultCommand resultCommand = new AssignmentResultCommand();
                resultCommand.assignmentType = command.assignmentType;
                resultCommand.candidateId = command.candidateId;
                resultCommand.idempotencyKey = command.idempotencyKey;
                resultCommand.resultSnapshot = command.resultSnapshot;
                resultCommand.resultStatus = command.resultStatus;
                resultCommand.sourcePageUrl = command.sourcePageUrl;
                return service().submitAssignmentResult(requireAccess(request), assignmentCode, resultCommand);
            }
            return service().submitCandidates(requireAccess(request), assignmentCode, command);
        } catch (ProductSelectionAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{assignmentCode}/submit-result")
    public Ali1688PluginAssignmentView submitAssignmentResult(
            @PathVariable String assignmentCode,
            @RequestBody(required = false) AssignmentResultCommand command,
            HttpServletRequest request
    ) {
        try {
            return service().submitAssignmentResult(requireAccess(request), assignmentCode, command);
        } catch (ProductSelectionAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private BusinessAccessContext requireAccess(HttpServletRequest request) {
        return accessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT);
    }

    private Ali1688PluginAssignmentService service() {
        Ali1688PluginAssignmentService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "1688 插件任务服务未启用。");
        }
        return service;
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    private ResponseStatusException forbidden(ProductSelectionAccessDeniedException exception) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
    }
}
