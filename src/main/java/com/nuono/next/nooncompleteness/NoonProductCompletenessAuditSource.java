package com.nuono.next.nooncompleteness;

public interface NoonProductCompletenessAuditSource {
    NoonProductCompletenessAudit audit(NoonDataAuditCommand command);
}
