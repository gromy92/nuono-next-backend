package com.nuono.next.noonpull;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noon.NoonSessionGateway;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayPullSessionFactoryTest {

    @Test
    void shouldPreferStorePasswordBeforeConfiguredMerchantEmailLogin() {
        NoonSessionGateway noonSessionGateway = org.mockito.Mockito.mock(NoonSessionGateway.class);
        when(noonSessionGateway.hasConfiguredMerchantEmailLogin()).thenReturn(true);
        NoonSessionGatewayPullSessionFactory factory = new NoonSessionGatewayPullSessionFactory(noonSessionGateway);

        factory.login(new NoonPullStoreBinding(
                307L,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "108065",
                "nuonuo1@p108065.idp.noon.partners",
                "legacy-password",
                null,
                "cookie=value"
        ));

        verify(noonSessionGateway).login(
                307L,
                "nuonuo1@p108065.idp.noon.partners",
                "legacy-password",
                "cookie=value",
                "PRJ108065",
                "STR108065-NSA"
        );
        verify(noonSessionGateway, never()).loginWithConfiguredEmailAuthCode(
                307L,
                "cookie=value",
                "PRJ108065",
                "STR108065-NSA"
        );
    }
}
