package com.nuono.next.logisticsquote;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/logistics-quote")
public class LogisticsQuoteController {

    private final LogisticsQuoteWorkbenchService logisticsQuoteWorkbenchService;
    private final LogisticsQuoteOperationService logisticsQuoteOperationService;

    public LogisticsQuoteController(
            LogisticsQuoteWorkbenchService logisticsQuoteWorkbenchService,
            LogisticsQuoteOperationService logisticsQuoteOperationService
    ) {
        this.logisticsQuoteWorkbenchService = logisticsQuoteWorkbenchService;
        this.logisticsQuoteOperationService = logisticsQuoteOperationService;
    }

    @GetMapping("/workbench")
    public LogisticsQuoteWorkbenchView workbench(
            @RequestParam(required = false) Long bundleId,
            @RequestParam(required = false) Long noteId,
            @RequestParam(required = false) Long fileId
    ) {
        return logisticsQuoteWorkbenchService.buildWorkbench(bundleId, noteId, fileId);
    }

    @PostMapping("/source-bundles")
    public LogisticsQuoteWorkbenchView createSourceBundle(@RequestBody LogisticsQuoteSourceBundleCreateCommand command) {
        try {
            return logisticsQuoteWorkbenchService.createSourceBundle(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/source-bundles/{bundleId}/files")
    public LogisticsQuoteWorkbenchView appendSourceBundleFile(
            @PathVariable Long bundleId,
            @RequestParam(required = false) Long selectedNoteId,
            @RequestParam(required = false) Long selectedFileId,
            @RequestBody LogisticsQuoteSourceBundleFileCreateCommand command
    ) {
        try {
            return logisticsQuoteWorkbenchService.appendSourceBundleFile(bundleId, selectedNoteId, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping(value = "/source-bundles/{bundleId}/files/archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LogisticsQuoteWorkbenchView archiveSourceBundleFile(
            @PathVariable Long bundleId,
            @RequestParam(required = false) Long selectedNoteId,
            @RequestParam(required = false) Long fileId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            return logisticsQuoteWorkbenchService.archiveSourceBundleFile(bundleId, selectedNoteId, fileId, file);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), exception);
        }
    }

    @GetMapping("/source-files/{fileId}/archive")
    public ResponseEntity<Resource> archivedSourceFile(@PathVariable Long fileId) {
        try {
            LogisticsQuoteArchivedFile archivedFile = logisticsQuoteWorkbenchService.resolveArchivedSourceFile(fileId);
            String contentType = Files.probeContentType(archivedFile.getPath());
            MediaType mediaType = contentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);
            String encodedFileName = URLEncoder
                    .encode(archivedFile.getFileName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.noCache())
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName
                    )
                    .body(new FileSystemResource(archivedFile.getPath()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取归档文件失败", exception);
        }
    }

    @PutMapping("/source-bundles/{bundleId}/files")
    public LogisticsQuoteWorkbenchView updateSourceBundleFile(
            @PathVariable Long bundleId,
            @RequestParam(required = false) Long selectedNoteId,
            @RequestParam(required = false) Long selectedFileId,
            @RequestBody LogisticsQuoteSourceBundleFileUpdateCommand command
    ) {
        try {
            return logisticsQuoteWorkbenchService.updateSourceBundleFile(bundleId, selectedNoteId, selectedFileId, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/source-bundles/{bundleId}/notes")
    public LogisticsQuoteWorkbenchView appendSourceBundleNote(
            @PathVariable Long bundleId,
            @RequestParam(required = false) Long selectedFileId,
            @RequestBody LogisticsQuoteSourceBundleNoteCreateCommand command
    ) {
        try {
            return logisticsQuoteWorkbenchService.appendSourceBundleNote(bundleId, selectedFileId, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/source-bundles/{bundleId}/quote-draft-from-note")
    public LogisticsQuoteWorkbenchView createQuoteDraftFromNote(
            @PathVariable Long bundleId,
            @RequestParam(required = false) Long selectedFileId,
            @RequestBody LogisticsQuoteDraftFromNoteCommand command
    ) {
        try {
            return logisticsQuoteWorkbenchService.createQuoteDraftFromNote(bundleId, selectedFileId, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PutMapping("/source-bundles/{bundleId}/notes")
    public LogisticsQuoteWorkbenchView updateSourceBundleNote(
            @PathVariable Long bundleId,
            @RequestParam(required = false) Long selectedFileId,
            @RequestBody LogisticsQuoteSourceBundleNoteUpdateCommand command
    ) {
        try {
            return logisticsQuoteWorkbenchService.updateSourceBundleNote(bundleId, selectedFileId, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PutMapping("/source-bundles/{bundleId}/analysis-summary")
    public LogisticsQuoteWorkbenchView updateSourceBundleAnalysisSummary(
            @PathVariable Long bundleId,
            @RequestParam(required = false) Long selectedNoteId,
            @RequestParam(required = false) Long selectedFileId,
            @RequestBody LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand command
    ) {
        try {
            return logisticsQuoteWorkbenchService.updateSourceBundleAnalysisSummary(bundleId, selectedNoteId, selectedFileId, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/note-preview")
    public LogisticsQuoteNotePreviewView notePreview(@RequestBody LogisticsQuoteNotePreviewCommand command) {
        try {
            return logisticsQuoteWorkbenchService.previewNote(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/operations/price-items")
    public LogisticsQuoteOperationPriceItemsView operationPriceItems(
            @RequestParam(required = false) String transportMode,
            @RequestParam(required = false) Long forwarderId,
            @RequestParam(required = false) String priceStatus
    ) {
        return logisticsQuoteOperationService.listPriceItems(transportMode, forwarderId, priceStatus);
    }

    @PostMapping("/operations/price-adjustments")
    public LogisticsQuoteOperationPriceAdjustmentView saveOperationPriceAdjustment(
            @RequestBody LogisticsQuoteOperationPriceAdjustmentCommand command
    ) {
        try {
            return logisticsQuoteOperationService.savePriceAdjustment(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), exception);
        }
    }
}
