package com.nuono.next.sales;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nuono.next.noon.NoonSessionGateway;
import org.junit.jupiter.api.Test;

class NoonSessionGatewaySalesReportSessionFactoryTest {

    @Test
    void backgroundSalesReportUsesThePersistedProjectCookieSession() {
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
    }
}
