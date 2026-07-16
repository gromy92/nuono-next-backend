package com.nuono.next.sales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nuono.next.noon.NoonSessionGateway;
import org.junit.jupiter.api.Test;

class NoonSessionGatewaySalesReportSessionFactoryTest {

    @Test
    void backgroundSalesReportMustNotUseEmailOtpWhenCookieNeedsAuthentication() {
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonSessionGatewaySalesReportSessionFactory factory = new NoonSessionGatewaySalesReportSessionFactory(gateway);
        NoonSalesReportBinding binding = new NoonSalesReportBinding(
                308L,
                50023L,
                "PRJ313934",
                "STR313934-NAE",
                "AE",
                "313934",
                "merchant@example.com",
                "legacy-password",
                "imap-secret",
                "sid=expired"
        );

        factory.login(binding);

        verify(gateway).loginWithPersistedCookie(
                308L,
                "merchant@example.com",
                "sid=expired",
                "PRJ313934",
                "STR313934-NAE"
        );
        verify(gateway, never()).login(any(), any(), any(), any(), any(), any());
    }
}
