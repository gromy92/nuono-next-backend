package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonProductionSchedulerEnablementGate {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final NoonPullFoundationService foundationService;
    private final NoonPullSmokeRunRepository smokeRunRepository;
    private final NoonProductionSchedulerEnablementRepository enablementRepository;
    private final Clock clock;

    @Autowired
    public NoonProductionSchedulerEnablementGate(
            NoonPullFoundationService foundationService,
            ObjectProvider<NoonPullSmokeRunRepository> smokeRunRepository,
            ObjectProvider<NoonProductionSchedulerEnablementRepository> enablementRepository
    ) {
        this(
                foundationService,
                smokeRunRepository == null ? null : smokeRunRepository.getIfAvailable(),
                enablementRepository == null ? null : enablementRepository.getIfAvailable(),
                Clock.system(SHANGHAI)
        );
    }

    NoonProductionSchedulerEnablementGate(
            NoonPullFoundationService foundationService,
            NoonPullSmokeRunRepository smokeRunRepository,
            NoonProductionSchedulerEnablementRepository enablementRepository,
            Clock clock
    ) {
        this.foundationService = foundationService;
        this.smokeRunRepository = smokeRunRepository;
        this.enablementRepository = enablementRepository;
        this.clock = clock == null ? Clock.system(SHANGHAI) : clock.withZone(SHANGHAI);
    }

    public NoonProductionSchedulerEnablementResult enable(
            NoonProductionSchedulerEnablementCommand command,
            Long operatorUserId
    ) {
        NoonProductionSchedulerEnablementResult result = new NoonProductionSchedulerEnablementResult();
        List<String> rejectionReasons = new ArrayList<>();
        if (command == null) {
            rejectionReasons.add("COMMAND_REQUIRED");
            result.setRejectionReasons(rejectionReasons);
            saveDecision(null, operatorUserId, null, rejectionReasons, List.of(), "REJECTED");
            return result;
        }

        List<NoonPullDataDomain> schedulableDomains = schedulableDomains(command);
        NoonPullSmokeRunRecord smokeRun = latestMatchingSmokeRun(command);
        if (smokeRun != null) {
            result.setSmokeRunId(smokeRun.getId());
        }

        validateCommand(command, rejectionReasons);
        validateSmokeRun(smokeRun, schedulableDomains, rejectionReasons);

        if (!rejectionReasons.isEmpty()) {
            NoonProductionSchedulerEnablementRecord record = saveDecision(
                    command,
                    operatorUserId,
                    smokeRun,
                    rejectionReasons,
                    List.of(),
                    "REJECTED"
            );
            result.setEnabled(false);
            result.setRejectionReasons(rejectionReasons);
            result.setEnabledDomains(domainNames(schedulableDomains));
            result.setDecisionRecordId(record == null ? null : String.valueOf(record.getId()));
            return result;
        }

        List<Long> planIds = new ArrayList<>();
        for (ScheduledPlanSpec spec : scheduledPlanSpecs(schedulableDomains)) {
            NoonPullPlanRecord plan = findExistingScheduledPlan(command, spec.getPullType(), spec.getDataDomain());
            if (plan == null) {
                plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                        .ownerUserId(command.getOwnerUserId())
                        .storeCode(command.getStoreCode())
                        .siteCode(command.getSiteCode())
                        .pullType(spec.getPullType())
                        .dataDomain(spec.getDataDomain())
                        .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                        .scheduleExpression(command.getScheduleBoundaries())
                        .build());
            } else if (plan.isPaused()) {
                plan = foundationService.resumePlan(plan.getId());
            }
            planIds.add(plan.getId());
        }

        NoonProductionSchedulerEnablementRecord record = saveDecision(
                command,
                operatorUserId,
                smokeRun,
                List.of(),
                planIds,
                "ENABLED"
        );
        result.setEnabled(true);
        result.setSmokeRunId(smokeRun.getId());
        result.setPlanIds(planIds);
        result.setEnabledDomains(domainNames(schedulableDomains));
        result.setDecisionRecordId(record == null ? null : String.valueOf(record.getId()));
        return result;
    }

    private void validateCommand(NoonProductionSchedulerEnablementCommand command, List<String> reasons) {
        if (!isSupportedTargetEnvironment(command.getTargetEnvironment())) {
            reasons.add("UNSUPPORTED_TARGET_ENVIRONMENT");
        }
        if (command.getOwnerUserId() == null || !StringUtils.hasText(command.getStoreCode())
                || !StringUtils.hasText(command.getSiteCode())) {
            reasons.add("TARGET_SCOPE_REQUIRED");
        }
        if (!command.isHitlApproved()) {
            reasons.add("HITL_APPROVAL_REQUIRED");
        }
        if (!StringUtils.hasText(command.getRollbackOrGlobalPauseStrategy())) {
            reasons.add("ROLLBACK_OR_GLOBAL_PAUSE_REQUIRED");
        }
        if (!StringUtils.hasText(command.getScheduleBoundaries())) {
            reasons.add("SCHEDULE_BOUNDARIES_REQUIRED");
        }
    }

    private boolean isSupportedTargetEnvironment(String targetEnvironment) {
        return "test".equalsIgnoreCase(targetEnvironment)
                || "production".equalsIgnoreCase(targetEnvironment);
    }

    private void validateSmokeRun(
            NoonPullSmokeRunRecord smokeRun,
            List<NoonPullDataDomain> requiredDomains,
            List<String> reasons
    ) {
        if (smokeRun == null) {
            reasons.add("READY_SMOKE_RUN_REQUIRED");
            return;
        }
        if (hasBlockingSmokeGateMiss(smokeRun, requiredDomains)
                || (!smokeRun.isProductionSchedulingAllowed() && smokeRun.getMissingRequirements().isEmpty())) {
            reasons.add("SMOKE_RUN_GATE_NOT_READY");
        }
        Map<NoonPullDataDomain, NoonPullSmokeEvidenceRecord> evidenceByDomain = new EnumMap<>(NoonPullDataDomain.class);
        for (NoonPullSmokeEvidenceRecord evidence : smokeRun.getEvidence()) {
            if (StringUtils.hasText(evidence.getDataDomain())) {
                evidenceByDomain.put(NoonPullDataDomain.valueOf(evidence.getDataDomain()), evidence);
            }
        }
        for (NoonPullDataDomain domain : requiredDomains) {
            NoonPullSmokeEvidenceRecord evidence = evidenceByDomain.get(domain);
            if (!isReadyEvidence(evidence)) {
                reasons.add(domain.name() + "_SMOKE_NOT_READY");
            }
        }
        if (!StringUtils.hasText(smokeRun.getRollbackOrGlobalPauseStrategy())) {
            reasons.add("SMOKE_RUN_ROLLBACK_OR_GLOBAL_PAUSE_REQUIRED");
        }
    }

    private boolean hasBlockingSmokeGateMiss(
            NoonPullSmokeRunRecord smokeRun,
            List<NoonPullDataDomain> requiredDomains
    ) {
        for (String missingRequirement : smokeRun.getMissingRequirements()) {
            if (!isIgnorableMissingSmokeRequirement(missingRequirement, requiredDomains)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIgnorableMissingSmokeRequirement(
            String missingRequirement,
            List<NoonPullDataDomain> requiredDomains
    ) {
        if (!StringUtils.hasText(missingRequirement)) {
            return true;
        }
        if ("PRODUCT_SMOKE".equals(missingRequirement)) {
            return !requiredDomains.contains(NoonPullDataDomain.PRODUCT);
        }
        if ("SALES_SMOKE".equals(missingRequirement)) {
            return !requiredDomains.contains(NoonPullDataDomain.SALES);
        }
        if ("ORDER_SMOKE".equals(missingRequirement)) {
            return !requiredDomains.contains(NoonPullDataDomain.ORDER);
        }
        return false;
    }

    private boolean isReadyEvidence(NoonPullSmokeEvidenceRecord evidence) {
        return evidence != null
                && NoonPullTaskStatus.SUCCEEDED.name().equals(evidence.getStatus())
                && "ready".equals(evidence.getQualityState())
                && StringUtils.hasText(evidence.getSourceBatchId());
    }

    private NoonPullSmokeRunRecord latestMatchingSmokeRun(NoonProductionSchedulerEnablementCommand command) {
        if (command == null || smokeRunRepository == null) {
            return null;
        }
        return smokeRunRepository.listRecent(100).stream()
                .filter((run) -> safeEquals(command.getTargetEnvironment(), run.getTargetEnvironment()))
                .filter((run) -> safeEquals(command.getOwnerUserId(), run.getOwnerUserId()))
                .filter((run) -> safeEquals(command.getStoreCode(), run.getStoreCode()))
                .filter((run) -> safeEquals(command.getSiteCode(), run.getSiteCode()))
                .max(Comparator.comparing(NoonPullSmokeRunRecord::getId))
                .orElse(null);
    }

    private NoonPullPlanRecord findExistingScheduledPlan(
            NoonProductionSchedulerEnablementCommand command,
            NoonPullType pullType,
            NoonPullDataDomain domain
    ) {
        return foundationService.listPlans().stream()
                .filter((plan) -> safeEquals(command.getOwnerUserId(), plan.getOwnerUserId()))
                .filter((plan) -> safeEquals(command.getStoreCode(), plan.getStoreCode()))
                .filter((plan) -> safeEquals(command.getSiteCode(), plan.getSiteCode()))
                .filter((plan) -> plan.getPullType() == pullType)
                .filter((plan) -> plan.getDataDomain() == domain)
                .filter((plan) -> plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY)
                .findFirst()
                .orElse(null);
    }

    private List<ScheduledPlanSpec> scheduledPlanSpecs(List<NoonPullDataDomain> schedulableDomains) {
        List<ScheduledPlanSpec> specs = new ArrayList<>();
        for (NoonPullDataDomain domain : schedulableDomains) {
            if (domain == NoonPullDataDomain.SALES) {
                specs.add(new ScheduledPlanSpec(NoonPullType.REPORT, NoonPullDataDomain.SALES));
            } else if (domain == NoonPullDataDomain.ORDER) {
                specs.add(new ScheduledPlanSpec(NoonPullType.REPORT, NoonPullDataDomain.ORDER));
            } else if (domain == NoonPullDataDomain.FINANCE_TRANSACTION) {
                specs.add(new ScheduledPlanSpec(NoonPullType.REPORT, NoonPullDataDomain.FINANCE_TRANSACTION));
            } else if (domain == NoonPullDataDomain.NOON_ADVERTISING) {
                specs.add(new ScheduledPlanSpec(NoonPullType.REPORT, NoonPullDataDomain.NOON_ADVERTISING));
            } else if (domain == NoonPullDataDomain.PRODUCT) {
                specs.add(new ScheduledPlanSpec(NoonPullType.INTERFACE, NoonPullDataDomain.PRODUCT));
            } else if (domain == NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY) {
                specs.add(new ScheduledPlanSpec(NoonPullType.INTERFACE, NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY));
            } else if (domain == NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED) {
                specs.add(new ScheduledPlanSpec(NoonPullType.REPORT, NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED));
            }
        }
        return specs;
    }

    private List<NoonPullDataDomain> schedulableDomains(NoonProductionSchedulerEnablementCommand command) {
        Set<NoonPullDataDomain> domains = new LinkedHashSet<>();
        List<NoonPullDataDomain> requested = command.getEnabledDomains().isEmpty()
                ? List.of(NoonPullDataDomain.SALES, NoonPullDataDomain.ORDER)
                : command.getEnabledDomains();
        for (NoonPullDataDomain domain : requested) {
            if (domain == NoonPullDataDomain.SALES
                    || domain == NoonPullDataDomain.ORDER
                    || domain == NoonPullDataDomain.FINANCE_TRANSACTION
                    || domain == NoonPullDataDomain.NOON_ADVERTISING
                    || domain == NoonPullDataDomain.PRODUCT
                    || domain == NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY
                    || domain == NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED) {
                domains.add(domain);
            }
        }
        return new ArrayList<>(domains);
    }

    private NoonProductionSchedulerEnablementRecord saveDecision(
            NoonProductionSchedulerEnablementCommand command,
            Long operatorUserId,
            NoonPullSmokeRunRecord smokeRun,
            List<String> rejectionReasons,
            List<Long> planIds,
            String decision
    ) {
        if (enablementRepository == null) {
            return null;
        }
        NoonProductionSchedulerEnablementRecord record = new NoonProductionSchedulerEnablementRecord();
        if (command != null) {
            record.setTargetEnvironment(command.getTargetEnvironment());
            record.setOwnerUserId(command.getOwnerUserId());
            record.setProjectCode(command.getProjectCode());
            record.setProjectName(command.getProjectName());
            record.setStoreCode(command.getStoreCode());
            record.setSiteCode(command.getSiteCode());
            record.setEnabledDomains(domainNames(schedulableDomains(command)));
            record.setScheduleBoundaries(command.getScheduleBoundaries());
            record.setRollbackOrGlobalPauseStrategy(NoonPullSafeText.redact(command.getRollbackOrGlobalPauseStrategy()));
            record.setHitlApproved(command.isHitlApproved());
        }
        record.setOperatorUserId(operatorUserId);
        record.setSmokeRunId(smokeRun == null ? null : smokeRun.getId());
        record.setDecision(decision);
        record.setRejectionReasons(rejectionReasons);
        record.setPlanIds(planIds);
        LocalDateTime now = LocalDateTime.now(clock);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return enablementRepository.save(record);
    }

    private List<String> domainNames(List<NoonPullDataDomain> domains) {
        return domains.stream().map(Enum::name).collect(Collectors.toList());
    }

    private boolean safeEquals(Object expected, Object actual) {
        return expected == null ? actual == null : expected.equals(actual);
    }

    private static final class ScheduledPlanSpec {
        private final NoonPullType pullType;
        private final NoonPullDataDomain dataDomain;

        private ScheduledPlanSpec(NoonPullType pullType, NoonPullDataDomain dataDomain) {
            this.pullType = pullType;
            this.dataDomain = dataDomain;
        }

        private NoonPullType getPullType() {
            return pullType;
        }

        private NoonPullDataDomain getDataDomain() {
            return dataDomain;
        }
    }
}
