package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseAsnTemplateExportService.ExportFile;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.web.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/warehouse/official-warehouse/asns")
public class OfficialWarehouseAsnTemplateController {

    private final ObjectProvider<OfficialWarehouseAsnTemplateExportService> serviceProvider;
    private final BusinessAccessResolver accessResolver;

    public OfficialWarehouseAsnTemplateController(
            ObjectProvider<OfficialWarehouseAsnTemplateExportService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.accessResolver = accessResolver;
    }

    @PostMapping("/eligible-template/export")
    public ResponseEntity<byte[]> export(
            @RequestBody OfficialWarehouseAsnTemplateExportCommand command,
            HttpServletRequest request
    ) {
        try {
            String storeCode = command == null ? null : command.storeCode;
            ExportFile file = service().export(
                    accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, storeCode),
                    command
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(file.fileName, StandardCharsets.UTF_8)
                    .build());
            headers.setContentLength(file.content.length);
            return new ResponseEntity<>(file.content, headers, HttpStatus.OK);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ApiProblemException(
                    HttpStatus.FAILED_DEPENDENCY,
                    "OFFICIAL_WAREHOUSE_ASN_TEMPLATE_EXPORT_FAILED",
                    "UPSTREAM_DEPENDENCY",
                    "EXPORT_ASN_ELIGIBLE_TEMPLATE",
                    exception.getMessage(),
                    false,
                    false,
                    null,
                    Map.of(),
                    exception
            );
        }
    }

    private OfficialWarehouseAsnTemplateExportService service() {
        OfficialWarehouseAsnTemplateExportService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon 约仓模板导出服务未启用。");
        }
        return service;
    }
}
