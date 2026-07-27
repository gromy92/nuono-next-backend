package com.nuono.next.productlisting;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-listing")
public class ProductListingWorkflowController {

    private final ProductListingWorkflowService workflowService;
    private final ProductListingCreateOutcomeService createOutcomeService;
    private final ProductListingReauthenticationService reauthenticationService;
    private final BusinessAccessResolver businessAccessResolver;

    public ProductListingWorkflowController(
            ProductListingWorkflowService workflowService,
            ProductListingCreateOutcomeService createOutcomeService,
            ProductListingReauthenticationService reauthenticationService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.workflowService = workflowService;
        this.createOutcomeService = createOutcomeService;
        this.reauthenticationService = reauthenticationService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/drafts/{draftId}/workflow")
    public ProductListingWorkflowView workflow(
            @PathVariable Long draftId,
            HttpServletRequest request
    ) {
        try {
            return workflowService.loadWorkflow(context(request), draftId);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/tasks/{dryRunTaskId}/reopen-review")
    public ProductListingWorkflowView reopenReview(
            @PathVariable Long dryRunTaskId,
            HttpServletRequest request
    ) {
        try {
            return workflowService.reopenReview(context(request), dryRunTaskId);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/tasks/{realRunTaskId}/verify-create-outcome")
    public ProductListingCreateOutcomeVerificationView verifyCreateOutcome(
            @PathVariable Long realRunTaskId,
            HttpServletRequest request
    ) {
        try {
            return createOutcomeService.verify(context(request), realRunTaskId);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/tasks/{realRunTaskId}/confirm-not-created")
    public ProductListingWorkflowView confirmNotCreated(
            @PathVariable Long realRunTaskId,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = context(request);
            Long draftId = createOutcomeService.confirmNotCreated(
                    context,
                    realRunTaskId
            );
            return workflowService.loadWorkflow(context, draftId);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/tasks/{realRunTaskId}/reauthenticate")
    public ProductListingWorkflowView reauthenticate(
            @PathVariable Long realRunTaskId,
            HttpServletRequest request
    ) {
        try {
            return reauthenticationService.reauthenticate(
                    context(request),
                    realRunTaskId
            );
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (ProductListingReauthenticationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/tasks/{realRunTaskId}/reauthentication-status")
    public ProductListingWorkflowView reauthenticationStatus(
            @PathVariable Long realRunTaskId,
            HttpServletRequest request
    ) {
        try {
            return reauthenticationService.reauthenticationStatus(
                    context(request),
                    realRunTaskId
            );
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (ProductListingReauthenticationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private BusinessAccessContext context(HttpServletRequest request) {
        return businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.PRODUCT_LISTING
        );
    }

    private ResponseStatusException forbidden(BusinessAccessDeniedException exception) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
}
