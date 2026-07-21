package com.nuono.next.filemanagement.parse;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(FileParseHttpSupport.BASE_PATH)
public class FileParseTaskLifecycleController {

    private final FileParseHttpSupport httpSupport;

    public FileParseTaskLifecycleController(FileParseHttpSupport httpSupport) {
        this.httpSupport = httpSupport;
    }

    @GetMapping("/target-plans")
    public List<FileParseTargetPlanSummary> targetPlans(HttpServletRequest request) {
        return httpSupport.invokeAccessOnly(
                request,
                (service, session) -> service.listTargetPlans(session)
        );
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
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.listTasks(
                        session,
                        keyword,
                        targetPlanId,
                        status,
                        page,
                        pageSize
                )
        );
    }

    @GetMapping("/tasks/{taskId}")
    public FileParseTaskDetailView taskDetail(
            @PathVariable("taskId") Long taskId,
            HttpServletRequest request
    ) {
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.getTask(session, taskId)
        );
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable("taskId") Long taskId,
            HttpServletRequest request
    ) {
        return httpSupport.invokeValidated(request, (service, session) -> {
            service.deleteTask(session, taskId);
            return ResponseEntity.noContent().build();
        });
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileParseUploadView upload(
            @RequestParam("targetPlanId") Long targetPlanId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        return httpSupport.invokeInternalFailure(
                request,
                (service, session) -> service.uploadFile(session, targetPlanId, file)
        );
    }

    @PostMapping("/tasks")
    public FileParseTaskDetailView createTask(
            @RequestBody FileParseCreateTaskCommand command,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        return httpSupport.invokeInternalFailure(
                request,
                (service, session) -> service.createTask(session, command, idempotencyKey)
        );
    }

    @PostMapping("/tasks/{taskId}/run")
    public FileParseTaskRunView runTask(
            @PathVariable("taskId") Long taskId,
            HttpServletRequest request
    ) {
        return httpSupport.invokeConflictAware(
                request,
                (service, session) -> service.startParseTask(session, taskId)
        );
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable("fileId") Long fileId,
            HttpServletRequest request
    ) {
        return httpSupport.invokeIoInternalFailure(
                request,
                (service, session) -> httpSupport.downloadResponse(
                        service.resolveArchivedFile(session, fileId)
                )
        );
    }
}
