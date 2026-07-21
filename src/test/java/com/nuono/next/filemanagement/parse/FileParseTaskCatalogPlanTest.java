package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileParseTaskCatalogPlanTest {

    @Mock
    private FileManagementParseMapper mapper;

    @Mock
    private FileParseUploadArchiveService uploadArchiveService;

    private FileParseTaskCatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new FileParseTaskCatalogService(
                mapper,
                uploadArchiveService,
                new FileParseActionPolicy()
        );
    }

    @Test
    void listsPlanMetadataItemTypesAndAdminActions() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan();
        when(mapper.selectVisibleTargetPlans(0, true)).thenReturn(List.of(plan));
        when(mapper.selectItemStandards(5105L)).thenReturn(List.of(
                itemStandard("logistics_channel_rule", "物流渠道规则"),
                itemStandard("logistics_service_line", " ")
        ));

        List<FileParseTargetPlanSummary> plans = catalogService.listTargetPlans(admin);

        assertEquals(1, plans.size());
        FileParseTargetPlanSummary summary = plans.get(0);
        assertEquals(4005L, summary.getId());
        assertEquals("logistics_yite", summary.getCode());
        assertEquals("物流-义特", summary.getLabel());
        assertEquals("logistics_rule", summary.getDocumentType());
        assertEquals("物流报价规则", summary.getDocumentName());
        assertEquals("STD-2026-05", summary.getStandardVersion());
        assertEquals("V2026.05", summary.getCurrentVersion());
        assertEquals("义特物流报价", summary.getDescription());
        assertEquals(
                List.of("logistics_channel_rule", "logistics_service_line"),
                summary.getItemTypes().stream()
                        .map(FileParseTargetPlanItemTypeView::getValue)
                        .collect(Collectors.toList())
        );
        assertEquals("物流渠道规则", summary.getItemTypes().get(0).getLabel());
        assertEquals("logistics_service_line", summary.getItemTypes().get(1).getLabel());
        FileParseAvailableActions actions = summary.getAvailableActions();
        assertTrue(actions.isCanCreateTask());
        assertTrue(actions.isCanProcess());
        assertTrue(actions.isCanPublish());
        assertTrue(actions.isCanManageStandard());
        assertTrue(actions.isCanActivateLogisticsChannels());
        verify(mapper).selectVisibleTargetPlans(0, true);
        verify(mapper).selectItemStandards(5105L);
    }

    private FileParseUserContext user(Long id, Integer level, String code, String name) {
        FileParseUserContext user = new FileParseUserContext();
        user.setUserId(id);
        user.setRoleLevel(level);
        user.setRoleCode(code);
        user.setRoleName(name);
        user.setStatus(1);
        return user;
    }

    private FileParseTargetPlanRow targetPlan() {
        FileParseTargetPlanRow row = new FileParseTargetPlanRow();
        row.setId(4005L);
        row.setCode("logistics_yite");
        row.setLabel("物流-义特");
        row.setDocumentType("logistics_rule");
        row.setDocumentName("物流报价规则");
        row.setStandardVersionId(5105L);
        row.setStandardVersion("STD-2026-05");
        row.setCurrentVersion("V2026.05");
        row.setDescription("义特物流报价");
        return row;
    }

    private FileParseItemStandardRow itemStandard(String type, String label) {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setItemType(type);
        row.setItemLabel(label);
        return row;
    }
}
