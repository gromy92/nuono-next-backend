package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FileParseActionPolicyTest {

    private final FileParseActionPolicy policy = new FileParseActionPolicy();

    @ParameterizedTest(name = "{0}")
    @MethodSource("systemAdminAliases")
    void recognizesEverySystemAdminAlias(String scenario, FileParseUserContext user) {
        assertTrue(policy.isSystemAdmin(user));
    }

    @Test
    void rejectsMissingOrOrdinaryUsersAsSystemAdmin() {
        assertAll(
                () -> assertFalse(policy.isSystemAdmin(null)),
                () -> assertFalse(policy.isSystemAdmin(user(3, "OPS", "运营")))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actionMatrix")
    void exposesActionsByRole(
            String scenario,
            FileParseUserContext user,
            boolean canCreateTask,
            boolean canProcess,
            boolean canPublish,
            boolean canManageStandard,
            boolean canActivateLogisticsChannels
    ) {
        FileParseAvailableActions actions = policy.availableActions(
                plan("LOGISTICS_PROVIDER_RATE", "RATE_SHEET"),
                user
        );

        assertAll(
                () -> assertFlag(canCreateTask, actions.isCanCreateTask()),
                () -> assertFlag(canProcess, actions.isCanProcess()),
                () -> assertFlag(canPublish, actions.isCanPublish()),
                () -> assertFlag(canManageStandard, actions.isCanManageStandard()),
                () -> assertFlag(
                        canActivateLogisticsChannels,
                        actions.isCanActivateLogisticsChannels()
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("logisticsPlans")
    void recognizesLogisticsPlanFromCodeOrDocumentType(
            String scenario,
            FileParseTargetPlanRow plan
    ) {
        assertTrue(policy.isLogisticsPlan(plan));
    }

    @Test
    void rejectsNullAndNonLogisticsPlans() {
        assertAll(
                () -> assertFalse(policy.isLogisticsPlan(null)),
                () -> assertFalse(policy.isLogisticsPlan(plan("COMMISSION", "SETTLEMENT"))),
                () -> assertFalse(policy.isLogisticsPlan(plan(null, null)))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("logisticsActivationRoles")
    void neverActivatesLogisticsChannelsForNonLogisticsPlans(
            String scenario,
            FileParseUserContext user
    ) {
        FileParseAvailableActions actions = policy.availableActions(
                plan("COMMISSION", "SETTLEMENT"),
                user
        );

        assertFalse(actions.isCanActivateLogisticsChannels());
    }

    private static Stream<Arguments> systemAdminAliases() {
        return Stream.of(
                Arguments.of("role level", user(0, null, null)),
                Arguments.of("English role code", user(null, "system_admin", null)),
                Arguments.of("Chinese role name", user(null, null, "系统管理员"))
        );
    }

    private static Stream<Arguments> actionMatrix() {
        return Stream.of(
                role("admin role level", user(0, null, null), true, true, true, true, true),
                role("admin code", user(null, "SYSTEM_ADMIN", null), true, true, true, true, true),
                role("admin name", user(null, null, "系统管理员"), true, true, true, true, true),
                role("boss role level", user(1, null, null), true, true, false, false, true),
                role("boss code", user(null, "boss", null), true, true, false, false, true),
                role("boss name", user(null, null, "老板"), true, true, false, false, true),
                role("ops role level", user(2, null, null), true, true, false, false, false),
                role("ops code", user(null, "ops_manager", null), true, true, false, false, false),
                role("ops name", user(null, null, "运营主管"), true, true, false, false, false),
                role("operator", user(3, "OPS", "运营"), false, false, false, false, false)
        );
    }

    private static Stream<Arguments> logisticsPlans() {
        return Stream.of(
                Arguments.of("code prefix", plan("LOGISTICS_PROVIDER_RATE", "RATE_SHEET")),
                Arguments.of("case-insensitive code prefix", plan("logistics-quote", "QUOTE")),
                Arguments.of("document type prefix", plan("PROVIDER_RATE", "LOGISTICS_RATE_SHEET")),
                Arguments.of("case-insensitive document prefix", plan("QUOTE", "logistics-quote"))
        );
    }

    private static Stream<Arguments> logisticsActivationRoles() {
        return Stream.of(
                Arguments.of("admin", user(0, null, null)),
                Arguments.of("boss", user(1, null, null))
        );
    }

    private static Arguments role(
            String scenario,
            FileParseUserContext user,
            boolean canCreateTask,
            boolean canProcess,
            boolean canPublish,
            boolean canManageStandard,
            boolean canActivateLogisticsChannels
    ) {
        return Arguments.of(
                scenario,
                user,
                canCreateTask,
                canProcess,
                canPublish,
                canManageStandard,
                canActivateLogisticsChannels
        );
    }

    private static FileParseUserContext user(
            Integer roleLevel,
            String roleCode,
            String roleName
    ) {
        FileParseUserContext user = new FileParseUserContext();
        user.setRoleLevel(roleLevel);
        user.setRoleCode(roleCode);
        user.setRoleName(roleName);
        return user;
    }

    private static FileParseTargetPlanRow plan(String code, String documentType) {
        FileParseTargetPlanRow plan = new FileParseTargetPlanRow();
        plan.setCode(code);
        plan.setDocumentType(documentType);
        return plan;
    }

    private static void assertFlag(boolean expected, boolean actual) {
        if (expected) {
            assertTrue(actual);
            return;
        }
        assertFalse(actual);
    }
}
