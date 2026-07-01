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
                AppointmentRecord.class
        );
        method.setAccessible(true);

        assertThat(method.invoke(null, 5, appointmentWithAttemptCount(null))).isEqualTo(10);
        assertThat(method.invoke(null, 5, appointmentWithAttemptCount(0))).isEqualTo(10);
        assertThat(method.invoke(null, 5, appointmentWithAttemptCount(1))).isEqualTo(20);
        assertThat(method.invoke(null, 5, appointmentWithAttemptCount(2))).isEqualTo(40);
    }

    private AppointmentRecord appointmentWithAttemptCount(Integer attemptCount) {
        AppointmentRecord record = new AppointmentRecord();
        record.attemptCount = attemptCount;
        return record;
    }
}
