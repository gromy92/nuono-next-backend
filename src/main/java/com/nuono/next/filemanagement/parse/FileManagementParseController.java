package com.nuono.next.filemanagement.parse;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/file-management/parse")
public class FileManagementParseController {

    private final ObjectProvider<LocalDbFileManagementParseService> fileManagementParseServiceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public FileManagementParseController(
            ObjectProvider<LocalDbFileManagementParseService> fileManagementParseServiceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.fileManagementParseServiceProvider = fileManagementParseServiceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @GetMapping("/target-plans")
    public List<FileParseTargetPlanSummary> targetPlans(HttpServletRequest request) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.listTargetPlans(session);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }
    }

    @GetMapping("/tasks")
    public FileParseTaskListView tasks(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "targetPlanId", required = false) Long targetPlanId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.listTasks(session, keyword, targetPlanId, status, page, pageSize);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping("/tasks/{taskId}")
    public FileParseTaskDetailView taskDetail(
            @PathVariable("taskId") Long taskId,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.getTask(session, taskId);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable("taskId") Long taskId,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            service.deleteTask(session, taskId);
            return ResponseEntity.noContent().build();
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileParseUploadView upload(
            @RequestParam("targetPlanId") Long targetPlanId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.uploadFile(session, targetPlanId, file);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage(), error);
        }
    }

    @PostMapping("/tasks")
    public FileParseTaskDetailView createTask(
            @RequestBody FileParseCreateTaskCommand command,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.createTask(session, command, idempotencyKey);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage(), error);
        }
    }

    @PostMapping("/tasks/{taskId}/run")
    public FileParseTaskRunView runTask(
            @PathVariable("taskId") Long taskId,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.startParseTask(session, taskId);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }

    @GetMapping("/tasks/{taskId}/source-rows")
    public FileParseSourceRowsView sourceRows(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "inputId", required = false) Long inputId,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.listSourceRows(session, taskId, inputId, sourceType, keyword, page, pageSize);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping("/tasks/{taskId}/workflow")
    public FileParseWorkflowView workflow(
            @PathVariable("taskId") Long taskId,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.getWorkflow(session, taskId);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping("/tasks/{taskId}/ai-chunks")
    public FileParseAiChunksView aiChunks(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.listAiChunks(session, taskId, status, page, pageSize);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping("/tasks/{taskId}/validation-issues")
    public FileParseValidationIssuesView validationIssues(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "issueType", required = false) String issueType,
            @RequestParam(value = "resolvedStatus", required = false) String resolvedStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.listValidationIssues(session, taskId, severity, issueType, resolvedStatus, page, pageSize);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping("/tasks/{taskId}/processing-items")
    public FileParseProcessingItemsView processingItems(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "reviewStatus", required = false) String reviewStatus,
            @RequestParam(value = "changeType", required = false) String changeType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.listProcessingItems(session, taskId, reviewStatus, changeType, page, pageSize);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping("/tasks/{taskId}/overview-items")
    public FileParseOverviewItemsView overviewItems(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.listOverviewItems(session, taskId, page, pageSize);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping({"/tasks/{taskId}/overview-items/export", "/tasks/{taskId}/overview/export"})
    public ResponseEntity<Resource> exportOverviewItems(
            @PathVariable("taskId") Long taskId,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            FileParseExportFile exportFile = service.exportOverviewItems(session, taskId);
            String encodedFileName = URLEncoder.encode(exportFile.getFileName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            byte[] content = exportFile.getContent();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentLength(content.length)
                    .contentType(MediaType.parseMediaType(exportFile.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                    .body(new ByteArrayResource(content));
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage(), error);
        }
    }

    @GetMapping("/target-plans/{targetPlanId}/versions")
    public FileParseVersionListView versions(
            @PathVariable("targetPlanId") Long targetPlanId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.listVersions(session, targetPlanId, page, pageSize);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping("/versions/{versionId}/items")
    public FileParseVersionItemsView versionItems(
            @PathVariable("versionId") Long versionId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.listVersionItems(session, versionId, page, pageSize);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @GetMapping("/logistics-channel-activations")
    public FileParseLogisticsChannelActivationView logisticsChannelActivations(
            @RequestParam("targetPlanId") Long targetPlanId,
            @RequestParam(value = "versionId", required = false) Long versionId,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.listLogisticsChannelActivations(session, targetPlanId, versionId);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PostMapping("/logistics-channel-activations")
    public FileParseLogisticsChannelActivationView saveLogisticsChannelActivations(
            @RequestBody FileParseLogisticsChannelActivationCommand command,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.saveLogisticsChannelActivations(session, command);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }

    @GetMapping("/tasks/{taskId}/items/{itemId}/compare")
    public FileParseItemCompareView compareItem(
            @PathVariable("taskId") Long taskId,
            @PathVariable("itemId") Long itemId,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.compareResultItem(session, taskId, itemId);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PostMapping("/tasks/{taskId}/items/{itemId}/edit")
    public FileParseProcessingItemView editItem(
            @PathVariable("taskId") Long taskId,
            @PathVariable("itemId") Long itemId,
            @RequestBody FileParseReviewCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        return reviewItem(taskId, itemId, "edit", command, idempotencyKey, request);
    }

    @PostMapping("/tasks/{taskId}/items/{itemId}/accept")
    public FileParseProcessingItemView acceptItem(
            @PathVariable("taskId") Long taskId,
            @PathVariable("itemId") Long itemId,
            @RequestBody FileParseReviewCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        return reviewItem(taskId, itemId, "accept", command, idempotencyKey, request);
    }

    @PostMapping("/tasks/{taskId}/items/batch-accept")
    public FileParseBatchReviewView batchAcceptItems(
            @PathVariable("taskId") Long taskId,
            @RequestBody FileParseBatchReviewCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.batchAcceptResultItems(session, taskId, command, idempotencyKey);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }

    @PostMapping("/tasks/{taskId}/items/{itemId}/reject")
    public FileParseProcessingItemView rejectItem(
            @PathVariable("taskId") Long taskId,
            @PathVariable("itemId") Long itemId,
            @RequestBody FileParseReviewCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        return reviewItem(taskId, itemId, "reject", command, idempotencyKey, request);
    }

    @PostMapping("/tasks/{taskId}/items/{itemId}/keep-old")
    public FileParseProcessingItemView keepOldItem(
            @PathVariable("taskId") Long taskId,
            @PathVariable("itemId") Long itemId,
            @RequestBody FileParseReviewCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        return reviewItem(taskId, itemId, "keep_old", command, idempotencyKey, request);
    }

    @PostMapping("/tasks/{taskId}/publish")
    public FileParsePublishView publishTask(
            @PathVariable("taskId") Long taskId,
            @RequestBody FileParsePublishCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.publishTask(session, taskId, command, idempotencyKey);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable("fileId") Long fileId,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            FileParseArchivedFile archivedFile = service.resolveArchivedFile(session, fileId);
            String contentType = archivedFile.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = Files.probeContentType(archivedFile.getPath());
            }
            if (contentType == null || contentType.isBlank()) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            String encodedFileName = URLEncoder.encode(archivedFile.getFileName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                    .body(new FileSystemResource(archivedFile.getPath()));
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IOException | IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage(), error);
        }
    }

    private LocalDbFileManagementParseService requireService() {
        LocalDbFileManagementParseService service = fileManagementParseServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文件管理解析数据库服务尚未启用。");
        }
        return service;
    }

    private FileParseProcessingItemView reviewItem(
            Long taskId,
            Long itemId,
            String action,
            FileParseReviewCommand command,
            String idempotencyKey,
            HttpServletRequest request
    ) {
        LocalDbFileManagementParseService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            return service.reviewResultItem(session, taskId, itemId, action, command, idempotencyKey);
        } catch (FileParseAccessDeniedException error) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }
}
