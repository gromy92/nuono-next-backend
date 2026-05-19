package com.nuono.next.masterdata;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.foundation.FoundationUserDetail;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ResponseStatusException;

import static com.nuono.next.auth.RoleAccessSupport.isOperatorRoleView;

@RestController
@RequestMapping("/api/master-data")
public class MasterDataController {

    private final ObjectProvider<LocalDbMasterDataService> masterDataServiceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public MasterDataController(
            ObjectProvider<LocalDbMasterDataService> masterDataServiceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.masterDataServiceProvider = masterDataServiceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @GetMapping("/users")
    public List<MasterDataUserView> users(
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            @RequestParam(value = "operatorRoleLevel", required = false) Integer operatorRoleLevel,
            @RequestParam(value = "view", required = false) String view,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        AuthenticatedSession session = requireActiveSession(request, service);
        return service.listUsers(session.getUserId(), service.getOperatorRoleLevel(session.getUserId()), view);
    }

    @GetMapping("/org-tree")
    public List<MasterDataOrgNodeView> orgTree(
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            @RequestParam(value = "operatorRoleLevel", required = false) Integer operatorRoleLevel,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        AuthenticatedSession session = requireActiveSession(request, service);
        return service.listOrgTree(session.getUserId(), service.getOperatorRoleLevel(session.getUserId()));
    }

    @GetMapping("/user-detail")
    public FoundationUserDetail userDetail(
            @RequestParam("userId") Long userId,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            return service.getUserDetail(userId, requireSession(request).getUserId());
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }

    @GetMapping("/roles")
    public List<MasterDataRoleView> roles(HttpServletRequest request) {
        LocalDbMasterDataService service = requireService();
        requireActiveSession(request, service);
        return service.listRoles();
    }

    @GetMapping("/menus")
    public List<MasterDataMenuView> menus(HttpServletRequest request) {
        LocalDbMasterDataService service = requireService();
        requireActiveSession(request, service);
        return service.listMenus();
    }

    @PostMapping("/assign-role")
    public Map<String, Object> assignRole(
            @RequestBody MasterDataAssignRoleCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.assignRole(command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (DataAccessException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责店铺授权数据冲突，请刷新后重试。", error);
        }
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(
            @RequestBody MasterDataSaveUserCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Long userId = service.createUser(command);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("userId", userId);
            payload.put("message", "账号已创建。");
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (DataAccessException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责店铺授权数据冲突，请刷新后重试。", error);
        }
    }

    @PutMapping("/users/{userId}")
    public Map<String, Object> updateUser(
            @PathVariable("userId") Long userId,
            @RequestBody MasterDataSaveUserCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.updateUser(userId, command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PostMapping("/users/{userId}/toggle-status")
    public Map<String, Object> toggleUserStatus(
            @PathVariable("userId") Long userId,
            @RequestBody MasterDataToggleUserStatusCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.toggleUserStatus(userId, command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PostMapping("/users/{userId}/reset-password")
    public Map<String, Object> resetUserPassword(
            @PathVariable("userId") Long userId,
            @RequestBody(required = false) MasterDataResetPasswordCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            command = attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.resetUserPassword(userId, command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PostMapping("/users/{userId}/assign-stores")
    public Map<String, Object> assignStores(
            @PathVariable("userId") Long userId,
            @RequestBody(required = false) MasterDataAssignStoresCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            command = attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.assignStores(userId, command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (DataAccessException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责店铺授权数据冲突，请刷新后重试。", error);
        }
    }

    @PutMapping("/users/{userId}/quota")
    public Map<String, Object> updateUserQuota(
            @PathVariable("userId") Long userId,
            @RequestBody MasterDataUserQuotaCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.updateUserQuota(userId, command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PutMapping("/users/{userId}/stores/{projectId}/quota")
    public Map<String, Object> updateStoreQuota(
            @PathVariable("userId") Long userId,
            @PathVariable("projectId") Long projectId,
            @RequestBody MasterDataUserQuotaCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.updateStoreQuota(userId, projectId, command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping("/users/{userId}/payments")
    public List<MasterDataPaymentRecordView> payments(
            @PathVariable("userId") Long userId,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            return service.listPayments(userId, requireSession(request).getUserId());
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PostMapping("/users/{userId}/payments")
    public Map<String, Object> addPayment(
            @PathVariable("userId") Long userId,
            @RequestBody MasterDataAddPaymentCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.addPayment(userId, command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PostMapping("/roles")
    public Map<String, Object> createRole(
            @RequestBody MasterDataSaveRoleCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.createRole(command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PutMapping("/roles/{roleId}")
    public Map<String, Object> updateRole(
            @PathVariable("roleId") Long roleId,
            @RequestBody MasterDataSaveRoleCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.updateRole(roleId, command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @DeleteMapping("/roles/{roleId}")
    public Map<String, Object> deleteRole(
            @PathVariable("roleId") Long roleId,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            Long currentUserId = requireManagementSession(request).getUserId();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.deleteRole(roleId, currentUserId));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PostMapping("/menus")
    public Map<String, Object> createMenu(
            @RequestBody MasterDataSaveMenuCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.createMenu(command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PutMapping("/menus/{menuId}")
    public Map<String, Object> updateMenu(
            @PathVariable("menuId") Long menuId,
            @RequestBody MasterDataSaveMenuCommand command,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            attachOperator(command, requireManagementSession(request).getUserId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.updateMenu(menuId, command));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @DeleteMapping("/menus/{menuId}")
    public Map<String, Object> deleteMenu(
            @PathVariable("menuId") Long menuId,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest request
    ) {
        LocalDbMasterDataService service = requireService();
        try {
            Long currentUserId = requireManagementSession(request).getUserId();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", service.deleteMenu(menuId, currentUserId));
            return payload;
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    private LocalDbMasterDataService requireService() {
        LocalDbMasterDataService service = masterDataServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前仍在无数据库骨架模式。");
        }
        return service;
    }

    private AuthenticatedSession requireSession(HttpServletRequest request) {
        return sessionTokenService.requireSession(request);
    }

    private AuthenticatedSession requireManagementSession(HttpServletRequest request) {
        AuthenticatedSession session = requireSession(request);
        if (isOperatorRoleView(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前为运营视角，不能执行老板管理操作。");
        }
        return session;
    }

    private AuthenticatedSession requireActiveSession(HttpServletRequest request, LocalDbMasterDataService service) {
        AuthenticatedSession session = requireSession(request);
        try {
            service.getOperatorRoleLevel(session.getUserId());
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录。", error);
        }
        return session;
    }

    private void attachOperator(MasterDataAssignRoleCommand command, Long operatorUserId) {
        if (command != null) {
            command.setOperatorUserId(operatorUserId);
        }
    }

    private void attachOperator(MasterDataSaveUserCommand command, Long operatorUserId) {
        if (command != null) {
            command.setOperatorUserId(operatorUserId);
        }
    }

    private void attachOperator(MasterDataToggleUserStatusCommand command, Long operatorUserId) {
        if (command != null) {
            command.setOperatorUserId(operatorUserId);
        }
    }

    private MasterDataResetPasswordCommand attachOperator(MasterDataResetPasswordCommand command, Long operatorUserId) {
        MasterDataResetPasswordCommand resolved = command != null ? command : new MasterDataResetPasswordCommand();
        resolved.setOperatorUserId(operatorUserId);
        return resolved;
    }

    private MasterDataAssignStoresCommand attachOperator(MasterDataAssignStoresCommand command, Long operatorUserId) {
        MasterDataAssignStoresCommand resolved = command != null ? command : new MasterDataAssignStoresCommand();
        resolved.setOperatorUserId(operatorUserId);
        return resolved;
    }

    private void attachOperator(MasterDataUserQuotaCommand command, Long operatorUserId) {
        if (command != null) {
            command.setOperatorUserId(operatorUserId);
        }
    }

    private void attachOperator(MasterDataAddPaymentCommand command, Long operatorUserId) {
        if (command != null) {
            command.setOperatorUserId(operatorUserId);
        }
    }

    private void attachOperator(MasterDataSaveRoleCommand command, Long operatorUserId) {
        if (command != null) {
            command.setOperatorUserId(operatorUserId);
        }
    }

    private void attachOperator(MasterDataSaveMenuCommand command, Long operatorUserId) {
        if (command != null) {
            command.setOperatorUserId(operatorUserId);
        }
    }
}
