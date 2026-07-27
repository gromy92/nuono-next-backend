package com.nuono.next.permission.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.nuono.next.auth.AuthSessionTokenService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BusinessOwnerAccessTest {

    private final BusinessAccessContext context = BusinessAccessContext.builder()
            .sessionUserId(90001L)
            .businessOwnerUserId(307L)
            .accountType(BusinessAccountType.OPERATOR)
            .storeOwnerUserIds(Map.of("STR-A", 307L, "STR-B", 408L))
            .build();

    @Test
    void contextRecognizesEveryOwnerReachableThroughItsStoreScope() {
        assertThat(context.canAccessOwner(307L)).isTrue();
        assertThat(context.canAccessOwner(408L)).isTrue();
        assertThat(context.canAccessOwner(509L)).isFalse();
    }

    @Test
    void guardDefaultsToCanonicalOwnerAndAllowsAnExplicitAccessibleOwner() {
        BusinessAccessGuard guard = new BusinessAccessGuard();

        assertThat(guard.requireOwnerUserId(context, null)).isEqualTo(307L);
        assertThat(guard.requireOwnerUserId(context, 408L)).isEqualTo(408L);
    }

    @Test
    void httpResolverRejectsAnOwnerOutsideTheAuthorizedContext() {
        BusinessAccessResolver resolver = new BusinessAccessResolver(
                mock(AuthSessionTokenService.class),
                mock(BusinessAccessMapper.class),
                new BusinessAccessGuard()
        );

        assertThatThrownBy(() -> resolver.requireOwnerUserId(context, 509L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN)
                );
    }
}
