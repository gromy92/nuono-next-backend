package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementAutoInquiryProbeScopeMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcurementAutoInquiryProbeScopeTest {

    @Mock
    private ProcurementAutoInquiryProbeScopeMapper scopeMapper;
    @Mock
    private AliAiBulkInquiryReadAdapter readAdapter;
    @Mock
    private AliAiBulkInquiryResultParser resultParser;
    @Mock
    private AliAiBulkInquiryCreatePlanner createPlanner;
    @Mock
    private AliUnpaidOrderCreatePlanner unpaidOrderCreatePlanner;

    private LocalDbAliAiBulkInquiryReadService readService;
    private LocalDbAliAiBulkInquiryCreateService createService;
    private LocalDbAliUnpaidOrderCreateService unpaidOrderCreateService;

    @BeforeEach
    void setUp() {
        readService = new LocalDbAliAiBulkInquiryReadService(
                scopeMapper,
                readAdapter,
                resultParser,
                new ObjectMapper(),
                new Ali1688BrowserUrlPolicy()
        );
        createService = new LocalDbAliAiBulkInquiryCreateService(
                scopeMapper,
                createPlanner,
                new ObjectMapper()
        );
        unpaidOrderCreateService = new LocalDbAliUnpaidOrderCreateService(
                scopeMapper,
                unpaidOrderCreatePlanner,
                new ObjectMapper()
        );
    }

    @Test
    void shouldRejectForeignResultTaskBeforeReadingBrowser() {
        AliAiBulkInquiryResultProbeCommand command = new AliAiBulkInquiryResultProbeCommand();
        command.setTaskId(45001L);
        when(scopeMapper.selectOwnedAutoInquiryTask(307L, 45001L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> readService.probeResult(307L, 801L, command)
        );

        assertEquals("自动询价任务不存在或无权访问。", exception.getMessage());
        verifyNoInteractions(readAdapter, resultParser);
    }

    @Test
    void shouldRejectMissingTrustedActorBeforeTaskOrBrowserAccess() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> readService.probeResult(307L, null, new AliAiBulkInquiryResultProbeCommand())
        );

        assertEquals("缺少有效的采购业务身份。", exception.getMessage());
        verifyNoInteractions(scopeMapper, readAdapter, resultParser);
    }

    @Test
    void shouldRejectUntrustedSampleResultUrlBeforeParsingOrPersisting() {
        AliAiBulkInquiryResultProbeCommand command = new AliAiBulkInquiryResultProbeCommand();
        command.setSampleText("sample");
        command.setResultUrl("https://evil.test/private");
        command.setPersistResult(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> readService.probeResult(307L, 801L, command)
        );

        assertEquals("浏览器地址不在允许的 1688 HTTPS 页面范围内。", exception.getMessage());
        verifyNoInteractions(scopeMapper, readAdapter, resultParser);
    }

    @Test
    void shouldRejectForeignAliAiCreateTaskBeforePlanning() {
        AliAiBulkInquiryCreateProbeCommand command = new AliAiBulkInquiryCreateProbeCommand();
        command.setTaskId(45002L);
        when(scopeMapper.selectOwnedAutoInquiryTask(307L, 45002L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createService.probeCreate(307L, 801L, command)
        );

        assertEquals("自动询价任务不存在或无权访问。", exception.getMessage());
        verifyNoInteractions(createPlanner);
    }

    @Test
    void shouldRejectForeignUnpaidOrderTaskBeforePlanning() {
        AliUnpaidOrderCreateProbeCommand command = new AliUnpaidOrderCreateProbeCommand();
        command.setTaskId(45003L);
        when(scopeMapper.selectOwnedAutoInquiryTask(307L, 45003L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> unpaidOrderCreateService.probeCreate(307L, 801L, command)
        );

        assertEquals("自动询价任务不存在或无权访问。", exception.getMessage());
        verifyNoInteractions(unpaidOrderCreatePlanner);
    }

    @Test
    void shouldScopeEveryProbeTaskSelectAndUpdateByOwner() {
        Method selectMethod = method("selectOwnedAutoInquiryTask");
        assertTrue(sql(selectMethod.getAnnotation(Select.class).value())
                .contains("task.owner_user_id = #{ownerUserId}"));

        Arrays.stream(ProcurementAutoInquiryProbeScopeMapper.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("updateOwnedAutoInquiryTask"))
                .forEach(method -> assertTrue(
                        sql(method.getAnnotation(Update.class).value())
                                .contains("owner_user_id = #{ownerUserId}"),
                        method.getName()
                ));
    }

    private Method method(String name) {
        return Arrays.stream(ProcurementAutoInquiryProbeScopeMapper.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private String sql(String[] lines) {
        return String.join(" ", lines);
    }
}
