package com.nuono.next.nooncompleteness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NoonDataCompletenessBootstrapService {
    private static final int FIRST_VERSION_CATEGORY_COUNT = 4;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final NoonDataCompletenessAuditScopeSource scopeSource;
    private final NoonDataAuditService auditService;
    private final Clock clock;

    @Autowired
    public NoonDataCompletenessBootstrapService(
            NoonDataCompletenessAuditScopeSource scopeSource,
            NoonDataAuditService auditService
    ) {
        this(scopeSource, auditService, Clock.systemUTC());
    }

    public NoonDataCompletenessBootstrapService(
            NoonDataCompletenessAuditScopeSource scopeSource,
            NoonDataAuditService auditService,
            Clock clock
    ) {
        this.scopeSource = scopeSource;
        this.auditService = auditService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public NoonDataCompletenessBootstrapResult auditExistingScopes() {
        List<NoonDataCompletenessAuditScope> scopes = scopeSource.listAuditScopes();
        LocalDate auditDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        int scopeCount = 0;
        for (NoonDataCompletenessAuditScope scope : scopes) {
            if (!isValid(scope)) {
                continue;
            }
            NoonDataAuditCommand command = NoonDataAuditCommand.builder()
                    .ownerUserId(scope.getOwnerUserId())
                    .storeCode(scope.getStoreCode())
                    .siteCode(scope.getSiteCode())
                    .auditDate(auditDate)
                    .build();
            auditService.auditProductCompleteness(command);
            auditService.auditSalesCompleteness(command);
            scopeCount++;
        }
        return new NoonDataCompletenessBootstrapResult(
                scopeCount,
                scopeCount * FIRST_VERSION_CATEGORY_COUNT,
                "已完成存量店铺数据完整性审计。"
        );
    }

    private boolean isValid(NoonDataCompletenessAuditScope scope) {
        return scope != null
                && scope.getOwnerUserId() != null
                && scope.getStoreCode() != null
                && !scope.getStoreCode().isBlank()
                && scope.getSiteCode() != null
                && !scope.getSiteCode().isBlank();
    }
}
