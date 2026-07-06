package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class OfficialWarehouseAppointmentSchedulerSqlTest {

    @Test
    void bufferUnderflowEofFromNoonCallIsRetryableAccessFailure() {
        String failureType = LocalDbOfficialWarehouseService.appointmentRetryFailureType(
                "NOON_CALL",
                "IllegalStateException",
                "请求 Noon 失败：BUFFER_UNDERFLOW with EOF, 1253 bytes non decrypted."
        );

        assertThat(failureType).isEqualTo("NOON_ACCESS_FAILURE");
        assertThat(LocalDbOfficialWarehouseService.isRetryableNoonCallFailure(failureType)).isTrue();
    }

    @Test
    void schedulerDueQueryOnlyConsumesPendingAppointments() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod("listDueAppointments", int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("status = 'PENDING'");
        assertThat(sql).doesNotContain("status IN ('PENDING', 'RUNNING', 'FAILED')");
        assertThat(sql).doesNotContain("'RUNNING'");
        assertThat(sql).doesNotContain("'FAILED'");
    }

    @Test
    void staleRunningRecoveryOnlyRequeuesNoCapacityAppointments() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod(
                "markStaleNoCapacityAppointmentsPending",
                int.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("status = 'RUNNING'");
        assertThat(sql).contains("failure_type = 'NO_CAPACITY'");
        assertThat(sql).contains("gmt_updated <= DATE_SUB(NOW(), INTERVAL #{staleMinutes} MINUTE)");
        assertThat(sql).contains("status = 'PENDING'");
        assertThat(sql).contains("next_attempt_at = NOW()");
        assertThat(sql).doesNotContain("failure_type IN");
        assertThat(sql).doesNotContain("status IN ('RUNNING'");
    }

    @Test
    void schedulerRunsEveryFiveSecondsByDefault() throws Exception {
        Method method = LocalDbOfficialWarehouseService.class.getMethod("runAppointmentScheduler");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.initialDelayString())
                .isEqualTo("${nuono.official-warehouse.appointment.scheduler.initial-delay-ms:5000}");
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${nuono.official-warehouse.appointment.scheduler.fixed-delay-ms:5000}");
    }

    @Test
    void pendingRetryUsesSecondPrecisionForBackoff() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod(
                "markAppointmentPendingRetry",
                Long.class,
                int.class,
                String.class,
                String.class,
                String.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("INTERVAL #{retrySeconds} SECOND");
        assertThat(sql).doesNotContain("INTERVAL #{retryMinutes} MINUTE");
    }

    @Test
    void successfulAppointmentResetsRetryAttemptCount() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod(
                "markAppointmentScheduled",
                Long.class,
                java.time.LocalDate.class,
                Integer.class,
                String.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("attempt_count = 0");
    }

    @Test
    void retryBackoffDoublesPerAppointmentFailure() throws Exception {
        Method method = LocalDbOfficialWarehouseService.class.getDeclaredMethod(
                "nextAppointmentRetrySeconds",
                int.class,
                AppointmentRecord.class,
                String.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        assertThat(method.invoke(null, 5, appointmentWithAttemptCount(null), "SCHEDULE", "SCHEDULE_APPOINTMENT", null))
                .isEqualTo(10);
        assertThat(method.invoke(null, 5, appointmentWithAttemptCount(0), "SCHEDULE", "SCHEDULE_APPOINTMENT", null))
                .isEqualTo(10);
        assertThat(method.invoke(null, 5, appointmentWithAttemptCount(1), "SCHEDULE", "SCHEDULE_APPOINTMENT", null))
                .isEqualTo(20);
        assertThat(method.invoke(null, 5, appointmentWithAttemptCount(2), "SCHEDULE", "SCHEDULE_APPOINTMENT", null))
                .isEqualTo(40);
    }

    @Test
    void noCapacityRetryIsImmediatelyEligibleEvenAfterManyAttempts() throws Exception {
        Method method = LocalDbOfficialWarehouseService.class.getDeclaredMethod(
                "nextAppointmentRetrySeconds",
                int.class,
                AppointmentRecord.class,
                String.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        assertThat(method.invoke(
                null,
                5,
                appointmentWithAttemptCount(32),
                "SCHEDULE",
                "NO_CAPACITY",
                "没有匹配的 Noon 可约仓日期或时段。"
        ))
                .isEqualTo(0);
    }

    @Test
    void noonAccessFailureRetryIsCappedAndClassified() throws Exception {
        Method retryMethod = LocalDbOfficialWarehouseService.class.getDeclaredMethod(
                "nextAppointmentRetrySeconds",
                int.class,
                AppointmentRecord.class,
                String.class,
                String.class,
                String.class
        );
        retryMethod.setAccessible(true);
        Method failureTypeMethod = LocalDbOfficialWarehouseService.class.getDeclaredMethod(
                "appointmentRetryFailureType",
                String.class,
                String.class,
                String.class
        );
        failureTypeMethod.setAccessible(true);

        assertThat(failureTypeMethod.invoke(null, "NOON_CALL", "IllegalStateException", "HTTP 407 empty response"))
                .isEqualTo("NOON_ACCESS_BLOCKED");
        assertThat(LocalDbOfficialWarehouseService.isRetryableNoonCallFailure("NOON_ACCESS_BLOCKED"))
                .isTrue();
        assertThat(retryMethod.invoke(
                null,
                5,
                appointmentWithAttemptCount(32),
                "NOON_CALL",
                "NOON_ACCESS_BLOCKED",
                "HTTP 407 empty response"
        ))
                .isEqualTo(1800);
    }

    @Test
    void transientHttpServerErrorIsRetryableNoonAccessFailure() throws Exception {
        Method failureTypeMethod = LocalDbOfficialWarehouseService.class.getDeclaredMethod(
                "appointmentRetryFailureType",
                String.class,
                String.class,
                String.class
        );
        failureTypeMethod.setAccessible(true);

        assertThat(failureTypeMethod.invoke(null, "NOON_CALL", "IllegalStateException", "HTTP 500 empty response"))
                .isEqualTo("NOON_ACCESS_FAILURE");
        assertThat(LocalDbOfficialWarehouseService.isRetryableNoonCallFailure("NOON_ACCESS_FAILURE"))
                .isTrue();
    }

    @Test
    void genericNoonCallExceptionIsNotRetriedAsAccessFailure() throws Exception {
        Method failureTypeMethod = LocalDbOfficialWarehouseService.class.getDeclaredMethod(
                "appointmentRetryFailureType",
                String.class,
                String.class,
                String.class
        );
        failureTypeMethod.setAccessible(true);

        assertThat(failureTypeMethod.invoke(null, "NOON_CALL", "IllegalArgumentException", "缺少 Noon 绑定。"))
                .isEqualTo("IllegalArgumentException");
        assertThat(LocalDbOfficialWarehouseService.isRetryableNoonCallFailure("IllegalArgumentException"))
                .isFalse();
    }

    private AppointmentRecord appointmentWithAttemptCount(Integer attemptCount) {
        AppointmentRecord record = new AppointmentRecord();
        record.attemptCount = attemptCount;
        return record;
    }
}
