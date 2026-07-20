package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class LocalDbFileParseUploadFacadeTest {

    @Mock
    private FileManagementParseMapper mapper;

    @Mock
    private FileParseUploadArchiveService uploadArchiveService;

    private LocalDbFileManagementParseService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbFileManagementParseService(
                mapper,
                new FileParseStorageProperties(),
                uploadArchiveService,
                mock(FileParseTaskCatalogService.class),
                new FileParseActionPolicy(),
                mock(FileParseInputExtractionService.class),
                mock(FileParseStructuredAiService.class),
                mock(FileParseResultDiffService.class),
                mock(FileParseResultPersistenceService.class),
                mock(FileParseItemReviewService.class),
                mock(FileParseQueryViewService.class),
                mock(FileParsePublishService.class),
                mock(FileParseLogisticsChannelActivationService.class)
        );
    }

    @Test
    void authorizesBeforeDelegatingUploadWithTrustedContext() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L);
        MockMultipartFile file = new MockMultipartFile("file", "quote.xlsx", "application/vnd.ms-excel", new byte[] {1});
        FileParseUploadView expected = new FileParseUploadView();
        when(mapper.selectUserContext(10001L)).thenReturn(admin);
        when(mapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(uploadArchiveService.archive(plan, 10001L, file)).thenReturn(expected);

        FileParseUploadView actual = service.uploadFile(
                new AuthenticatedSession(10001L, 1L, 0),
                4005L,
                file
        );

        assertSame(expected, actual);
        InOrder order = inOrder(mapper, uploadArchiveService);
        order.verify(mapper).selectUserContext(10001L);
        order.verify(mapper).selectVisibleTargetPlan(4005L, 0, true);
        order.verify(uploadArchiveService).archive(plan, 10001L, file);
    }

    @Test
    void rejectsReadOnlyOperatorBeforeUploadSideEffects() {
        FileParseUserContext operator = user(10004L, 3, "OPS", "运营");
        FileParseTargetPlanRow plan = targetPlan(4002L);
        when(mapper.selectUserContext(10004L)).thenReturn(operator);
        when(mapper.countActiveUserMenu(10004L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(mapper.selectVisibleTargetPlan(4002L, 3, false)).thenReturn(plan);
        MockMultipartFile file = new MockMultipartFile("file", "commission.pdf", "application/pdf", new byte[] {1});

        assertThrows(
                FileParseAccessDeniedException.class,
                () -> service.uploadFile(new AuthenticatedSession(10004L, 4L, 3), 4002L, file)
        );

        verifyNoInteractions(uploadArchiveService);
    }

    @Test
    void delegatesArchiveResolutionWithDerivedAdminFlag() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseArchivedFile expected = mock(FileParseArchivedFile.class);
        when(mapper.selectUserContext(10001L)).thenReturn(admin);
        when(uploadArchiveService.resolve(eq(10009L), eq(10001L), eq(true))).thenReturn(expected);

        FileParseArchivedFile actual = service.resolveArchivedFile(
                new AuthenticatedSession(10001L, 1L, 0),
                10009L
        );

        assertSame(expected, actual);
        InOrder order = inOrder(mapper, uploadArchiveService);
        order.verify(mapper).selectUserContext(10001L);
        order.verify(uploadArchiveService).resolve(10009L, 10001L, true);
    }

    @Test
    void delegatesOrdinaryUserResolutionWithoutAdminBypass() {
        FileParseUserContext operator = user(10004L, 3, "OPS", "运营");
        FileParseArchivedFile expected = mock(FileParseArchivedFile.class);
        when(mapper.selectUserContext(10004L)).thenReturn(operator);
        when(mapper.countActiveUserMenu(10004L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(uploadArchiveService.resolve(10009L, 10004L, false)).thenReturn(expected);

        FileParseArchivedFile actual = service.resolveArchivedFile(
                new AuthenticatedSession(10004L, 4L, 3),
                10009L
        );

        assertSame(expected, actual);
        InOrder order = inOrder(mapper, uploadArchiveService);
        order.verify(mapper).selectUserContext(10004L);
        order.verify(mapper).countActiveUserMenu(
                10004L,
                LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID
        );
        order.verify(uploadArchiveService).resolve(10009L, 10004L, false);
    }

    private FileParseUserContext user(Long userId, int roleLevel, String roleCode, String roleName) {
        FileParseUserContext user = new FileParseUserContext();
        user.setUserId(userId);
        user.setRoleLevel(roleLevel);
        user.setRoleCode(roleCode);
        user.setRoleName(roleName);
        user.setStatus(1);
        return user;
    }

    private FileParseTargetPlanRow targetPlan(Long targetPlanId) {
        FileParseTargetPlanRow plan = new FileParseTargetPlanRow();
        plan.setId(targetPlanId);
        return plan;
    }
}
