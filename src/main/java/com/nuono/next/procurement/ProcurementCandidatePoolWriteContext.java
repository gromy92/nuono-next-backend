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
}
