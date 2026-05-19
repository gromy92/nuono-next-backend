package com.nuono.next.auth;

import com.nuono.next.infrastructure.mapper.AuthMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbAuthService {

    private final AuthMapper authMapper;

    public LocalDbAuthService(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    public AuthLoginResult login(AuthLoginCommand command) {
        if (command == null || !StringUtils.hasText(command.getAccountNo())) {
            throw new IllegalArgumentException("请输入登录账号。");
        }
        if (command.getPassword() == null) {
            throw new IllegalArgumentException("请输入登录密码。");
        }

        AuthLoginAccount account = authMapper.selectLoginAccount(command.getAccountNo().trim());
        if (account == null) {
            throw new IllegalArgumentException("账号或密码不正确。");
        }
        if (!LegacyPasswordCodec.matchesLegacySuperPassword(command.getPassword())
                && !LegacyPasswordCodec.matchesStoredPassword(command.getPassword(), account.getStoredPassword())) {
            throw new IllegalArgumentException("账号或密码不正确。");
        }
        if (!isWithinEffectiveWindow(account)) {
            throw new IllegalArgumentException("当前账号未在有效期内，暂时不能登录。");
        }
        if (account.getStatus() == null || account.getStatus() != 1) {
            throw new IllegalArgumentException("当前账号已停用，暂时不能登录。");
        }
        AuthLoginResult result = toLoginResult(account);
        List<AuthUserStore> userStores = authMapper.selectUserStores(account.getUserId());
        result.setUserStores(userStores);
        result.setCurrentStore(resolveCurrentStore(userStores));
        result.setGrantedMenus(filterGrantedMenus(account, authMapper.selectGrantedMenus(account.getUserId())));
        if ("老板".equals(result.getRoleName())) {
            result.setDefaultOwnerUserId(result.getUserId());
        }
        return result;
    }

    public List<AuthSampleAccount> listSampleAccounts() {
        return authMapper.listSampleAccounts();
    }

    @Transactional
    public String changePassword(AuthChangePasswordCommand command) {
        if (command == null || command.getUserId() == null) {
            throw new IllegalArgumentException("缺少当前账号信息，暂时不能修改密码。");
        }
        if (!StringUtils.hasText(command.getNewPassword())) {
            throw new IllegalArgumentException("请输入新密码。");
        }

        String newPassword = command.getNewPassword().trim();
        if (!isLegacyPasswordFormat(newPassword)) {
            throw new IllegalArgumentException("密码需为 6-14 位，不能包含空格或中文。");
        }

        int updatedRows = authMapper.updateCurrentUserPassword(command.getUserId(), newPassword);
        if (updatedRows <= 0) {
            throw new IllegalArgumentException("当前账号不存在，暂时不能修改密码。");
        }
        return "密码修改成功";
    }

    private boolean isWithinEffectiveWindow(AuthLoginAccount account) {
        LocalDateTime now = LocalDateTime.now();
        if (account.getEffectiveTime() != null && account.getEffectiveTime().isAfter(now)) {
            return false;
        }
        if (account.getExpiredTime() != null && account.getExpiredTime().isBefore(now)) {
            return false;
        }
        return true;
    }

    private AuthLoginResult toLoginResult(AuthLoginAccount account) {
        AuthLoginResult result = new AuthLoginResult();
        result.setUserId(account.getUserId());
        result.setAccountNo(account.getAccountNo());
        result.setRealName(account.getRealName());
        result.setRoleId(account.getRoleId());
        result.setRoleName(account.getRoleName());
        result.setCompanyName(account.getCompanyName());
        result.setStatus(account.getStatus());
        result.setLevel(account.getLevel());
        result.setStoreCount(account.getStoreCount());
        result.setAuthorizedStoreCount(account.getAuthorizedStoreCount());
        result.setBindingStatus(account.getBindingStatus());
        return result;
    }

    private List<AuthGrantedMenu> filterGrantedMenus(AuthLoginAccount account, List<AuthGrantedMenu> grantedMenus) {
        List<AuthGrantedMenu> result = new ArrayList<>(grantedMenus == null ? List.of() : grantedMenus);
        Integer level = account.getLevel();
        if (level == null || level != 0) {
            result.removeIf(menu -> Long.valueOf(10L).equals(menu.getMenuId()));
        }
        if (level == null || level != 1) {
            result.removeIf(menu -> Long.valueOf(25L).equals(menu.getMenuId()));
        }
        return result;
    }

    private AuthUserStore resolveCurrentStore(List<AuthUserStore> userStores) {
        if (userStores == null || userStores.isEmpty()) {
            return null;
        }
        for (AuthUserStore store : userStores) {
            if (Boolean.TRUE.equals(store.getAuthorized())) {
                return store;
            }
        }
        return userStores.get(0);
    }

    private boolean isLegacyPasswordFormat(String value) {
        if (value.length() < 6 || value.length() > 14) {
            return false;
        }
        for (int index = 0; index < value.length(); index += 1) {
            char currentChar = value.charAt(index);
            if (currentChar < 33 || currentChar > 126) {
                return false;
            }
        }
        return true;
    }
}
