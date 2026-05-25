package com.nuono.next.nooncompleteness;

import com.nuono.next.infrastructure.mapper.NoonSalesFactMapper;
import org.springframework.stereotype.Component;

@Component
public class NoonSalesFactCompletenessAuditSource implements NoonSalesProductViewsCompletenessAuditSource {
    private final NoonSalesFactMapper mapper;

    public NoonSalesFactCompletenessAuditSource(NoonSalesFactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public NoonSalesProductViewsCompletenessAudit audit(NoonDataAuditCommand command) {
        NoonSalesProductViewsCompletenessAudit audit = mapper.auditSalesProductViewsCompleteness(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        return audit == null ? NoonSalesProductViewsCompletenessAudit.missing() : audit;
    }
}
