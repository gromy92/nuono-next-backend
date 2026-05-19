package com.nuono.next.mobile;

import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/mobile")
public class MobileWorkbenchController {

    private final ObjectProvider<LocalDbMobileWorkbenchService> mobileWorkbenchServiceProvider;

    public MobileWorkbenchController(ObjectProvider<LocalDbMobileWorkbenchService> mobileWorkbenchServiceProvider) {
        this.mobileWorkbenchServiceProvider = mobileWorkbenchServiceProvider;
    }

    @GetMapping("/dashboard/overview")
    public MobileApiResponse<MobileDashboardOverviewView> dashboardOverview(
            @RequestParam(required = false) String storeCode,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "token", required = false) String token
    ) {
        return execute(service -> service.dashboardOverview(resolveToken(authorization, token), storeCode));
    }

    @GetMapping("/dashboard/alerts")
    public MobileApiResponse<MobileDashboardAlertsView> dashboardAlerts(
            @RequestParam(required = false) String storeCode,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "token", required = false) String token
    ) {
        return execute(service -> service.dashboardAlerts(resolveToken(authorization, token), storeCode));
    }

    @GetMapping("/message/list")
    public MobileApiResponse<MobilePageResponse<MobileMessageView>> messageList(
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "token", required = false) String token
    ) {
        return execute(service -> service.messageList(
                resolveToken(authorization, token),
                storeCode,
                type,
                currentPage,
                pageSize
        ));
    }

    @GetMapping("/task/list")
    public MobileApiResponse<MobilePageResponse<MobileTaskView>> taskList(
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "token", required = false) String token
    ) {
        return execute(service -> service.taskList(
                resolveToken(authorization, token),
                storeCode,
                status,
                taskType,
                currentPage,
                pageSize
        ));
    }

    private <T> MobileApiResponse<T> execute(Function<LocalDbMobileWorkbenchService, T> action) {
        LocalDbMobileWorkbenchService workbenchService = mobileWorkbenchServiceProvider.getIfAvailable();
        if (workbenchService == null) {
            return MobileApiResponse.failure(503, "当前仍在无数据库骨架模式，不能读取移动端工作台。");
        }
        try {
            return MobileApiResponse.success(action.apply(workbenchService));
        } catch (MobileApiException exception) {
            return MobileApiResponse.failure(exception.getCode(), exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return MobileApiResponse.failure(400, exception.getMessage());
        }
    }

    private String resolveToken(String authorization, String token) {
        if (StringUtils.hasText(authorization)) {
            String trimmed = authorization.trim();
            if (trimmed.toLowerCase().startsWith("bearer ")) {
                return trimmed.substring(7).trim();
            }
            return trimmed;
        }
        return token;
    }
}
