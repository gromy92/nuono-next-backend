package com.nuono.next.officialwarehouse;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-db")
public class OfficialWarehouseAppointmentLifecycleModule {
    private static final String STATE_CHANGED_MESSAGE =
            "约仓状态已变化，请刷新后重试。";

    private final OfficialWarehouseMapper mapper;
    private final OfficialWarehouseAppointmentReconciliationImplementation reconciliation;
    public OfficialWarehouseAppointmentLifecycleModule(OfficialWarehouseMapper mapper) {
        this.mapper = mapper;
        this.reconciliation =
                new OfficialWarehouseAppointmentReconciliationImplementation(mapper);
    }
    @Transactional
    public AppointmentRecord saveRequest(AppointmentInsertRecord request) {
        OfficialWarehouseAppointmentPreparedRequest prepared =
                prepareRequest(request, false);
        return current(prepared.ownerUserId, prepared.appointmentId);
    }
    @Transactional
    public OfficialWarehouseAppointmentRunClaim saveAndClaimSelected(
            AppointmentInsertRecord request
    ) {
        OfficialWarehouseAppointmentPreparedRequest prepared =
                prepareRequest(request, true);
        if (mapper.markAppointmentRunning(
                prepared.ownerUserId,
                prepared.appointmentId,
                prepared.executionVersion,
                request.operatorUserId
        ) != 1) {
            throw conflict(STATE_CHANGED_MESSAGE);
        }
        return claimed(
                prepared.ownerUserId,
                prepared.appointmentId,
                prepared.executionVersion + 1
        );
    }
    @Transactional
    public OfficialWarehouseAppointmentRunClaim claimManual(
            AppointmentRecord expected,
            Long operatorUserId
    ) {
        OfficialWarehouseAppointmentReconciliationPolicy.ensureClaimable(expected);
        long version = version(expected);
        if (mapper.markAppointmentRunning(
                expected.ownerUserId, expected.id, version, operatorUserId
        ) != 1) {
            throw conflict(STATE_CHANGED_MESSAGE);
        }
        return claimed(expected.ownerUserId, expected.id, version + 1);
    }
    @Transactional
    public OfficialWarehouseAppointmentRunClaim claimDue(
            AppointmentRecord expected,
            Long operatorUserId
    ) {
        if (expected == null || !"PENDING".equals(expected.status)) {
            return null;
        }
        long version = version(expected);
        if (mapper.claimDueAppointmentForRun(
                expected.ownerUserId, expected.id, version, operatorUserId
        ) != 1) {
            return null;
        }
        return claimed(expected.ownerUserId, expected.id, version + 1);
    }
    public boolean completeScheduled(
            OfficialWarehouseAppointmentRunClaim claim,
            LocalDate appointmentDate,
            Integer slotId,
            String appointmentTime,
            Long operatorUserId
    ) {
        return mapper.markAppointmentScheduled(
                claim.appointment().ownerUserId,
                claim.appointment().id,
                claim.executionVersion(),
                appointmentDate,
                slotId,
                appointmentTime,
                operatorUserId
        ) == 1;
    }
    public boolean completePending(
            OfficialWarehouseAppointmentRunClaim claim,
            int retrySeconds,
            String errorStage,
            String failureType,
            String errorMessage,
            Long operatorUserId
    ) {
        return mapper.markAppointmentPendingRetry(
                claim.appointment().ownerUserId,
                claim.appointment().id,
                claim.executionVersion(),
                retrySeconds,
                errorStage,
                failureType,
                errorMessage,
                operatorUserId
        ) == 1;
    }
    public boolean completeFailed(
            OfficialWarehouseAppointmentRunClaim claim,
            String errorStage,
            String failureType,
            String errorMessage,
            Long operatorUserId
    ) {
        return mapper.markAppointmentFailed(
                claim.appointment().ownerUserId,
                claim.appointment().id,
                claim.executionVersion(),
                errorStage,
                failureType,
                errorMessage,
                operatorUserId
        ) == 1;
    }
    public boolean completeUnknownWrite(
            OfficialWarehouseAppointmentRunClaim claim,
            String sourceFailureType,
            String message,
            Long operatorUserId
    ) {
        return completeFailed(
                claim,
                "RECONCILIATION",
                OfficialWarehouseAppointmentReconciliationPolicy.NOON_WRITE,
                sourceFailureType + ": " + message,
                operatorUserId
        );
    }
    public int quarantineStaleExecutions(int staleMinutes, Long operatorUserId) {
        return mapper.markStaleAppointmentsForReconciliation(
                staleMinutes, operatorUserId
        );
    }
    public void guardExecution(
            OfficialWarehouseAppointmentRunClaim claim,
            Long operatorUserId
    ) {
        if (mapper.heartbeatAppointmentExecution(
                claim.appointment().ownerUserId,
                claim.appointment().id,
                claim.executionVersion(),
                operatorUserId
        ) != 1) {
            throw conflict(STATE_CHANGED_MESSAGE);
        }
    }
    @Transactional
    public AppointmentRecord cancel(AppointmentRecord expected, Long operatorUserId) {
        OfficialWarehouseAppointmentReconciliationPolicy.ensureCancellable(expected);
        if ("CANCELED".equals(expected.status)) {
            return current(expected.ownerUserId, expected.id);
        }
        if (!"PENDING".equals(expected.status) && !"FAILED".equals(expected.status)) {
            throw conflict("执行中或已成功的约仓不能取消，请先刷新并核对 Noon 状态。");
        }
        if (mapper.cancelAppointment(
                expected.ownerUserId,
                expected.id,
                version(expected),
                operatorUserId
        ) != 1) {
            throw conflict(STATE_CHANGED_MESSAGE);
        }
        return current(expected.ownerUserId, expected.id);
    }
    @Transactional
    public AppointmentRecord correct(
            AppointmentRecord expected,
            OfficialWarehouseAppointmentCorrection correction,
            Long operatorUserId,
            boolean reconciliationConfirmed
    ) {
        OfficialWarehouseAppointmentReconciliationPolicy.ensureCorrectable(
                expected, reconciliationConfirmed
        );
        try {
            if (reconciliation.correct(expected, correction, operatorUserId) != 1) {
                throw conflict(STATE_CHANGED_MESSAGE);
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("该 ASN 已有另一条有效约仓，请刷新后处理当前记录。");
        }
        return current(expected.ownerUserId, expected.id);
    }
    @Transactional
    public OfficialWarehouseAppointmentReconcileOutcome reconcileFromNoon(
            AppointmentInsertRecord seed,
            OfficialWarehouseAppointmentCorrection correction,
            boolean createIfMissing
    ) {
        return reconciliation.reconcile(seed, correction, createIfMissing);
    }
    private OfficialWarehouseAppointmentPreparedRequest prepareRequest(
            AppointmentInsertRecord request,
            boolean allowScheduled
    ) {
        lockParent(request.ownerUserId, request.asnId);
        AppointmentRecord existing =
                mapper.selectActiveAppointmentByAsnForUpdate(request.ownerUserId, request.asnId);
        if (existing == null) {
            request.id = mapper.nextAppointmentId();
            insert(request);
            return new OfficialWarehouseAppointmentPreparedRequest(
                    request.ownerUserId, request.id, 0L
            );
        }
        requireRequestMutable(existing, allowScheduled);
        request.id = existing.id;
        long expectedVersion = version(existing);
        if (mapper.updateAppointmentRequest(
                request, expectedVersion, allowScheduled
        ) != 1) {
            throw conflict(STATE_CHANGED_MESSAGE);
        }
        return new OfficialWarehouseAppointmentPreparedRequest(
                existing.ownerUserId, existing.id, expectedVersion + 1
        );
    }
    private void insert(AppointmentInsertRecord request) {
        try {
            if (mapper.insertAppointment(request) != 1) {
                throw conflict(STATE_CHANGED_MESSAGE);
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("该 ASN 已有有效约仓，请刷新后重试。");
        }
    }
    private void lockParent(Long ownerUserId, Long asnId) {
        if (!Objects.equals(asnId, mapper.lockAsnForAppointment(ownerUserId, asnId))) {
            throw new IllegalArgumentException("官方仓 ASN 不存在。");
        }
    }
    private void requireRequestMutable(AppointmentRecord existing, boolean allowScheduled) {
        if ("RUNNING".equals(existing.status)) {
            throw conflict("约仓正在执行中，不能覆盖请求参数。");
        }
        if ("SCHEDULED".equals(existing.status) && !allowScheduled) {
            throw conflict("该 ASN 已约仓成功；如需改约，请选择明确的日期和时段。");
        }
        if ("FAILED".equals(existing.status)
                && OfficialWarehouseAppointmentReconciliationPolicy
                .requiresReconciliation(existing.failureType)) {
            throw conflict("上次执行结果未知，请先与 Noon 对账并订正后再运行。");
        }
        if (!"PENDING".equals(existing.status)
                && !"FAILED".equals(existing.status)
                && !(allowScheduled && "SCHEDULED".equals(existing.status))) {
            throw conflict(STATE_CHANGED_MESSAGE);
        }
    }

    private OfficialWarehouseAppointmentRunClaim claimed(
            Long ownerUserId,
            Long appointmentId,
            long expectedVersion
    ) {
        AppointmentRecord current = current(ownerUserId, appointmentId);
        if (!"RUNNING".equals(current.status) || version(current) != expectedVersion) {
            throw conflict(STATE_CHANGED_MESSAGE);
        }
        return new OfficialWarehouseAppointmentRunClaim(current, expectedVersion);
    }

    private AppointmentRecord current(Long ownerUserId, Long appointmentId) {
        AppointmentRecord current = mapper.selectAppointment(ownerUserId, appointmentId);
        if (current == null) {
            throw new IllegalArgumentException("约仓记录不存在。");
        }
        return current;
    }

    private long version(AppointmentRecord appointment) {
        return appointment.executionVersion == null ? 0L : appointment.executionVersion;
    }

    private OfficialWarehouseAppointmentStateConflictException conflict(String message) {
        return new OfficialWarehouseAppointmentStateConflictException(message);
    }

}
