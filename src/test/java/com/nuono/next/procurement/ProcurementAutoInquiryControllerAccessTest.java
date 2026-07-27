package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.permission.access.RequiredBusinessAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProcurementAutoInquiryControllerAccessTest {

    @Mock
    private ObjectProvider<LocalDbProcurementAutoInquiryService> autoInquiryServiceProvider;
    @Mock
    private ObjectProvider<LocalDbAliAiBulkInquiryReadService> readServiceProvider;
    @Mock
    private ObjectProvider<LocalDbAliAiBulkInquiryCreateService> createServiceProvider;
    @Mock
    private ObjectProvider<LocalDbAliAiBulkInquiryCreatePageProbeService> pageProbeServiceProvider;
    @Mock
    private ObjectProvider<LocalDbAliUnpaidOrderCreateService> unpaidOrderServiceProvider;
    @Mock
    private LocalDbProcurementAutoInquiryService autoInquiryService;
    @Mock
    private LocalDbAliAiBulkInquiryReadService readService;
    @Mock
    private LocalDbAliAiBulkInquiryCreateService createService;
    @Mock
    private LocalDbAliUnpaidOrderCreateService unpaidOrderService;
    @Mock
    private BusinessAccessResolver accessResolver;

    private ProcurementAutoInquiryController controller;

    @BeforeEach
    void setUp() {
        controller = new ProcurementAutoInquiryController(
                autoInquiryServiceProvider,
                readServiceProvider,
                createServiceProvider,
                pageProbeServiceProvider,
                unpaidOrderServiceProvider,
                accessResolver
        );
    }

    @Test
    void shouldOverrideOwnerAndOperatorBeforeStartingAutoInquiry() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(801L)
                .businessOwnerUserId(307L)
                .build();
        ProcurementAutoInquiryStartCommand command = new ProcurementAutoInquiryStartCommand();
        command.setOwnerUserId(999L);
        command.setOperatorUserId(666L);
        when(accessResolver.requireOwnerUserId(context, 999L)).thenReturn(307L);
        when(autoInquiryServiceProvider.getIfAvailable()).thenReturn(autoInquiryService);

        controller.startAutoInquiry(command, context);

        assertEquals(307L, command.getOwnerUserId());
        assertEquals(801L, command.getOperatorUserId());
        verify(autoInquiryService).startAutoInquiry(307L, 801L, command);
    }

    @Test
    void shouldOverrideProbeIdentityAndUseCanonicalOwnerWhenOwnerIsOmitted() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(801L)
                .businessOwnerUserId(307L)
                .build();
        AliAiBulkInquiryResultProbeCommand command = new AliAiBulkInquiryResultProbeCommand();
        command.setOperatorUserId(666L);
        when(accessResolver.requireOwnerUserId(context, null)).thenReturn(307L);
        when(readServiceProvider.getIfAvailable()).thenReturn(readService);

        controller.probeAliAiBulkInquiryResult(command, context);

        assertEquals(307L, command.getOwnerUserId());
        assertEquals(801L, command.getOperatorUserId());
        verify(readService).probeResult(307L, 801L, command);
    }

    @Test
    void shouldAuthorizeSelectedOwnerAndOverrideOperatorForEveryTaskProbe() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(801L)
                .businessOwnerUserId(307L)
                .build();
        AliAiBulkInquiryResultProbeCommand resultCommand = new AliAiBulkInquiryResultProbeCommand();
        AliAiBulkInquiryCreateProbeCommand createCommand = new AliAiBulkInquiryCreateProbeCommand();
        AliUnpaidOrderCreateProbeCommand unpaidCommand = new AliUnpaidOrderCreateProbeCommand();
        List.of(resultCommand, createCommand, unpaidCommand).forEach(command -> {
            command.setOwnerUserId(408L);
            command.setOperatorUserId(666L);
        });
        when(accessResolver.requireOwnerUserId(context, 408L)).thenReturn(408L);
        when(readServiceProvider.getIfAvailable()).thenReturn(readService);
        when(createServiceProvider.getIfAvailable()).thenReturn(createService);
        when(unpaidOrderServiceProvider.getIfAvailable()).thenReturn(unpaidOrderService);

        controller.probeAliAiBulkInquiryResult(resultCommand, context);
        controller.probeAliAiBulkInquiryCreate(createCommand, context);
        controller.probeAliUnpaidOrderCreate(unpaidCommand, context);

        List.of(resultCommand, createCommand, unpaidCommand).forEach(command -> {
            assertEquals(408L, command.getOwnerUserId());
            assertEquals(801L, command.getOperatorUserId());
        });
        verify(readService).probeResult(408L, 801L, resultCommand);
        verify(createService).probeCreate(408L, 801L, createCommand);
        verify(unpaidOrderService).probeCreate(408L, 801L, unpaidCommand);
    }

    @Test
    void shouldRejectUnauthorizedProbeOwnerBeforeResolvingBusinessService() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(801L)
                .businessOwnerUserId(307L)
                .build();
        AliAiBulkInquiryResultProbeCommand command = new AliAiBulkInquiryResultProbeCommand();
        command.setOwnerUserId(509L);
        when(accessResolver.requireOwnerUserId(context, 509L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.probeAliAiBulkInquiryResult(command, context)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(readServiceProvider, readService);
    }

    @Test
    void shouldKeepEmptyProbeBodiesOnTheCanonicalOwnerCompatibilityPath() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(801L)
                .businessOwnerUserId(307L)
                .build();
        when(accessResolver.requireOwnerUserId(context, null)).thenReturn(307L);
        when(readServiceProvider.getIfAvailable()).thenReturn(readService);
        when(createServiceProvider.getIfAvailable()).thenReturn(createService);
        when(unpaidOrderServiceProvider.getIfAvailable()).thenReturn(unpaidOrderService);

        controller.probeAliAiBulkInquiryResult(null, context);
        controller.probeAliAiBulkInquiryCreate(null, context);
        controller.probeAliUnpaidOrderCreate(null, context);

        verify(readService).probeResult(307L, 801L, null);
        verify(createService).probeCreate(307L, 801L, null);
        verify(unpaidOrderService).probeCreate(307L, 801L, null);
    }

    @Test
    void inheritedProbeIdentityFieldsShouldRemainJacksonCompatible() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = "{\"ownerUserId\":408,\"operatorUserId\":666}";
        List<Class<? extends ProcurementAutoInquiryProbeCommand>> commandTypes = List.of(
                AliAiBulkInquiryResultProbeCommand.class,
                AliAiBulkInquiryCreateProbeCommand.class,
                AliUnpaidOrderCreateProbeCommand.class
        );

        for (Class<? extends ProcurementAutoInquiryProbeCommand> commandType : commandTypes) {
            ProcurementAutoInquiryProbeCommand command = objectMapper.readValue(json, commandType);
            assertEquals(408L, command.getOwnerUserId(), commandType.getSimpleName());
            assertEquals(666L, command.getOperatorUserId(), commandType.getSimpleName());
        }
    }

    @Test
    void shouldDeclareOneProcurementAccessContextOnEveryEndpoint() {
        List<Method> endpoints = Arrays.stream(ProcurementAutoInquiryController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class))
                .collect(Collectors.toList());

        assertEquals(6, endpoints.size());
        endpoints.forEach(this::assertProcurementContext);
    }

    @Test
    void splitControllerShouldPreserveEveryAutoInquiryHttpPath() {
        RequestMapping baseMapping = ProcurementAutoInquiryController.class.getAnnotation(RequestMapping.class);
        assertEquals(List.of("/api/procurement/auto-inquiry"), List.of(baseMapping.value()));

        Set<String> paths = Arrays.stream(ProcurementAutoInquiryController.class.getDeclaredMethods())
                .map(method -> {
                    GetMapping get = method.getAnnotation(GetMapping.class);
                    PostMapping post = method.getAnnotation(PostMapping.class);
                    return get == null ? post == null ? null : post.value()[0] : get.value()[0];
                })
                .filter(path -> path != null)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "/workbench",
                "/start",
                "/ali-ai/result/probe",
                "/ali-ai/create/probe",
                "/ali-ai/create/page-probe",
                "/ali-unpaid-order/create/probe"
        ), paths);
    }

    private void assertProcurementContext(Method method) {
        List<Parameter> contextParameters = Arrays.stream(method.getParameters())
                .filter(parameter -> parameter.getType() == BusinessAccessContext.class)
                .collect(Collectors.toList());
        assertEquals(1, contextParameters.size(), method.getName());
        RequiredBusinessAccess access = contextParameters.get(0).getAnnotation(RequiredBusinessAccess.class);
        assertNotNull(access, method.getName());
        assertEquals(BusinessCapability.PROCUREMENT, access.capability(), method.getName());
    }
}
