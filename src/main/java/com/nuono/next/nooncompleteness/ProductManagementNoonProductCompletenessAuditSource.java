package com.nuono.next.nooncompleteness;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import org.springframework.stereotype.Component;

@Component
public class ProductManagementNoonProductCompletenessAuditSource implements NoonProductCompletenessAuditSource {
    private final ProductManagementMapper mapper;

    public ProductManagementNoonProductCompletenessAuditSource(ProductManagementMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public NoonProductCompletenessAudit audit(NoonDataAuditCommand command) {
        NoonProductCompletenessAudit audit = mapper.auditNoonProductCompleteness(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        return audit == null ? NoonProductCompletenessAudit.empty() : audit;
    }
}
