package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.nuono.next.intransit.InTransitBatchCommands.ConfirmImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.PreviewImportCommand;
import com.nuono.next.intransit.InTransitBatchRecords.ImportConfirmView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class InTransitImportControllerTest {

    @Mock
    private InTransitImportService importService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    @Mock
    private HttpServletRequest request;

    private InTransitImportController controller;

    @BeforeEach
    void setUp() {
        controller = new InTransitImportController(importService, businessAccessResolver);
    }

    @Test
    void shouldOverwriteOwnerOperatorWhenPreviewingImport() throws Exception {
        BusinessAccessContext context = context();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "历史在途.csv",
                "text/csv",
                "批次号,SKU,发货数量\nBATCH-001,SKU-AE-001,10\n".getBytes()
        );
        ImportPreviewView preview = new ImportPreviewView();
        preview.setImportBatchId(56001L);

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<PreviewImportCommand> captor = ArgumentCaptor.forClass(PreviewImportCommand.class);
        when(importService.preview(captor.capture())).thenReturn(preview);

        ImportPreviewView result = controller.previewImport(file, request);

        assertEquals(56001L, result.getImportBatchId());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals("历史在途.csv", captor.getValue().getFileName());
        assertEquals(context, captor.getValue().getAccessContext());
    }

    @Test
    void shouldDownloadImportTemplateBehindInTransitGoodsCapability() {
        byte[] template = new byte[]{1, 2, 3};

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context());
        when(importService.buildTemplate()).thenReturn(template);

        ResponseEntity<byte[]> response = controller.downloadImportTemplate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(template, response.getBody());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", response.getHeaders().getContentType().toString());
        Assertions.assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("in-transit-goods-import-template.xlsx"));
    }

    @Test
    void shouldOverwriteOwnerOperatorAndRouteImportIdWhenConfirmingImport() {
        BusinessAccessContext context = context();
        ImportConfirmView confirmView = new ImportConfirmView();
        confirmView.setImportBatchId(56001L);

        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(context);
        ArgumentCaptor<ConfirmImportCommand> captor = ArgumentCaptor.forClass(ConfirmImportCommand.class);
        when(importService.confirm(captor.capture())).thenReturn(confirmView);

        ImportConfirmView result = controller.confirmImport(56001L, request);

        assertEquals(56001L, result.getImportBatchId());
        assertEquals(56001L, captor.getValue().getImportBatchId());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
        assertEquals(context, captor.getValue().getAccessContext());
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
