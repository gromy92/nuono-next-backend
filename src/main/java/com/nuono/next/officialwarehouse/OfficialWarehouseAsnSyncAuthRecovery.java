package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.web.ApiProblemException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

final class OfficialWarehouseAsnSyncAuthRecovery {
    private final OfficialWarehouseAppointmentAuthRecovery authRecovery;

    OfficialWarehouseAsnSyncAuthRecovery(OfficialWarehouseAppointmentAuthRecovery authRecovery) {
        this.authRecovery = authRecovery == null
                ? OfficialWarehouseAppointmentAuthRecovery.disabled()
                : authRecovery;
    }

    <T> T execute(
            Long ownerUserId,
            String projectCode,
            StoreSiteRecord site,
            String operation,
            Supplier<T> sync
    ) {
        OfficialWarehouseAppointmentAuthRecovery.AuthWait blocked =
                authRecovery.blockedWait(ownerUserId, projectCode);
        if (blocked != null) {
            throw problem(blocked, site, operation, null);
        }
        try {
            return sync.get();
        } catch (ApiProblemException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            OfficialWarehouseAppointmentAuthRecovery.AuthWait authWait =
                    authRecovery.enqueue(ownerUserId, projectCode, site.storeCode, exception);
            if (authWait != null || authRecovery.isExplicitAuthFailure(exception)) {
                throw problem(authWait, site, operation, exception);
            }
            throw exception;
        }
    }

    private ApiProblemException problem(
            OfficialWarehouseAppointmentAuthRecovery.AuthWait wait,
            StoreSiteRecord site,
            String operation,
            Throwable cause
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("storeCode", site.storeCode);
        details.put("siteCode", site.siteCode);
        if (wait != null) {
            details.put("authRecoveryStatus", "PENDING");
            details.put("retryAfterSeconds", wait.retrySeconds);
            if (wait.recoveryId != null) {
                details.put("recoveryId", wait.recoveryId);
            }
            return new ApiProblemException(
                    HttpStatus.CONFLICT,
                    "OFFICIAL_WAREHOUSE_AUTH_RECOVERY_PENDING",
                    "AUTH_REQUIRED",
                    operation,
                    "Noon 授权已失效，系统正在自动恢复。请约 1 分钟后重新执行本次 ASN 同步。",
                    true,
                    false,
                    null,
                    details,
                    cause
            );
        }
        details.put("authRecoveryStatus", "MANUAL_ACTION_REQUIRED");
        details.put("manualActionRequired", true);
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                "OFFICIAL_WAREHOUSE_AUTH_REQUIRED",
                "AUTH_REQUIRED",
                operation,
                "Noon 授权已失效，自动恢复暂未启动。请在店铺管理中恢复授权后重新执行本次 ASN 同步。",
                true,
                false,
                null,
                details,
                cause
        );
    }
}
