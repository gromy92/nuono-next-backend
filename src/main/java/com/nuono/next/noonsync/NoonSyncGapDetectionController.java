package com.nuono.next.noonsync;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/noon-sync-readiness")
@Deprecated // 未接入：前端 0 引用。保留待删除（#9）
public class NoonSyncGapDetectionController {

    private final NoonSyncGapDetectionService gapDetectionService;
    private final BusinessAccessResolver businessAccessResolver;

    public NoonSyncGapDetectionController(
            NoonSyncGapDetectionService gapDetectionService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.gapDetectionService = gapDetectionService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @PostMapping("/preview")
    public NoonSyncReadinessView preview(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestBody NoonSyncGapDetectionPreviewRequest body,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                storeCode
        );
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        NoonSyncScope scope = NoonSyncScope.of(ownerUserId, body.getLogicalStoreId(), storeCode, siteCode);
        return gapDetectionService.preview(new NoonSyncGapDetectionInput(
                defaultValue(body.getAccountOrigin(), NoonSyncAccountOrigin.EXISTING),
                scope,
                Boolean.TRUE.equals(body.getNoonBindingReady()),
                Boolean.TRUE.equals(body.getProviderConfigured()),
                defaultValue(body.getProductWorkspaceState(), NoonProductWorkspaceState.READY),
                defaultValue(body.getSalesCoverageState(), NoonSalesCoverageState.COMPLETE),
                body.getSalesBackfillFrom(),
                body.getSalesBackfillTo()
        ));
    }

    private <T> T defaultValue(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
