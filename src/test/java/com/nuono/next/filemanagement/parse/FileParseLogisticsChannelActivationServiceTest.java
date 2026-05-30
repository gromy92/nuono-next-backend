package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 数据一致性深度防御测试：FileParseLogisticsChannelActivationService 自身必须保证
 * version 归属 targetPlan，即使绕过编排层（orchestrator）直接调用也不能写错位记录。
 */
class FileParseLogisticsChannelActivationServiceTest {

    private static FileParseLogisticsChannelActivationService newService() {
        // 断言在方法最前执行，version 不匹配时直接抛出，不会触及 mapper / assembler，
        // 因此用 null 依赖即可单测该不变量。
        return new FileParseLogisticsChannelActivationService(null, null);
    }

    private static FileParseTargetPlanRow plan(long id) {
        FileParseTargetPlanRow plan = new FileParseTargetPlanRow();
        plan.setId(id);
        return plan;
    }

    private static FileParseVersionSummaryRow version(long id, Long targetPlanId) {
        FileParseVersionSummaryRow version = new FileParseVersionSummaryRow();
        version.setId(id);
        version.setTargetPlanId(targetPlanId);
        return version;
    }

    @Test
    void saveActivationsRejectsVersionFromAnotherPlan() {
        assertThrows(IllegalArgumentException.class, () ->
                newService().saveActivations(plan(1L), version(99L, 2L), 10L, List.of(), 10L));
    }

    @Test
    void saveActivationsRejectsVersionWithoutPlanBinding() {
        assertThrows(IllegalArgumentException.class, () ->
                newService().saveActivations(plan(1L), version(99L, null), 10L, List.of(), 10L));
    }

    @Test
    void listActivationsRejectsVersionFromAnotherPlan() {
        assertThrows(IllegalArgumentException.class, () ->
                newService().listActivations(plan(1L), version(99L, 2L), 10L));
    }
}
