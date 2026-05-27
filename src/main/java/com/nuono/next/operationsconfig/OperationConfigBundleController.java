package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/operations-config/bundles")
@Deprecated
public class OperationConfigBundleController {
    private static final String COMPATIBILITY_HEADER = "X-Operations-Config-Compatibility";
    private static final String BUNDLE_LEGACY = "bundle-legacy";

    private final BusinessAccessResolver accessResolver;
    private final OperationConfigBundleService service;

    public OperationConfigBundleController(
            BusinessAccessResolver accessResolver,
            OperationConfigBundleService service
    ) {
        this.accessResolver = accessResolver;
        this.service = service;
    }

    @GetMapping("/versions")
    public List<OperationConfigBundleVersionView> versions(HttpServletRequest request, HttpServletResponse response) {
        markLegacyCompatibility(response);
        return withAccessDeniedMapping(() -> service.listVersions(resolveContext(request)));
    }

    @GetMapping("/default-versions")
    public List<OperationConfigDefaultVersionView> defaultVersions(HttpServletRequest request, HttpServletResponse response) {
        markLegacyCompatibility(response);
        return withAccessDeniedMapping(() -> service.listDefaultVersions(resolveContext(request)));
    }

    @GetMapping("/current")
    public OperationConfigCurrentBundleView current(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam("ownerUserId") Long ownerUserId,
            @RequestParam("storeCode") String storeCode,
            @RequestParam("siteCode") String siteCode
    ) {
        markLegacyCompatibility(response);
        return withAccessDeniedMapping(() -> service.resolveCurrent(
                resolveContext(request),
                ownerUserId,
                storeCode,
                siteCode
        ));
    }

    @PostMapping("/drafts")
    public OperationConfigBundleVersionView createDraft(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody OperationConfigBundleDraftRequest command
    ) {
        markLegacyCompatibility(response);
        return withAccessDeniedMapping(() -> service.createEmptyDraft(resolveContext(request), command.toCommand()));
    }

    @PutMapping("/{bundleId}/scope")
    public OperationConfigBundleVersionView updateScope(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable Long bundleId,
            @RequestBody OperationConfigBundleScopeRequest command
    ) {
        markLegacyCompatibility(response);
        return withAccessDeniedMapping(() -> service.updateScope(resolveContext(request), bundleId, command.toCommand()));
    }

    @PostMapping("/{bundleId}/publish")
    public OperationConfigBundleVersionView publish(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable Long bundleId,
            @RequestBody(required = false) OperationConfigBundlePublishRequest command
    ) {
        markLegacyCompatibility(response);
        OperationConfigBundlePublishRequest safeCommand = command == null ? new OperationConfigBundlePublishRequest() : command;
        return withAccessDeniedMapping(() -> service.publish(resolveContext(request), bundleId, safeCommand.getMessage()));
    }

    @PostMapping("/{bundleId}/copies")
    public OperationConfigBundleVersionView copyVersion(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable Long bundleId
    ) {
        markLegacyCompatibility(response);
        return withAccessDeniedMapping(() -> service.copyVersion(resolveContext(request), bundleId));
    }

    @DeleteMapping("/{bundleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVersion(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable Long bundleId
    ) {
        markLegacyCompatibility(response);
        withAccessDeniedMapping(() -> {
            service.deleteVersion(resolveContext(request), bundleId);
            return null;
        });
    }

    private void markLegacyCompatibility(HttpServletResponse response) {
        response.setHeader(COMPATIBILITY_HEADER, BUNDLE_LEGACY);
    }

    private BusinessAccessContext resolveContext(HttpServletRequest request) {
        return accessResolver.resolve(request);
    }

    private <T> T withAccessDeniedMapping(SupplierWithAccess<T> action) {
        try {
            return action.get();
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    private interface SupplierWithAccess<T> {
        T get();
    }
}
