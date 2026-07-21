package com.nuono.next.filemanagement.parse;

import javax.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(FileParseHttpSupport.BASE_PATH)
public class FileParseInspectionController {

    private final FileParseHttpSupport httpSupport;

    public FileParseInspectionController(FileParseHttpSupport httpSupport) {
        this.httpSupport = httpSupport;
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
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.listSourceRows(
                        session,
                        taskId,
                        inputId,
                        sourceType,
                        keyword,
                        page,
                        pageSize
                )
        );
    }

    @GetMapping("/tasks/{taskId}/workflow")
    public FileParseWorkflowView workflow(
            @PathVariable("taskId") Long taskId,
            HttpServletRequest request
    ) {
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.getWorkflow(session, taskId)
        );
    }

    @GetMapping("/tasks/{taskId}/ai-chunks")
    public FileParseAiChunksView aiChunks(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.listAiChunks(
                        session,
                        taskId,
                        status,
                        page,
                        pageSize
                )
        );
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
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.listValidationIssues(
                        session,
                        taskId,
                        severity,
                        issueType,
                        resolvedStatus,
                        page,
                        pageSize
                )
        );
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
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.listProcessingItems(
                        session,
                        taskId,
                        reviewStatus,
                        changeType,
                        page,
                        pageSize
                )
        );
    }

    @GetMapping("/tasks/{taskId}/overview-items")
    public FileParseOverviewItemsView overviewItems(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.listOverviewItems(
                        session,
                        taskId,
                        page,
                        pageSize
                )
        );
    }

    @GetMapping({"/tasks/{taskId}/overview-items/export", "/tasks/{taskId}/overview/export"})
    public ResponseEntity<Resource> exportOverviewItems(
            @PathVariable("taskId") Long taskId,
            HttpServletRequest request
    ) {
        return httpSupport.invokeInternalFailure(
                request,
                (service, session) -> httpSupport.exportResponse(
                        service.exportOverviewItems(session, taskId)
                )
        );
    }

    @GetMapping("/tasks/{taskId}/items/{itemId}/compare")
    public FileParseItemCompareView compareItem(
            @PathVariable("taskId") Long taskId,
            @PathVariable("itemId") Long itemId,
            HttpServletRequest request
    ) {
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.compareResultItem(session, taskId, itemId)
        );
    }
}
