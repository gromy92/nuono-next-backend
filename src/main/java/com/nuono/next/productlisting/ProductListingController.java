package com.nuono.next.productlisting;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<ProductListingAiListingService> aiListingServiceProvider;

    public ProductListingController(
            ProductListingService service,
            BusinessAccessResolver businessAccessResolver,
            ObjectProvider<ProductListingAiListingService> aiListingServiceProvider
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
        this.aiListingServiceProvider = aiListingServiceProvider;
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

    @GetMapping("/drafts")
    public List<ProductListingDraftView> drafts(
            @RequestParam String storeCode,
            @RequestParam(defaultValue = "30") int limit,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                    request,
                    BusinessCapability.PRODUCT_LISTING,
                    storeCode
            );
            return service.listDrafts(context, storeCode, limit);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/drafts/{draftId}")
    public ProductListingDraftView draft(
            @PathVariable Long draftId,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                    request,
                    BusinessCapability.PRODUCT_LISTING
            );
            return service.loadDraft(context, draftId);
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

    @PostMapping("/field-validation")
    public ProductListingFieldValidationView validateFields(
            @RequestBody(required = false) ProductListingDraftCommand command,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                    request,
                    BusinessCapability.PRODUCT_LISTING,
                    command == null ? null : command.getStoreCode()
            );
            return service.validateFields(context, command);
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

    @PostMapping("/ai/noon-listing")
    public ProductListingAiListingView generateNoonListing(
            @RequestBody(required = false) ProductListingAiListingCommand command,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                    request,
                    BusinessCapability.PRODUCT_LISTING,
                    command == null || command.getDraft() == null ? null : command.getDraft().getStoreCode()
            );
            ProductListingAiListingService aiListingService = aiListingServiceProvider.getIfAvailable();
            if (aiListingService == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品上架 AI 暂时不可用。");
            }
            return aiListingService.generate(context, command);
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

    @PostMapping("/tasks/{realRunTaskId}/verify-readback")
    public ProductListingTaskView verifyRealRunReadBack(
            @PathVariable Long realRunTaskId,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                    request,
                    BusinessCapability.PRODUCT_LISTING
            );
            return service.verifyRealRunReadBack(context, realRunTaskId);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/tasks/{realRunTaskId}/continue-after-create")
    public ProductListingTaskView continueRealRunAfterCreate(
            @PathVariable Long realRunTaskId,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                    request,
                    BusinessCapability.PRODUCT_LISTING
            );
            return service.continueRealRunAfterCreate(context, realRunTaskId);
        } catch (BusinessAccessDeniedException exception) {
            throw forbidden(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/tasks/{realRunTaskId}/replay-projection")
    public ProductListingTaskView replayRealRunProjectionBackfill(
            @PathVariable Long realRunTaskId,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                    request,
                    BusinessCapability.PRODUCT_LISTING
            );
            return service.replaySuccessfulProjectionBackfill(context, realRunTaskId);
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
