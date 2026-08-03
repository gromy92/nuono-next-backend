package com.nuono.next.officialwarehouse;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;

final class OfficialWarehouseAppointmentReconciliationImplementation {

    private final OfficialWarehouseMapper mapper;

    OfficialWarehouseAppointmentReconciliationImplementation(
            OfficialWarehouseMapper mapper
    ) {
        this.mapper = mapper;
    }

    OfficialWarehouseAppointmentReconcileOutcome reconcile(
            AppointmentInsertRecord seed,
            OfficialWarehouseAppointmentCorrection correction,
            boolean createIfMissing
    ) {
        lockParent(seed.ownerUserId, seed.asnId);
        AppointmentRecord current =
                mapper.selectActiveAppointmentByAsnForUpdate(seed.ownerUserId, seed.asnId);
        boolean inserted = false;
        if (current == null) {
            if (!createIfMissing) {
                return new OfficialWarehouseAppointmentReconcileOutcome(null, false);
            }
            seed.id = mapper.nextAppointmentId();
            seed.status = "PENDING";
            insert(seed);
            current = current(seed.ownerUserId, seed.id);
            inserted = true;
        }
        if ("RUNNING".equals(current.status)) {
            return new OfficialWarehouseAppointmentReconcileOutcome(current, false);
        }
        if (matches(current, correction)) {
            return new OfficialWarehouseAppointmentReconcileOutcome(current, inserted);
        }
        if (correct(current, correction, seed.operatorUserId) != 1) {
            return new OfficialWarehouseAppointmentReconcileOutcome(
                    current(seed.ownerUserId, current.id),
                    false
            );
        }
        return new OfficialWarehouseAppointmentReconcileOutcome(
                current(seed.ownerUserId, current.id),
                true
        );
    }

    int correct(
            AppointmentRecord current,
            OfficialWarehouseAppointmentCorrection correction,
            Long operatorUserId
    ) {
        return mapper.correctAppointment(
                current.ownerUserId,
                current.id,
                current.status,
                version(current),
                correction.status(),
                correction.appointmentDate(),
                correction.slotId(),
                correction.appointmentTime(),
                correction.gate(),
                correction.docks(),
                correction.failureType(),
                correction.errorStage(),
                correction.errorMessage(),
                operatorUserId
        );
    }

    private void insert(AppointmentInsertRecord seed) {
        try {
            if (mapper.insertAppointment(seed) != 1) {
                throw conflict();
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    private void lockParent(Long ownerUserId, Long asnId) {
        if (!Objects.equals(asnId, mapper.lockAsnForAppointment(ownerUserId, asnId))) {
            throw new IllegalArgumentException("官方仓 ASN 不存在。");
        }
    }

    private AppointmentRecord current(Long ownerUserId, Long appointmentId) {
        AppointmentRecord current = mapper.selectAppointment(ownerUserId, appointmentId);
        if (current == null) {
            throw new IllegalArgumentException("约仓记录不存在。");
        }
        return current;
    }

    private boolean matches(
            AppointmentRecord current,
            OfficialWarehouseAppointmentCorrection correction
    ) {
        return Objects.equals(current.status, correction.status())
                && desiredEquals(correction.appointmentDate(), current.appointmentDate)
                && desiredEquals(correction.slotId(), current.appointmentSlotId)
                && desiredEquals(correction.appointmentTime(), current.appointmentTime)
                && desiredEquals(correction.gate(), current.gate)
                && desiredEquals(correction.docks(), current.docks)
                && desiredEquals(correction.failureType(), current.failureType)
                && desiredEquals(correction.errorStage(), current.errorStage)
                && desiredEquals(correction.errorMessage(), current.errorMessage);
    }

    private boolean desiredEquals(Object desired, Object current) {
        return desired == null
                || Objects.equals(desired, current)
                || Objects.equals(String.valueOf(desired), String.valueOf(current));
    }

    private long version(AppointmentRecord current) {
        return current.executionVersion == null ? 0L : current.executionVersion;
    }

    private OfficialWarehouseAppointmentStateConflictException conflict() {
        return new OfficialWarehouseAppointmentStateConflictException(
                "该 ASN 已有有效约仓，请刷新后重试。"
        );
    }
}
