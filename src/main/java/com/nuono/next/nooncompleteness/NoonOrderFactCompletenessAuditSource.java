package com.nuono.next.nooncompleteness;

import com.nuono.next.infrastructure.mapper.NoonOrderFactMapper;
import org.springframework.stereotype.Component;

@Component
public class NoonOrderFactCompletenessAuditSource implements NoonSalesOrderCompletenessAuditSource {
    private final NoonOrderFactMapper mapper;

    public NoonOrderFactCompletenessAuditSource(NoonOrderFactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public NoonSalesOrderCompletenessAudit audit(NoonDataAuditCommand command) {
        NoonSalesOrderCompletenessAudit audit = mapper.auditSalesOrderCompleteness(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        return audit == null ? NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated") : audit;
    }
}
