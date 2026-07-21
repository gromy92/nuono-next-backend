package com.nuono.next.permission.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BusinessAccessRequestCacheTest {

    @Mock
    private AuthSessionTokenService sessionTokenService;

    @Mock
    private BusinessAccessMapper accessMapper;

    @Test
    void reusesOneSuccessfulContextWithinTheSameRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        stubActiveOperator(request);
        BusinessAccessResolver resolver = resolver();

        BusinessAccessContext first = resolver.resolve(request);
        BusinessAccessContext second = resolver.resolve(request);

        assertThat(second).isSameAs(first);
        verify(sessionTokenService).requireSession(request);
        verify(accessMapper).selectUserAccess(357L);
        verify(accessMapper).selectGrantedMenuPaths(357L);
        verify(accessMapper).selectStoreScope(357L);
    }

    @Test
    void doesNotShareAContextAcrossRequests() {
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(any()))
                .thenReturn(new AuthenticatedSession(357L, 3L, 3));
        stubActiveOperatorRows();
        BusinessAccessResolver resolver = resolver();

        BusinessAccessContext first = resolver.resolve(firstRequest);
        BusinessAccessContext second = resolver.resolve(secondRequest);

        assertThat(second).isNotSameAs(first);
        verify(sessionTokenService, times(2)).requireSession(any());
        verify(accessMapper, times(2)).selectUserAccess(357L);
        verify(accessMapper, times(2)).selectGrantedMenuPaths(357L);
        verify(accessMapper, times(2)).selectStoreScope(357L);
    }

    @Test
    void doesNotCacheFailedContextConstruction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessUserAccessRow disabled = activeOperator();
        disabled.setStatus(0);
        when(sessionTokenService.requireSession(request))
                .thenReturn(new AuthenticatedSession(357L, 3L, 3));
        when(accessMapper.selectUserAccess(357L)).thenReturn(disabled, activeOperator());
        when(accessMapper.selectGrantedMenuPaths(357L)).thenReturn(List.of("/purchase/in-transit-goods"));
        when(accessMapper.selectStoreScope(357L)).thenReturn(List.of(storeScope()));
        BusinessAccessResolver resolver = resolver();

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(resolver.resolve(request).getSessionUserId()).isEqualTo(357L);

        verify(sessionTokenService, times(2)).requireSession(request);
        verify(accessMapper, times(2)).selectUserAccess(357L);
        verify(accessMapper).selectGrantedMenuPaths(357L);
        verify(accessMapper).selectStoreScope(357L);
    }

    @Test
    void rechecksAuthorizationWhenTheContextIsCached() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        stubActiveOperator(request);
        BusinessAccessResolver resolver = resolver();

        assertThat(resolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .isNotNull();
        assertThatThrownBy(() -> resolver.requireBusinessContext(request, BusinessCapability.PRODUCT_MASTER))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(resolver.requireStoreAccess(request, BusinessCapability.IN_TRANSIT_GOODS, "STR-A"))
                .isNotNull();
        assertThatThrownBy(() -> resolver.requireStoreAccess(
                request,
                BusinessCapability.IN_TRANSIT_GOODS,
                "STR-B"
        )).isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(accessMapper).selectUserAccess(357L);
        verify(accessMapper).selectGrantedMenuPaths(357L);
        verify(accessMapper).selectStoreScope(357L);
    }

    private BusinessAccessResolver resolver() {
        return new BusinessAccessResolver(sessionTokenService, accessMapper, new BusinessAccessGuard());
    }

    private void stubActiveOperator(MockHttpServletRequest request) {
        when(sessionTokenService.requireSession(request))
                .thenReturn(new AuthenticatedSession(357L, 3L, 3));
        stubActiveOperatorRows();
    }

    private void stubActiveOperatorRows() {
        when(accessMapper.selectUserAccess(357L)).thenReturn(activeOperator());
        when(accessMapper.selectGrantedMenuPaths(357L)).thenReturn(List.of("/purchase/in-transit-goods"));
        when(accessMapper.selectStoreScope(357L)).thenReturn(List.of(storeScope()));
    }

    private BusinessUserAccessRow activeOperator() {
        BusinessUserAccessRow user = new BusinessUserAccessRow();
        user.setUserId(357L);
        user.setCreatedBy(307L);
        user.setAccountType("internal");
        user.setRoleId(3L);
        user.setRoleName("采购");
        user.setRoleLevel(3);
        user.setStatus(1);
        return user;
    }

    private BusinessStoreScopeRow storeScope() {
        BusinessStoreScopeRow row = new BusinessStoreScopeRow();
        row.setOwnerUserId(307L);
        row.setStoreCode("STR-A");
        return row;
    }
}
