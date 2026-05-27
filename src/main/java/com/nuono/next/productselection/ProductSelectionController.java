package com.nuono.next.productselection;

import com.nuono.next.auth.AuthSessionTokenService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-selection")
public class ProductSelectionController {

    private final ObjectProvider<LocalDbSourceCollectionService> sourceCollectionServiceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public ProductSelectionController(
            ObjectProvider<LocalDbSourceCollectionService> sourceCollectionServiceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.sourceCollectionServiceProvider = sourceCollectionServiceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @GetMapping("/source-collections")
    public Object sourceCollections(
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sourcePlatform,
            @RequestParam(required = false) String sourceTitle,
            @RequestParam(required = false) String sourceTitleCn,
            @RequestParam(required = false) String status,
            HttpServletRequest request
    ) {
        try {
            if (page != null
                    || pageSize != null
                    || StringUtils.hasText(sourcePlatform)
                    || StringUtils.hasText(sourceTitle)
                    || StringUtils.hasText(sourceTitleCn)
                    || StringUtils.hasText(status)) {
                ProductSelectionSourceCollectionListQuery query = new ProductSelectionSourceCollectionListQuery();
                query.setPage(page);
                query.setPageSize(pageSize);
                query.setSourcePlatform(sourcePlatform);
                query.setSourceTitle(sourceTitle);
                query.setSourceTitleCn(sourceTitleCn);
                query.setStatus(status);
                return sourceCollectionService().listSourceCollectionsPage(
                        storeName,
                        storeCode,
                        authenticatedOperatorUserId(request),
                        query
                );
            }
            return sourceCollectionService().listSourceCollections(storeName, storeCode, authenticatedOperatorUserId(request));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @GetMapping("/ali1688-collections")
    public List<Ali1688CollectionView> ali1688Collections(
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String status,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().listAli1688Collections(
                    storeName,
                    storeCode,
                    status,
                    authenticatedOperatorUserId(request)
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @PostMapping("/source-collections")
    public ProductSelectionSourceCollectionView createSourceCollection(
            @RequestBody ProductSelectionSourceCollectionCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().createSourceCollection(attachOperator(command, authenticatedOperatorUserId(request)));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/source-collections/{collectionId}/recollect")
    public ProductSelectionSourceCollectionView recollectSourceCollection(
            @PathVariable String collectionId,
            @RequestBody(required = false) ProductSelectionSourceCollectionCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().recollectSourceCollection(
                    collectionId,
                    attachOperator(command, authenticatedOperatorUserId(request))
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/source-collections/{collectionId}/ali1688")
    public Ali1688CollectionView sourceCollectionAli1688(
            @PathVariable String collectionId,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().getSourceCollectionAli1688(collectionId, authenticatedOperatorUserId(request));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/source-collections/{collectionId}/ali1688/recollect")
    public Ali1688CollectionView recollectSourceCollectionAli1688(
            @PathVariable String collectionId,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().recollectSourceCollectionAli1688(collectionId, authenticatedOperatorUserId(request));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/ali1688-collections/{taskId}/retry")
    public Ali1688CollectionView retryAli1688Collection(
            @PathVariable String taskId,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().retryAli1688Collection(taskId, authenticatedOperatorUserId(request));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private LocalDbSourceCollectionService sourceCollectionService() {
        LocalDbSourceCollectionService service = sourceCollectionServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "人工选品源头采集服务未启用。");
        }
        return service;
    }

    private Long authenticatedOperatorUserId(HttpServletRequest request) {
        return sessionTokenService.requireSession(request).getUserId();
    }

    private ProductSelectionSourceCollectionCommand attachOperator(
            ProductSelectionSourceCollectionCommand command,
            Long operatorUserId
    ) {
        ProductSelectionSourceCollectionCommand source = command == null
                ? new ProductSelectionSourceCollectionCommand()
                : command;
        source.setOperatorUserId(operatorUserId);
        return source;
    }
}
