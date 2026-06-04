package com.nuono.next.productlisting;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
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
@RequestMapping("/api/product-listing")
public class ProductListingController {

    private final ProductListingService service;
    private final BusinessAccessResolver businessAccessResolver;

    public ProductListingController(
            ProductListingService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @PostMapping("/drafts")
    public ProductListingDraftView saveDraft(
            @RequestBody ProductListingDraftCommand command,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                    request,
                    BusinessCapability.PRODUCT_LISTING,
                    command == null ? null : command.getStoreCode()
            );
            return service.saveDraft(context, command);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/drafts/{draftId}/validate")
    public ProductListingDraftView validateDraft(
            @PathVariable Long draftId,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                    request,
                    BusinessCapability.PRODUCT_LISTING
            );
            return service.validateDraft(context, draftId);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/dry-run")
    public ProductListingTaskView submitDryRun(
            @RequestBody ProductListingDryRunSubmitCommand command,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                    request,
                    BusinessCapability.PRODUCT_LISTING,
                    command == null ? null : command.getStoreCode()
            );
            return service.submitDryRun(context, command);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/tasks/{taskId}")
    public ProductListingTaskView task(
            @PathVariable Long taskId,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                    request,
                    BusinessCapability.PRODUCT_LISTING
            );
            return service.loadTask(context, taskId);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/tasks/{dryRunTaskId}/confirm-real-run")
    public ProductListingTaskView confirmRealRun(
            @PathVariable Long dryRunTaskId,
            @RequestBody(required = false) ProductListingRealRunCommand command,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                    request,
                    BusinessCapability.PRODUCT_LISTING
            );
            return service.confirmRealRun(context, dryRunTaskId, command);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/tasks/recent")
    public List<ProductListingTaskView> recentTasks(
            @RequestParam String storeCode,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                    request,
                    BusinessCapability.PRODUCT_LISTING,
                    storeCode
            );
            return service.recentTasks(context, storeCode, limit);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private ResponseStatusException forbidden(BusinessAccessDeniedException exception) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
}
