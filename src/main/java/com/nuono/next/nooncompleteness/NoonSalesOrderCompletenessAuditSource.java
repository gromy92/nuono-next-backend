package com.nuono.next.nooncompleteness;

public interface NoonSalesOrderCompletenessAuditSource {
    NoonSalesOrderCompletenessAudit audit(NoonDataAuditCommand command);
}
