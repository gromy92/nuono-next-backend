package com.nuono.next.procurement;

import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.procurement.ProcurementRequirementConfirmationCommands.OperatorCommand;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.OperatorContextRow;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
class ProcurementCandidatePoolPermissionGuard {

    private static final String ROLE_PURCHASE = "PURCHASE";

    private final ProcurementRequirementConfirmationMapper mapper;

    ProcurementCandidatePoolPermissionGuard(ProcurementRequirementConfirmationMapper mapper) {
        this.mapper = mapper;
    }

    ProcurementCandidatePoolWriteContext resolveWriteContext(OperatorCommand command, String actionName) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少后端确认的老板上下文，暂时不能" + actionName + "。");
        }
        if (command.getOperatorUserId() == null) {
            throw new IllegalArgumentException("缺少后端确认的操作人，暂时不能" + actionName + "。");
        }
        OperatorContextRow operator = mapper.selectOperatorContext(command.getOperatorUserId());
        if (operator == null || operator.getStatus() == null || operator.getStatus() != 1) {
            throw new IllegalArgumentException("当前操作账号不存在或已停用，暂时不能" + actionName + "。");
        }
        if (!isPurchaseOperator(operator)) {
            throw new IllegalArgumentException("当前账号不是采购角色，不能" + actionName + "。");
        }
        return new ProcurementCandidatePoolWriteContext(command.getOwnerUserId(), operator.getUserId(), resolveOperatorRole(operator));
    }

    ProcurementCandidatePoolWriteContext systemWriteContext(Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("缺少老板上下文，系统任务不能更新采购待选池。");
        }
        return new ProcurementCandidatePoolWriteContext(ownerUserId, 0L, "SYSTEM_TASK");
    }

    private boolean isPurchaseOperator(OperatorContextRow operator) {
        if (operator == null) {
            return false;
        }
        String roleCode = upper(operator.getRoleCode());
        if (ROLE_PURCHASE.equals(roleCode) || "BUYER".equals(roleCode)) {
            return true;
        }
        String roleName = normalize(operator.getRoleName());
        if (roleName != null && roleName.contains("采购")) {
            return true;
        }
        String userRole = normalize(operator.getUserRole());
        return userRole != null && userRole.contains("采购");
    }

    private String resolveOperatorRole(OperatorContextRow operator) {
        String roleCode = upper(operator.getRoleCode());
        if (StringUtils.hasText(roleCode)) {
            return roleCode;
        }
        String roleName = normalize(operator.getRoleName());
        if (StringUtils.hasText(roleName)) {
            return roleName;
        }
        String userRole = normalize(operator.getUserRole());
        return userRole == null ? ROLE_PURCHASE : userRole;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized.toUpperCase(Locale.ROOT);
    }
}
