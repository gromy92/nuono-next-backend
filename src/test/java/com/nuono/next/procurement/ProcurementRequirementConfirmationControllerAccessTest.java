package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementRequirementConfirmationMapper;
import com.nuono.next.permission.access.BusinessAccountType;
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
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@ExtendWith(MockitoExtension.class)
class ProcurementRequirementConfirmationControllerAccessTest {

    @Mock
    private ObjectProvider<LocalDbProcurementRequirementConfirmationService> requirementServiceProvider;

    @Mock
    private ObjectProvider<LocalDbProcurementCandidatePoolService> poolServiceProvider;

    @Mock
    private LocalDbProcurementRequirementConfirmationService requirementService;

    @Mock
    private LocalDbProcurementCandidatePoolService poolService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private ProcurementRequirementConfirmationController controller;

    @BeforeEach
    void setUp() {
        controller = new ProcurementRequirementConfirmationController(
                requirementServiceProvider,
                poolServiceProvider,
                businessAccessResolver
        );
    }

    @Test
    void shouldUseAuthorizedOwnerForDemandReads() {
        BusinessAccessContext context = context();
        when(businessAccessResolver.requireOwnerUserId(context, 99999L)).thenReturn(10002L);
        when(requirementServiceProvider.getIfAvailable()).thenReturn(requirementService);

        controller.demands(99999L, "PENDING", "paper", 2, 20, context);
        controller.demand(41101L, 99999L, context);

        verify(businessAccessResolver, times(2)).requireOwnerUserId(context, 99999L);
        verify(requirementService).listDemands(10002L, "PENDING", "paper", 2, 20);
        verify(requirementService).getDemandDetail(41101L, 10002L);
    }

    @Test
    void shouldOverwriteForgedIdentityForEveryWriteEndpoint() {
        BusinessAccessContext context = context();
        var initialize = new ProcurementRequirementConfirmationCommands.InitializePoolCommand();
        var remove = new ProcurementRequirementConfirmationCommands.RemovePoolItemCommand();
        var add = new ProcurementRequirementConfirmationCommands.AddPoolCandidateCommand();
        var finish = new ProcurementRequirementConfirmationCommands.FinishPoolInquiryCommand();
        var reply = new ProcurementRequirementConfirmationCommands.RecordPoolItemReplyCommand();
        var advance = new ProcurementRequirementConfirmationCommands.AdvancePoolItemFollowUpCommand();
        var noReply = new ProcurementRequirementConfirmationCommands.MarkPoolItemExceptionCommand();
        var parseFailed = new ProcurementRequirementConfirmationCommands.MarkPoolItemExceptionCommand();
        var confirm = new ProcurementRequirementConfirmationCommands.ConfirmFinalCandidatesCommand();
        var summary = new ProcurementRequirementConfirmationCommands.GenerateSummaryCommand();
        List<ProcurementRequirementConfirmationCommands.OperatorCommand> commands = List.of(
                initialize, remove, add, finish, reply, advance, noReply, parseFailed, confirm, summary
        );
        commands.forEach(this::forgeIdentity);
        when(businessAccessResolver.requireOwnerUserId(context, 99999L)).thenReturn(10002L);
        when(poolServiceProvider.getIfAvailable()).thenReturn(poolService);

        controller.initializePool(41101L, initialize, context);
        controller.removePoolItem(41101L, 91001L, remove, context);
        controller.addPoolCandidate(41101L, 43101L, add, context);
        controller.finishInquiry(41101L, finish, context);
        controller.recordPoolItemReply(41101L, 91001L, reply, context);
        controller.advancePoolItemFollowUp(41101L, 91001L, advance, context);
        controller.markPoolItemNoReplyHandoff(41101L, 91001L, noReply, context);
        controller.markPoolItemReplyParseFailed(41101L, 91001L, parseFailed, context);
        controller.confirmFinalCandidates(41101L, confirm, context);
        controller.generateSummary(41101L, summary, context);

        verify(businessAccessResolver, times(10)).requireOwnerUserId(context, 99999L);
        commands.forEach(this::assertAuthenticatedIdentity);
    }

    @Test
    void shouldNormalizeNullWriteBodyBeforeCallingService() {
        BusinessAccessContext context = context();
        when(businessAccessResolver.requireOwnerUserId(context, null)).thenReturn(10002L);
        when(poolServiceProvider.getIfAvailable()).thenReturn(poolService);

        controller.finishInquiry(41101L, null, context);

        ArgumentCaptor<ProcurementRequirementConfirmationCommands.FinishPoolInquiryCommand> captor =
                ArgumentCaptor.forClass(ProcurementRequirementConfirmationCommands.FinishPoolInquiryCommand.class);
        verify(poolService).finishInquiry(org.mockito.ArgumentMatchers.eq(41101L), captor.capture());
        assertNotNull(captor.getValue());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals("采购", captor.getValue().getOperatorRole());
    }

    @Test
    void everyEndpointShouldDeclareExactlyOneProcurementAccessContext() {
        List<Method> endpoints = Arrays.stream(
                        ProcurementRequirementConfirmationController.class.getDeclaredMethods()
                )
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class))
                .collect(Collectors.toList());

        assertEquals(12, endpoints.size());
        for (Method endpoint : endpoints) {
            List<Parameter> contexts = Arrays.stream(endpoint.getParameters())
                    .filter(parameter -> parameter.getType().equals(BusinessAccessContext.class))
                    .collect(Collectors.toList());
            assertEquals(1, contexts.size(), endpoint.getName());
            RequiredBusinessAccess requiredAccess =
                    contexts.get(0).getAnnotation(RequiredBusinessAccess.class);
            assertNotNull(requiredAccess, endpoint.getName());
            assertEquals(BusinessCapability.PROCUREMENT, requiredAccess.capability(), endpoint.getName());
        }
    }

    @Test
    void ownerPredicateShouldBeMandatoryInAllDemandRootQueries() {
        Set<String> scopedQueries = Set.of(
                "listDemandRows",
                "countDemandRows",
                "selectDemandDetail",
                "selectDemandDetailForUpdate"
        );
        List<Method> methods = Arrays.stream(
                        ProcurementRequirementConfirmationMapper.class.getDeclaredMethods()
                )
                .filter(method -> scopedQueries.contains(method.getName()))
                .collect(Collectors.toList());

        assertEquals(scopedQueries.size(), methods.size());
        for (Method method : methods) {
            String sql = String.join("\n", method.getAnnotation(Select.class).value());
            assertTrue(sql.contains("AND po.owner_user_id = #{ownerUserId}"), method.getName());
            assertFalse(sql.contains("ownerUserId != null"), method.getName());
            if (method.getName().equals("listDemandRows") || method.getName().equals("countDemandRows")) {
                assertTrue(sql.startsWith("<script>"), method.getName());
                assertTrue(sql.endsWith("</script>"), method.getName());
            } else {
                assertFalse(sql.contains("<script>"), method.getName());
                assertFalse(sql.contains("</script>"), method.getName());
            }
        }
    }

    @Test
    void poolQueriesShouldRejectInconsistentOwnerDemandAndPoolRelations() {
        String poolSql = selectSql("selectCurrentPoolForUpdate");
        assertTrue(poolSql.contains("JOIN procurement_order po"), "selectCurrentPoolForUpdate");
        assertTrue(poolSql.contains("pool.demand_item_id = di.id"), "selectCurrentPoolForUpdate");
        assertTrue(poolSql.contains("pool.owner_user_id = po.owner_user_id"), "selectCurrentPoolForUpdate");

        for (String methodName : List.of(
                "listCurrentPoolItems",
                "selectPoolItemForUpdate",
                "listCurrentPoolItemsForUpdate"
        )) {
            String itemSql = selectSql(methodName);
            assertTrue(itemSql.contains("JOIN procurement_candidate_pool pool"), methodName);
            assertTrue(itemSql.contains("item.owner_user_id = pool.owner_user_id"), methodName);
            assertTrue(itemSql.contains("item.demand_item_id = pool.demand_item_id"), methodName);
        }
    }

    private String selectSql(String methodName) {
        Method method = Arrays.stream(ProcurementRequirementConfirmationMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return String.join("\n", method.getAnnotation(Select.class).value());
    }

    private void forgeIdentity(ProcurementRequirementConfirmationCommands.OperatorCommand command) {
        command.setOwnerUserId(99999L);
        command.setOperatorUserId(88888L);
        command.setOperatorRole("SYSTEM_TASK");
    }

    private void assertAuthenticatedIdentity(ProcurementRequirementConfirmationCommands.OperatorCommand command) {
        assertEquals(10002L, command.getOwnerUserId());
        assertEquals(90001L, command.getOperatorUserId());
        assertEquals("采购", command.getOperatorRole());
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleName("采购")
                .build();
    }
}
