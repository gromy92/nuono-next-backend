package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductMasterAccessGuardTest {

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductManagementMapper productManagementMapper;

    private ProductMasterAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ProductMasterAccessGuard(storeSyncMapper, productManagementMapper);
    }

    @Test
    void shouldResolveOwnerFromAuthorizedStoreInsteadOfTrustingRequest() {
        when(storeSyncMapper.selectAccessibleOwnerUserIdForStore(10003L, "STR245027-NAE")).thenReturn(10002L);

        Long ownerUserId = guard.resolveOwnerUserId(
                new AuthenticatedSession(10003L, 3L, 2),
                10002L,
                "STR245027-NAE"
        );

        assertEquals(10002L, ownerUserId);
    }

    @Test
    void shouldRejectCrossOwnerRequestWhenStoreDoesNotAuthorizeRequestedOwner() {
        when(storeSyncMapper.selectAccessibleOwnerUserIdForStore(10003L, "STR245027-NAE")).thenReturn(10002L);

        assertThrows(
                ProductMasterAccessDeniedException.class,
                () -> guard.resolveOwnerUserId(
                        new AuthenticatedSession(10003L, 3L, 2),
                        99999L,
                        "STR245027-NAE"
                )
        );
    }

    @Test
    void shouldRejectSystemAdminInProductBusinessDomain() {
        assertThrows(
                ProductMasterAccessDeniedException.class,
                () -> guard.resolveOwnerUserId(
                        new AuthenticatedSession(10001L, 1L, 0),
                        10002L,
                        "STR245027-NAE"
                )
        );
    }

    @Test
    void shouldResolvePublishTaskOwnerThroughTaskStoreAccess() {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        when(productManagementMapper.selectProductPublishTaskById(64001L)).thenReturn(task);
        when(storeSyncMapper.selectAccessibleOwnerUserIdForStore(10003L, "STR245027-NAE")).thenReturn(10002L);

        Long ownerUserId = guard.resolvePublishTaskOwnerUserId(
                new AuthenticatedSession(10003L, 3L, 2),
                64001L,
                10002L
        );

        assertEquals(10002L, ownerUserId);
    }
}
