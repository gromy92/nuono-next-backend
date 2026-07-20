package com.nuono.next.filemanagement.parse;

import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(FileParseHttpSupport.BASE_PATH)
public class FileParseReviewPublicationController {

    private final FileParseHttpSupport httpSupport;

    public FileParseReviewPublicationController(FileParseHttpSupport httpSupport) {
        this.httpSupport = httpSupport;
    }

    @GetMapping("/target-plans/{targetPlanId}/versions")
    public FileParseVersionListView versions(
            @PathVariable("targetPlanId") Long targetPlanId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.listVersions(session, targetPlanId, page, pageSize)
        );
    }

    @GetMapping("/versions/{versionId}/items")
    public FileParseVersionItemsView versionItems(
            @PathVariable("versionId") Long versionId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request
    ) {
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.listVersionItems(session, versionId, page, pageSize)
        );
    }

    @GetMapping("/logistics-channel-activations")
    public FileParseLogisticsChannelActivationView logisticsChannelActivations(
            @RequestParam("targetPlanId") Long targetPlanId,
            @RequestParam(value = "versionId", required = false) Long versionId,
            HttpServletRequest request
    ) {
        return httpSupport.invokeValidated(
                request,
                (service, session) -> service.listLogisticsChannelActivations(
                        session,
                        targetPlanId,
                        versionId
                )
        );
    }

    @PostMapping("/logistics-channel-activations")
    public FileParseLogisticsChannelActivationView saveLogisticsChannelActivations(
            @RequestBody FileParseLogisticsChannelActivationCommand command,
            HttpServletRequest request
    ) {
        return httpSupport.invokeConflictAware(
                request,
                (service, session) -> service.saveLogisticsChannelActivations(session, command)
        );
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
        return httpSupport.invokeConflictAware(
                request,
                (service, session) -> service.batchAcceptResultItems(
                        session,
                        taskId,
                        command,
                        idempotencyKey
                )
        );
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
        return httpSupport.invokeConflictAware(
                request,
                (service, session) -> service.publishTask(
                        session,
                        taskId,
                        command,
                        idempotencyKey
                )
        );
    }

    private FileParseProcessingItemView reviewItem(
            Long taskId,
            Long itemId,
            String action,
            FileParseReviewCommand command,
            String idempotencyKey,
            HttpServletRequest request
    ) {
        return httpSupport.invokeConflictAware(
                request,
                (service, session) -> service.reviewResultItem(
                        session,
                        taskId,
                        itemId,
                        action,
                        command,
                        idempotencyKey
                )
        );
    }
}
