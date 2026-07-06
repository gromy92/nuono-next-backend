package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@ExtendWith(MockitoExtension.class)
class LogisticsAutoSyncControllerTest {

    @Mock
    private LogisticsAutoSyncAccountService accountService;
    @Mock
    private BusinessAccessResolver businessAccessResolver;
    @Mock
    private HttpServletRequest request;

    private LogisticsAutoSyncController controller;

    @BeforeEach
    void setUp() {
        controller = new LogisticsAutoSyncController(accountService, businessAccessResolver);
    }

    @Test
    void accountsReturnsMaskedAccountsForCurrentBusinessOwner() {
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context());
        LogisticsAutoSyncAccountView view = new LogisticsAutoSyncAccountView();
        view.setAccountId(180001L);
        view.setLoginAccountMasked("us***om");
        when(accountService.list(10002L)).thenReturn(List.of(view));

        List<LogisticsAutoSyncAccountView> result = controller.accounts(request);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getAccountId()).isEqualTo(180001L);
            assertThat(item.getLoginAccountMasked()).isEqualTo("us***om");
        });
    }

    @Test
    void saveAccountUsesCurrentBusinessContextAndOnlySavesConfig() {
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context());
        LogisticsAutoSyncAccountCommand command = new LogisticsAutoSyncAccountCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setSourceSystem("CHIC");
        command.setForwarderName("启客");
        command.setLoginAccount("login");
        command.setPassword("password");
        LogisticsAutoSyncAccountView saved = new LogisticsAutoSyncAccountView();
        saved.setAccountId(180001L);
        ArgumentCaptor<LogisticsAutoSyncAccountCommand> commandCaptor =
                ArgumentCaptor.forClass(LogisticsAutoSyncAccountCommand.class);
        ArgumentCaptor<Long> actorCaptor = ArgumentCaptor.forClass(Long.class);
        when(accountService.save(commandCaptor.capture(), actorCaptor.capture())).thenReturn(saved);

        LogisticsAutoSyncAccountView result = controller.saveAccount(command, request);

        assertThat(result.getAccountId()).isEqualTo(180001L);
        assertThat(commandCaptor.getValue().getOwnerUserId()).isEqualTo(10002L);
        assertThat(commandCaptor.getValue().getOperatorUserId()).isEqualTo(90001L);
        assertThat(actorCaptor.getValue()).isEqualTo(90001L);
        assertThat(fieldNames(LogisticsAutoSyncController.class))
                .containsExactlyInAnyOrder("accountService", "businessAccessResolver");
    }

    @Test
    void controllerDoesNotExposeManualRunRetryOrTriggerEndpoint() {
        List<String> paths = endpointPaths();

        assertThat(paths).allSatisfy(path -> assertThat(path)
                .doesNotContain("/run")
                .doesNotContain("/sync-now")
                .doesNotContain("/retry")
                .doesNotContain("/trigger"));
    }

    private List<String> endpointPaths() {
        RequestMapping root = LogisticsAutoSyncController.class.getAnnotation(RequestMapping.class);
        String base = root == null || root.value().length == 0 ? "" : root.value()[0];
        List<String> paths = new ArrayList<>();
        for (Method method : LogisticsAutoSyncController.class.getDeclaredMethods()) {
            GetMapping get = method.getAnnotation(GetMapping.class);
            if (get != null) {
                for (String value : get.value()) {
                    paths.add(base + value);
                }
            }
            PostMapping post = method.getAnnotation(PostMapping.class);
            if (post != null) {
                for (String value : post.value()) {
                    paths.add(base + value);
                }
            }
        }
        return paths;
    }

    private static Set<String> fieldNames(Class<?> type) {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            names.add(field.getName());
        }
        return names;
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .menuPaths(Set.of("/purchase/in-transit-goods"))
                .build();
    }
}
