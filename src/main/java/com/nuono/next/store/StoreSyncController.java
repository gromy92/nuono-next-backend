package com.nuono.next.store;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static com.nuono.next.auth.RoleAccessSupport.isOperatorRoleView;
import static com.nuono.next.auth.RoleAccessSupport.isSystemAdmin;

@RestController
@RequestMapping("/api/store-sync")
public class StoreSyncController {

    private final ObjectProvider<LocalDbStoreSyncService> localDbStoreSyncServiceProvider;
    private final ObjectProvider<LocalDbStoreInitializationService> localDbStoreInitializationServiceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public StoreSyncController(
            ObjectProvider<LocalDbStoreSyncService> localDbStoreSyncServiceProvider,
            ObjectProvider<LocalDbStoreInitializationService> localDbStoreInitializationServiceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.localDbStoreSyncServiceProvider = localDbStoreSyncServiceProvider;
        this.localDbStoreInitializationServiceProvider = localDbStoreInitializationServiceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @GetMapping("/overview")
    public StoreSyncOverview overview(
            @RequestParam(required = false) Long ownerUserId,
            HttpServletRequest request
    ) {
        AuthenticatedSession session = requireSession(request);
        Long resolvedOwnerUserId = resolveOwnerUserId(session, ownerUserId);
        LocalDbStoreSyncService storeSyncService = localDbStoreSyncServiceProvider.getIfAvailable();
        if (storeSyncService != null) {
            StoreSyncOverview overview = storeSyncService.buildOverview(resolvedOwnerUserId);
            if (!isSystemAdmin(session)) {
                overview.setOwnerOptions(overview.getOwnerOptions().stream()
                        .filter(owner -> session.getUserId().equals(owner.getId()))
                        .collect(Collectors.toList()));
            }
            return overview;
        }

        StoreSyncOverview overview = new StoreSyncOverview();
        overview.setMode("bootstrap-only");
        overview.setReady(false);
        overview.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取店铺管理视图。");
        overview.setSyncedRules(List.of(
                "店铺管理按当前登录账号自己的 user_store 范围读取。",
                "老板可绑定 Noon 商家后台登录邮箱和密码、测试连通和绑定新店铺，非老板只读查看。",
                "店铺负责人继续按当前店铺编码聚合，避免协同成员被拆散。",
                "后续补分配/重分配写操作时，必须保留既有 isAuthorized，不能把 Noon 绑定状态覆盖掉。"
        ));
        return overview;
    }

    @PostMapping("/bind")
    public Map<String, Object> bind(
            @RequestBody StoreBindCommand command,
            HttpServletRequest request
    ) {
        AuthenticatedSession session = requireStoreManagementSession(request);
        LocalDbStoreSyncService storeSyncService = localDbStoreSyncServiceProvider.getIfAvailable();
        if (storeSyncService == null) {
            throw new IllegalStateException("当前仍在无数据库骨架模式，不能写入店铺绑定。");
        }

        try {
            if (command != null) {
                command.setOwnerUserId(resolveOwnerUserId(session, command.getOwnerUserId()));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            StoreBindingResult result = storeSyncService.bindStore(command);
            payload.put("success", result.isSuccess());
            payload.put("message", result.getMessage());
            if (!result.getProjectList().isEmpty()) {
                payload.put("projectList", result.getProjectList());
            }
            return payload;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/create-store")
    public Map<String, Object> createStore(
            @RequestBody StoreCreateCommand command,
            HttpServletRequest request
    ) {
        AuthenticatedSession session = requireStoreManagementSession(request);
        LocalDbStoreSyncService storeSyncService = localDbStoreSyncServiceProvider.getIfAvailable();
        if (storeSyncService == null) {
            throw new IllegalStateException("当前仍在无数据库骨架模式，不能绑定新店铺。");
        }

        try {
            if (command != null) {
                command.setOwnerUserId(resolveOwnerUserId(session, command.getOwnerUserId()));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            StoreBindingResult result = storeSyncService.createStore(command);
            payload.put("success", result.isSuccess());
            payload.put("message", result.getMessage());
            if (!result.getProjectList().isEmpty()) {
                payload.put("projectList", result.getProjectList());
            }
            return payload;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/test-connection")
    public Map<String, Object> testConnection(
            @RequestParam Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        AuthenticatedSession session = requireStoreManagementSession(request);
        LocalDbStoreSyncService storeSyncService = localDbStoreSyncServiceProvider.getIfAvailable();
        if (storeSyncService == null) {
            throw new IllegalStateException("当前仍在无数据库骨架模式，不能测试店铺连通。");
        }

        try {
            LocalDbStoreSyncService.StoreConnectionTestResult result =
                    storeSyncService.testConnection(resolveOwnerUserId(session, ownerUserId), storeCode);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("connected", result.isConnected());
            payload.put("message", result.getMessage());
            payload.put("checkMode", result.getCheckMode());
            payload.put("noonRequestCounts", result.getNoonRequestCounts());
            payload.put("noonRequestTotalCount", result.getNoonRequestTotalCount());
            return payload;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/init-status")
    public LocalDbStoreInitializationService.StoreInitializationStatusView initStatus(
            @RequestParam Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        AuthenticatedSession session = requireSession(request);
        LocalDbStoreInitializationService initializationService = localDbStoreInitializationServiceProvider.getIfAvailable();
        if (initializationService == null) {
            LocalDbStoreInitializationService.StoreInitializationStatusView view =
                    new LocalDbStoreInitializationService.StoreInitializationStatusView();
            view.setMode("bootstrap-only");
            view.setReady(false);
            view.setStatus("BLOCKED");
            view.setMessage("当前仍在无数据库骨架模式，不能执行新店初始化。");
            return view;
        }

        try {
            return initializationService.getStatus(resolveOwnerUserId(session, ownerUserId), storeCode);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/init-start")
    public LocalDbStoreInitializationService.StoreInitializationStatusView initStart(
            @RequestBody LocalDbStoreInitializationService.StoreInitializationCommand command,
            HttpServletRequest request
    ) {
        AuthenticatedSession session = requireSession(request);
        LocalDbStoreInitializationService initializationService = localDbStoreInitializationServiceProvider.getIfAvailable();
        if (initializationService == null) {
            throw new IllegalStateException("当前仍在无数据库骨架模式，不能启动新店初始化。");
        }

        try {
            if (command != null) {
                command.setOwnerUserId(resolveOwnerUserId(session, command.getOwnerUserId()));
            }
            return initializationService.start(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/init-preflight")
    public LocalDbStoreInitializationService.StoreInitializationPreflightView initPreflight(
            @RequestBody LocalDbStoreInitializationService.StoreInitializationCommand command,
            HttpServletRequest request
    ) {
        AuthenticatedSession session = requireSession(request);
        LocalDbStoreInitializationService initializationService = localDbStoreInitializationServiceProvider.getIfAvailable();
        if (initializationService == null) {
            throw new IllegalStateException("当前仍在无数据库骨架模式，不能执行新店初始化预检。");
        }

        try {
            if (command != null) {
                command.setOwnerUserId(resolveOwnerUserId(session, command.getOwnerUserId()));
            }
            return initializationService.preflight(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    private AuthenticatedSession requireSession(HttpServletRequest request) {
        return sessionTokenService.requireSession(request);
    }

    private AuthenticatedSession requireStoreManagementSession(HttpServletRequest request) {
        AuthenticatedSession session = requireSession(request);
        if (isOperatorRoleView(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前为运营视角，不能执行老板管理操作。");
        }
        return session;
    }

    private Long resolveOwnerUserId(AuthenticatedSession session, Long requestedOwnerUserId) {
        Long resolvedOwnerUserId = requestedOwnerUserId != null ? requestedOwnerUserId : session.getUserId();
        if (isSystemAdmin(session) || session.getUserId().equals(resolvedOwnerUserId)) {
            return resolvedOwnerUserId;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作其他负责人的店铺。");
    }
}
