package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class OfficialWarehouseAppointmentAuthRecoverySpringWiringTest {

    @Test
    void localDbAppointmentServiceWiresSharedProjectRecovery() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=local-db")
                .withBean(OfficialWarehouseMapper.class, () -> mock(OfficialWarehouseMapper.class))
                .withBean(NoonSessionGateway.class, () -> mock(NoonSessionGateway.class))
                .withBean(NoonSalesReportBindingResolver.class, () -> mock(NoonSalesReportBindingResolver.class))
                .withBean(NoonHttpCallLogService.class, () -> mock(NoonHttpCallLogService.class))
                .withBean(OfficialWarehouseNoonInboundClient.class,
                        () -> mock(OfficialWarehouseNoonInboundClient.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(NoonRiskBackoffGuard.class, NoonRiskBackoffGuard::disabled)
                .withBean(NoonPullFailurePolicy.class, NoonPullFailurePolicy::new)
                .withBean(NoonProjectAuthRecoveryQueue.class, () -> mock(NoonProjectAuthRecoveryQueue.class))
                .withBean(NoonPullProjectAuthGate.class, () -> mock(NoonPullProjectAuthGate.class))
                .withUserConfiguration(WiringConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OfficialWarehouseAppointmentAuthRecovery.class);
                    assertThat(context).hasSingleBean(LocalDbOfficialWarehouseService.class);
                });
    }

    @Configuration
    @Import({
            OfficialWarehouseAppointmentAuthRecovery.class,
            OfficialWarehouseAppointmentLifecycleModule.class,
            LocalDbOfficialWarehouseService.class
    })
    static class WiringConfig {
    }
}
