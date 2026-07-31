package com.nuono.next.product;

import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonPullFailureType;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

class ProductActiveStateReconciliationGuard {
    private final NoonRiskBackoffGuard riskBackoffGuard;
    private final NoonPullFailurePolicy failurePolicy;

    ProductActiveStateReconciliationGuard(
            NoonRiskBackoffGuard riskBackoffGuard,
            NoonPullFailurePolicy failurePolicy
    ) {
        this.riskBackoffGuard = riskBackoffGuard == null ? NoonRiskBackoffGuard.disabled() : riskBackoffGuard;
        this.failurePolicy = failurePolicy == null ? new NoonPullFailurePolicy() : failurePolicy;
    }

    static ProductActiveStateReconciliationGuard disabled(Clock clock) {
        return new ProductActiveStateReconciliationGuard(
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy(clock)
        );
    }

    boolean isHeld(Long ownerUserId, String storeCode, String siteCode) {
        return riskBackoffGuard.currentHold(scope(ownerUserId, storeCode, siteCode)).isPresent();
    }

    ProductMasterSnapshotView fetch(
            LocalDbProductMasterService productMasterService,
            ProductMasterFetchCommand command,
            String siteCode
    ) {
        NoonRiskBackoffScope scope = scope(command.getOwnerUserId(), command.getStoreCode(), siteCode);
        ProductMasterSnapshotView snapshot;
        try {
            snapshot = productMasterService.fetchSnapshot(command);
        } catch (RuntimeException exception) {
            recordIfRisk(scope, failurePolicy.classify(exception.getMessage()), exception.getMessage());
            throw exception;
        }
        String evidence = failureEvidence(snapshot);
        NoonPullFailureType warningType = failurePolicy.classify(evidence);
        if (isRiskFailure(warningType)) {
            record(scope, warningType, evidence);
            throw new IllegalStateException(warningType.code() + ": Noon 商品状态补证已暂停");
        }
        riskBackoffGuard.recordSuccess(scope, "PRODUCT");
        return snapshot;
    }

    private void recordIfRisk(NoonRiskBackoffScope scope, NoonPullFailureType type, String evidence) {
        if (isRiskFailure(type)) {
            record(scope, type, evidence);
        }
    }

    private void record(NoonRiskBackoffScope scope, NoonPullFailureType type, String evidence) {
        riskBackoffGuard.recordRiskSignal(scope, type.code(), "PRODUCT", null, null, evidence);
    }

    private String failureEvidence(ProductMasterSnapshotView snapshot) {
        if (snapshot == null) {
            return null;
        }
        List<String> evidence = new ArrayList<>();
        if (StringUtils.hasText(snapshot.getMessage())) {
            evidence.add(snapshot.getMessage());
        }
        if (snapshot.getWarnings() != null) {
            for (String warning : snapshot.getWarnings()) {
                if (StringUtils.hasText(warning)) {
                    evidence.add(warning);
                }
            }
        }
        return String.join("; ", evidence);
    }

    private boolean isRiskFailure(NoonPullFailureType type) {
        return type == NoonPullFailureType.RATE_LIMITED
                || type == NoonPullFailureType.CAPTCHA_REQUIRED
                || type == NoonPullFailureType.BLOCKED_BY_RISK_CONTROL;
    }

    private NoonRiskBackoffScope scope(Long ownerUserId, String storeCode, String siteCode) {
        return NoonRiskBackoffScope.productInterface(ownerUserId, storeCode, siteCode);
    }
}
