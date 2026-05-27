package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.sales.ProductLifecycleCalculationJobService;
import com.nuono.next.sales.ProductLifecycleCalculationScope;
import com.nuono.next.sales.ProductLifecycleJobRecord;
import com.nuono.next.sales.ProductLifecycleResult;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationConfigLifecycleRecalculationService {

    private final OperationConfigScopeService scopeService;
    private final ProductLifecycleCalculationJobService jobService;
    private final OperationLifecycleRuleRepository lifecycleRuleRepository;

    public OperationConfigLifecycleRecalculationService(
            OperationConfigScopeService scopeService,
            ProductLifecycleCalculationJobService jobService,
            OperationLifecycleRuleRepository lifecycleRuleRepository
    ) {
        this.scopeService = scopeService;
        this.jobService = jobService;
        this.lifecycleRuleRepository = lifecycleRuleRepository;
    }

    public ProductLifecycleJobRecord recalculate(
            BusinessAccessContext context,
            OperationConfigLifecycleRecalculationCommand command
    ) {
        validate(command);
        scopeService.requireStoreSiteScope(
                context,
                command.getBossUserIds(),
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        String ruleVersion = resolveSelectedRuleVersion(command);
        return jobService.run(new ProductLifecycleCalculationScope(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getAnchorDate(),
                ruleVersion,
                true,
                true,
                context.getSessionUserId(),
                "operations_config"
        ));
    }

    private void validate(OperationConfigLifecycleRecalculationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.getOwnerUserId() == null
                || !StringUtils.hasText(command.getStoreCode())
                || !StringUtils.hasText(command.getSiteCode())) {
            throw new IllegalArgumentException("scope is required");
        }
        if (command.getAnchorDate() == null || command.getAnchorDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("anchorDate is invalid");
        }
    }

    private String resolveSelectedRuleVersion(OperationConfigLifecycleRecalculationCommand command) {
        String selectedRuleVersion = StringUtils.hasText(command.getSelectedRuleVersion())
                ? command.getSelectedRuleVersion().trim()
                : ProductLifecycleResult.DEFAULT_RULE_VERSION;
        if (ProductLifecycleResult.DEFAULT_RULE_VERSION.equals(selectedRuleVersion)) {
            return selectedRuleVersion;
        }
        boolean publishedInScope = lifecycleRuleRepository.listRules(
                        command.getOwnerUserId(),
                        command.getStoreCode(),
                        command.getSiteCode()
                )
                .stream()
                .anyMatch(rule -> selectedRuleVersion.equals(rule.getRuleVersion())
                        && OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus()));
        if (!publishedInScope) {
            throw new IllegalArgumentException("selectedRuleVersion is not published for scope");
        }
        return selectedRuleVersion;
    }
}
