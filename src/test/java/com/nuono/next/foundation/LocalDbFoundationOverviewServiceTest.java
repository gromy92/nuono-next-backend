package com.nuono.next.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.FoundationOverviewMapper;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbFoundationOverviewServiceTest {

    @Mock
    private FoundationOverviewMapper foundationOverviewMapper;

    @Mock
    private LocalDbBootstrapStatusService localDbBootstrapStatusService;

    private LocalDbFoundationOverviewService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbFoundationOverviewService(foundationOverviewMapper, localDbBootstrapStatusService);
    }

    @Test
    void shouldBuildUserDetailAndAttachStoreLinksWhenCoreTablesReady() {
        when(localDbBootstrapStatusService.inspect()).thenReturn(readyInspection());

        FoundationUserDetail detail = new FoundationUserDetail();
        detail.setId(10004L);
        detail.setRoleId(4L);
        detail.setRoleName("运营");
        detail.setNoonPartnerProjectUser("matianlong.project");

        FoundationUserStoreLink storeLink = new FoundationUserStoreLink();
        storeLink.setId(30003L);
        storeLink.setProjectCode("PJT-JED01");
        storeLink.setStoreCode("STORE-JED01");
        storeLink.setSite("AE");
        storeLink.setAuthorized(true);

        when(foundationOverviewMapper.selectUserDetail(10004L)).thenReturn(detail);
        when(foundationOverviewMapper.listUserStoreLinks(10004L)).thenReturn(List.of(storeLink));

        FoundationUserDetail result = service.buildUserDetail(10004L);

        assertSame(detail, result);
        assertEquals(10004L, result.getId());
        assertEquals("运营", result.getRoleName());
        assertEquals("matianlong.project", result.getNoonPartnerProjectUser());
        assertEquals(1, result.getStoreLinks().size());
        assertEquals("STORE-JED01", result.getStoreLinks().get(0).getStoreCode());
        verify(foundationOverviewMapper).selectUserDetail(10004L);
        verify(foundationOverviewMapper).listUserStoreLinks(10004L);
    }

    @Test
    void shouldRejectNullUserIdBeforeAnyMapperRead() {
        assertEquals(
                "缺少用户 ID，暂时不能读取迁移详情。",
                assertThrows(IllegalArgumentException.class, () -> service.buildUserDetail(null)).getMessage()
        );
        verifyNoInteractions(localDbBootstrapStatusService, foundationOverviewMapper);
    }

    @Test
    void shouldRejectUserDetailReadWhenCoreTablesNotReady() {
        when(localDbBootstrapStatusService.inspect()).thenReturn(
                new CoreTableInspection(
                        "nuono_new_dev",
                        List.of("user", "role"),
                        List.of("user"),
                        List.of("role")
                )
        );

        assertEquals(
                "本地库第一批核心表还没有补齐，暂时不能读取用户迁移详情。",
                assertThrows(IllegalStateException.class, () -> service.buildUserDetail(10004L)).getMessage()
        );
        verify(localDbBootstrapStatusService).inspect();
        verifyNoInteractions(foundationOverviewMapper);
    }

    @Test
    void shouldRejectUnknownUserWhenDetailReadReturnsNull() {
        when(localDbBootstrapStatusService.inspect()).thenReturn(readyInspection());
        when(foundationOverviewMapper.selectUserDetail(99999L)).thenReturn(null);

        assertEquals(
                "当前样本库里没有找到这条用户记录。",
                assertThrows(IllegalArgumentException.class, () -> service.buildUserDetail(99999L)).getMessage()
        );
        verify(foundationOverviewMapper).selectUserDetail(99999L);
    }

    private CoreTableInspection readyInspection() {
        return new CoreTableInspection(
                "nuono_new_dev",
                List.of("user", "role", "user_store"),
                List.of("user", "role", "user_store"),
                List.of()
        );
    }
}
