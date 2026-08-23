package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.officialwarehouse.OfficialWarehouseAsnTemplateProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseAsnTemplateProvider.TemplateFile;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OfficialWarehouseAsnTemplateExportServiceTest {

    @Test
    void exportsStoreScopedFilledTemplateWithSelectedQuantity() {
        OfficialWarehouseAsnTemplateProvider provider = mock(OfficialWarehouseAsnTemplateProvider.class);
        OfficialWarehouseAsnTemplateCsv csv = new OfficialWarehouseAsnTemplateCsv();
        String source = String.join(",", OfficialWarehouseAsnTemplateCsv.EXPECTED_HEADERS) + "\r\n"
                + "CODE-1,BARCODE-1,PSKU-1,family,brand,Title,SKU-1,0,SA,10,8,2,0.1,standard,0.01,Noon System,0\r\n";
        when(provider.fetchLatest(any())).thenReturn(new TemplateFile(
                source.getBytes(StandardCharsets.UTF_8),
                "fbn_eligible_item_69486_2026-08-23_SA.csv"
        ));
        OfficialWarehouseAsnTemplateExportService service =
                new OfficialWarehouseAsnTemplateExportService(provider, csv);
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .storeOwnerUserIds(Map.of("STR69486-NSA", 307L))
                .build();
        OfficialWarehouseAsnTemplateExportCommand command = new OfficialWarehouseAsnTemplateExportCommand();
        command.storeCode = "STR69486-NSA";
        command.siteCode = "sa";
        OfficialWarehouseAsnTemplateExportCommand.Line line = new OfficialWarehouseAsnTemplateExportCommand.Line();
        line.partnerSku = "PSKU-1";
        line.quantity = 39;
        command.lines.add(line);

        OfficialWarehouseAsnTemplateExportService.ExportFile result = service.export(access, command);

        ArgumentCaptor<PullRequest> request = ArgumentCaptor.forClass(PullRequest.class);
        verify(provider).fetchLatest(request.capture());
        assertThat(request.getValue().ownerUserId).isEqualTo(307L);
        assertThat(request.getValue().storeCode).isEqualTo("STR69486-NSA");
        assertThat(request.getValue().siteCode).isEqualTo("SA");
        assertThat(result.fileName).isEqualTo("fbn_eligible_item_69486_2026-08-23_SA_filled.csv");
        assertThat(new String(result.content, StandardCharsets.UTF_8))
                .contains("SKU-1,39,SA,10,8,2");
    }
}
