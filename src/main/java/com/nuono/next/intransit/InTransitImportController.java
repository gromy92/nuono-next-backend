package com.nuono.next.intransit;

import com.nuono.next.intransit.InTransitBatchCommands.ConfirmImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.PreviewImportCommand;
import com.nuono.next.intransit.InTransitBatchRecords.ImportConfirmView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/in-transit-goods")
public class InTransitImportController {

    private final InTransitImportService importService;
    private final BusinessAccessResolver businessAccessResolver;

    public InTransitImportController(
            InTransitImportService importService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.importService = importService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @PostMapping("/import-preview")
    public ImportPreviewView previewImport(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        PreviewImportCommand command = new PreviewImportCommand();
        command.setOwnerUserId(context.getBusinessOwnerUserId());
        command.setOperatorUserId(context.getSessionUserId());
        command.setAccessContext(context);
        command.setFileName(file == null ? null : file.getOriginalFilename());
        command.setContentType(file == null ? null : file.getContentType());
        try {
            command.setContent(file == null ? null : file.getBytes());
            return importService.preview(command);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导入文件读取失败。", exception);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate(HttpServletRequest request) {
        requireContext(request);
        byte[] template = importService.buildTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"in-transit-goods-import-template.xlsx\"")
                .body(template);
    }

    @PostMapping("/imports/{importBatchId}/confirm")
    public ImportConfirmView confirmImport(
            @PathVariable Long importBatchId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        ConfirmImportCommand command = new ConfirmImportCommand();
        command.setImportBatchId(importBatchId);
        command.setOwnerUserId(context.getBusinessOwnerUserId());
        command.setOperatorUserId(context.getSessionUserId());
        command.setAccessContext(context);
        try {
            return importService.confirm(command);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    private BusinessAccessContext requireContext(HttpServletRequest request) {
        return businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }
}
