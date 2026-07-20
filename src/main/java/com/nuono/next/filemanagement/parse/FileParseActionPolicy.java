package com.nuono.next.filemanagement.parse;

import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class FileParseActionPolicy {

    public boolean isSystemAdmin(FileParseUserContext user) {
        if (user == null) {
            return false;
        }
        return hasRoleLevel(user, 0)
                || "SYSTEM_ADMIN".equalsIgnoreCase(user.getRoleCode())
                || "系统管理员".equals(user.getRoleName());
    }

    public boolean isLogisticsPlan(FileParseTargetPlanRow row) {
        if (row == null) {
            return false;
        }
        return startsWithIgnoreCase(row.getCode(), "logistics")
                || startsWithIgnoreCase(row.getDocumentType(), "logistics");
    }

    public FileParseAvailableActions availableActions(
            FileParseTargetPlanRow row,
            FileParseUserContext user
    ) {
        boolean systemAdmin = isSystemAdmin(user);
        boolean boss = isBoss(user);
        boolean opsManager = isOpsManager(user);
        boolean logisticsPlan = isLogisticsPlan(row);

        FileParseAvailableActions actions = new FileParseAvailableActions();
        actions.setCanCreateTask(systemAdmin || boss || opsManager);
        actions.setCanProcess(systemAdmin || boss || opsManager);
        actions.setCanPublish(systemAdmin);
        actions.setCanManageStandard(systemAdmin);
        actions.setCanActivateLogisticsChannels(logisticsPlan && (systemAdmin || boss));
        return actions;
    }

    private boolean isBoss(FileParseUserContext user) {
        return hasRoleLevel(user, 1)
                || "BOSS".equalsIgnoreCase(user.getRoleCode())
                || "老板".equals(user.getRoleName());
    }

    private boolean isOpsManager(FileParseUserContext user) {
        return hasRoleLevel(user, 2)
                || "OPS_MANAGER".equalsIgnoreCase(user.getRoleCode())
                || "运营主管".equals(user.getRoleName());
    }

    private boolean hasRoleLevel(FileParseUserContext user, int level) {
        return user != null && user.getRoleLevel() != null && user.getRoleLevel() == level;
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return StringUtils.hasText(value)
                && value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }
}
