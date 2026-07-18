package com.nuono.next.permission.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BusinessStoreAccessResolverTest {

    @Mock
    private AuthSessionTokenService sessionTokenService;

    @Mock
    private BusinessAccessMapper accessMapper;

    @Test
    void mappedStoreOwnerWinsAndStoreCodeIsCanonical() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        stubAccess(
                request,
                operator(357L, 307L),
                List.of(store(307L, "STR-A"), store(408L, "str-b")),
                List.of("/api/sku/manage")
        );

        BusinessStoreAccess access = resolver().requireStoreAccessScope(
                request,
                BusinessCapability.PRODUCT_MASTER,
                " str-b "
        );

        assertThat(access.getOwnerUserId()).isEqualTo(408L);
        assertThat(access.getStoreCode()).isEqualTo("STR-B");
        assertThat(access.getStoreCode())
                .isEqualTo(BusinessAccessContext.normalizeStoreCode(" str-b "));
    }

    @Test
    void nullStoreOwnerFallsBackToBusinessOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        stubAccess(
                request,
                operator(357L, 307L),
                List.of(store(null, "STR-A")),
                List.of("/api/sku/manage")
        );

        BusinessStoreAccess access = resolver().requireStoreAccessScope(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR-A"
        );

        assertThat(access.getOwnerUserId()).isEqualTo(307L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void nonPositiveMappedOwnerFailsClosedWithoutBusinessOwnerFallback(long mappedOwnerUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        stubAccess(
                request,
                boss(307L),
                List.of(store(mappedOwnerUserId, "STR-A")),
                List.of("/api/sku/manage")
        );

        assertOwnerScopeDenied(() -> resolver().requireStoreAccessScope(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR-A"
        ));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void nonPositiveBusinessOwnerFallbackFailsClosed(long businessOwnerUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        stubAccess(
                request,
                operator(357L, businessOwnerUserId),
                List.of(store(null, "STR-A")),
                List.of("/api/sku/manage")
        );

        assertOwnerScopeDenied(() -> resolver().requireStoreAccessScope(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR-A"
        ));
    }

    @Test
    void missingFinalOwnerFailsClosed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        stubAccess(
                request,
                operator(357L, null),
                List.of(store(null, "STR-A")),
                List.of("/api/sku/manage")
        );

        assertOwnerScopeDenied(() -> resolver().requireStoreAccessScope(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR-A"
        ));
    }

    @Test
    void capabilityAndStoreGuardsStillRunForEveryTypedResolution() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        stubAccess(
                request,
                operator(357L, 307L),
                List.of(store(307L, "STR-A")),
                List.of("/api/sku/manage")
        );
        BusinessAccessResolver resolver = resolver();

        assertThat(resolver.requireStoreAccessScope(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR-A"
        )).isNotNull();
        assertThatThrownBy(() -> resolver.requireStoreAccessScope(
                request,
                BusinessCapability.PROCUREMENT,
                "STR-A"
        )).isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> resolver.requireStoreAccessScope(
                request,
                BusinessCapability.PRODUCT_MASTER,
                "STR-B"
        )).isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(sessionTokenService).requireSession(request);
        verify(accessMapper).selectUserAccess(357L);
        verify(accessMapper).selectGrantedMenuPaths(357L);
        verify(accessMapper).selectStoreScope(357L);
    }

    @Test
    void blankStoreKeepsExistingForbiddenContract() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        stubAccess(
                request,
                operator(357L, 307L),
                List.of(store(307L, "STR-A")),
                List.of("/api/sku/manage")
        );

        assertThatThrownBy(() -> resolver().requireStoreAccessScope(
                request,
                BusinessCapability.PRODUCT_MASTER,
                " "
        )).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(error.getReason()).isEqualTo("当前账号不能操作该店铺。");
        });
    }

    @Test
    void sessionAndMapperFailuresKeepTheirExistingStatuses() {
        MockHttpServletRequest noSessionRequest = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(noSessionRequest))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_REQUIRED"));

        assertThatThrownBy(() -> resolver().requireStoreAccessScope(
                noSessionRequest,
                BusinessCapability.PRODUCT_MASTER,
                "STR-A"
        )).isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(accessMapper);

        MockHttpServletRequest noMapperRequest = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(noMapperRequest))
                .thenReturn(new AuthenticatedSession(357L, 3L, 3));
        BusinessAccessResolver noMapper = new BusinessAccessResolver(
                sessionTokenService,
                (BusinessAccessMapper) null,
                new BusinessAccessGuard()
        );

        assertThatThrownBy(() -> noMapper.requireStoreAccessScope(
                noMapperRequest,
                BusinessCapability.PRODUCT_MASTER,
                "STR-A"
        )).isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(sessionTokenService, times(2)).requireSession(org.mockito.ArgumentMatchers.any(HttpServletRequest.class));
    }

    private BusinessAccessResolver resolver() {
        return new BusinessAccessResolver(sessionTokenService, accessMapper, new BusinessAccessGuard());
    }

    private void stubAccess(
            MockHttpServletRequest request,
            BusinessUserAccessRow user,
            List<BusinessStoreScopeRow> stores,
            List<String> menuPaths
    ) {
        when(sessionTokenService.requireSession(request))
                .thenReturn(new AuthenticatedSession(user.getUserId(), user.getRoleId(), user.getRoleLevel()));
        when(accessMapper.selectUserAccess(user.getUserId())).thenReturn(user);
        when(accessMapper.selectGrantedMenuPaths(user.getUserId())).thenReturn(menuPaths);
        when(accessMapper.selectStoreScope(user.getUserId())).thenReturn(stores);
    }

    private BusinessUserAccessRow operator(Long userId, Long createdBy) {
        BusinessUserAccessRow user = baseUser(userId, 3L, "运营", 3);
        user.setCreatedBy(createdBy);
        return user;
    }

    private BusinessUserAccessRow boss(Long userId) {
        return baseUser(userId, 2L, "老板", 1);
    }

    private BusinessUserAccessRow baseUser(Long userId, Long roleId, String roleName, int roleLevel) {
        BusinessUserAccessRow user = new BusinessUserAccessRow();
        user.setUserId(userId);
        user.setAccountType("internal");
        user.setRoleId(roleId);
        user.setRoleName(roleName);
        user.setRoleLevel(roleLevel);
        user.setStatus(1);
        return user;
    }

    private BusinessStoreScopeRow store(Long ownerUserId, String storeCode) {
        BusinessStoreScopeRow row = new BusinessStoreScopeRow();
        row.setOwnerUserId(ownerUserId);
        row.setStoreCode(storeCode);
        return row;
    }

    private void assertOwnerScopeDenied(ThrowingAction action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getReason()).isEqualTo("BUSINESS_STORE_OWNER_SCOPE_REQUIRED");
                });
    }

    private interface ThrowingAction {
        void run();
    }
}
