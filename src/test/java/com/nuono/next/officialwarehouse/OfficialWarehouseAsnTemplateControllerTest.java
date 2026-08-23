package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.officialwarehouse.OfficialWarehouseAsnTemplateExportService.ExportFile;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;

class OfficialWarehouseAsnTemplateControllerTest {

    @Test
    void returnsCsvAttachmentAfterCheckingOfficialWarehouseStoreAccess() {
        @SuppressWarnings("unchecked")
        ObjectProvider<OfficialWarehouseAsnTemplateExportService> provider = mock(ObjectProvider.class);
        OfficialWarehouseAsnTemplateExportService service = mock(OfficialWarehouseAsnTemplateExportService.class);
        BusinessAccessResolver accessResolver = mock(BusinessAccessResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR108065-NSA"))
                .build();
        OfficialWarehouseAsnTemplateExportCommand command = new OfficialWarehouseAsnTemplateExportCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        byte[] content = "code,quantity\r\n1,3\r\n".getBytes(StandardCharsets.UTF_8);
        when(provider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireStoreAccess(
                request, BusinessCapability.OFFICIAL_WAREHOUSE, command.storeCode
        )).thenReturn(access);
        when(service.export(access, command)).thenReturn(new ExportFile(
                content, "fbn_eligible_item_69486_2026-08-23_SA_filled.csv"
        ));
        OfficialWarehouseAsnTemplateController controller =
                new OfficialWarehouseAsnTemplateController(provider, accessResolver);

        ResponseEntity<byte[]> response = controller.export(command, request);

        assertThat(response.getBody()).isEqualTo(content);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("fbn_eligible_item_69486_2026-08-23_SA_filled.csv");
        verify(accessResolver).requireStoreAccess(
                request, BusinessCapability.OFFICIAL_WAREHOUSE, command.storeCode
        );
    }
}
