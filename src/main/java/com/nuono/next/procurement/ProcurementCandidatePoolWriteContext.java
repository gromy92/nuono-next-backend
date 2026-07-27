package com.nuono.next.procurement;

final class ProcurementCandidatePoolWriteContext {

    final Long ownerUserId;
    final Long operatorUserId;
    final String operatorRole;

    ProcurementCandidatePoolWriteContext(Long ownerUserId, Long operatorUserId, String operatorRole) {
        this.ownerUserId = ownerUserId;
        this.operatorUserId = operatorUserId;
        this.operatorRole = operatorRole;
    }

    static ProcurementCandidatePoolWriteContext requireAuthenticated(
            ProcurementCandidatePoolWriteContext context,
            String actionName
    ) {
        if (context == null || context.ownerUserId == null || context.ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能" + actionName + "。");
        }
        if (context.operatorUserId == null || context.operatorUserId <= 0) {
            throw new IllegalArgumentException("缺少操作人，暂时不能" + actionName + "。");
        }
        return context;
    }
}
