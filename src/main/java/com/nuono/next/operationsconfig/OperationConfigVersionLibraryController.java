package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/operations-config/versions")
public class OperationConfigVersionLibraryController {
    private final BusinessAccessResolver accessResolver;
    private final OperationConfigVersionLibraryService service;

    public OperationConfigVersionLibraryController(
            BusinessAccessResolver accessResolver,
            OperationConfigVersionLibraryService service
    ) {
        this.accessResolver = accessResolver;
        this.service = service;
    }

    @GetMapping
    public List<OperationConfigVersionRowView> versions(HttpServletRequest request) {
        return withAccessDeniedMapping(() -> service.listVersions(resolveContext(request)));
    }

    @GetMapping("/{versionNo}")
    public OperationConfigVersionDetailView detail(
            HttpServletRequest request,
            @PathVariable String versionNo
    ) {
        return withAccessDeniedMapping(() -> service.getDetail(resolveContext(request), versionNo));
    }

    @GetMapping("/current")
    public OperationConfigVersionDetailView current(
            HttpServletRequest request,
            @RequestParam("configType") String configType,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "siteCode", required = false) String siteCode
    ) {
        return withAccessDeniedMapping(() -> service.currentVersion(
                resolveContext(request),
                configType,
                ownerUserId,
                storeCode,
                siteCode
        ));
    }

    @PostMapping("/{versionNo}/copies")
    public OperationConfigVersionRowView copy(
            HttpServletRequest request,
            @PathVariable String versionNo
    ) {
        return withAccessDeniedMapping(() -> service.copyVersion(resolveContext(request), versionNo));
    }

    @PostMapping("/{versionNo}/publish")
    public OperationConfigVersionDetailView publish(
            HttpServletRequest request,
            @PathVariable String versionNo,
            @RequestBody(required = false) OperationConfigVersionPublishRequest publishRequest
    ) {
        return withAccessDeniedMapping(() -> service.publishVersion(resolveContext(request), versionNo, publishRequest));
    }

    @PostMapping("/{versionNo}/disable")
    public OperationConfigVersionDetailView disable(
            HttpServletRequest request,
            @PathVariable String versionNo,
            @RequestBody(required = false) OperationConfigVersionDisableRequest disableRequest
    ) {
        return withAccessDeniedMapping(() -> service.disableVersion(resolveContext(request), versionNo, disableRequest));
    }

    @PutMapping("/{versionNo}")
    public OperationConfigVersionDetailView update(
            HttpServletRequest request,
            @PathVariable String versionNo,
            @RequestBody OperationConfigVersionUpdateRequest updateRequest
    ) {
        return withAccessDeniedMapping(() -> service.updateVersion(resolveContext(request), versionNo, updateRequest));
    }

    @DeleteMapping("/{versionNo}")
    public ResponseEntity<Void> delete(
            HttpServletRequest request,
            @PathVariable String versionNo
    ) {
        withAccessDeniedMapping(() -> {
            service.deleteVersion(resolveContext(request), versionNo);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    private BusinessAccessContext resolveContext(HttpServletRequest request) {
        return accessResolver.resolve(request);
    }

    private <T> T withAccessDeniedMapping(SupplierWithAccess<T> action) {
        try {
            return action.get();
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private interface SupplierWithAccess<T> {
        T get();
    }
}
