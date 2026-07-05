package com.nuono.next.productselection;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final ObjectProvider<ProductSelectionAnalysisSkill> productSelectionAnalysisSkillProvider;
    private final ObjectProvider<ProductSelectionAccessAdapter> accessAdapterProvider;
    private final BusinessAccessResolver accessResolver;

    public ProductSelectionController(
            ObjectProvider<LocalDbSourceCollectionService> sourceCollectionServiceProvider,
            ObjectProvider<ProductSelectionAnalysisSkill> productSelectionAnalysisSkillProvider,
            ObjectProvider<ProductSelectionAccessAdapter> accessAdapterProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.sourceCollectionServiceProvider = sourceCollectionServiceProvider;
        this.productSelectionAnalysisSkillProvider = productSelectionAnalysisSkillProvider;
        this.accessAdapterProvider = accessAdapterProvider;
        this.accessResolver = accessResolver;
    }

    @GetMapping("/source-collections")
    public List<ProductSelectionSourceCollectionView> sourceCollections(
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) String storeCode,
            HttpServletRequest request
    ) {
        try {
            ProductSelectionAccessScope access = readableAccess(request, storeCode);
            return sourceCollectionService().listSourceCollections(storeName, access.getStoreCode(), access.getOperatorUserId());
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
            ProductSelectionAccessScope access = readableAccess(request, storeCode);
            return sourceCollectionService().listAli1688Collections(
                    storeName,
                    access.getStoreCode(),
                    status,
                    access.getOperatorUserId()
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @GetMapping("/analysis-items")
    public List<ProductSelectionAnalysisItemView> analysisItems(
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) String storeCode,
            HttpServletRequest request
    ) {
        try {
            ProductSelectionAccessScope access = readableAccess(request, storeCode);
            return sourceCollectionService().listAnalysisItems(storeName, access.getStoreCode(), access.getOperatorUserId());
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @GetMapping("/groups")
    public List<ProductSelectionGroupView> groups(
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) String storeCode,
            HttpServletRequest request
    ) {
        try {
            ProductSelectionAccessScope access = readableAccess(request, storeCode);
            return sourceCollectionService().listGroups(storeName, access.getStoreCode(), access.getOperatorUserId());
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @GetMapping("/groups/{groupId}")
    public ProductSelectionGroupView group(
            @PathVariable String groupId,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().getGroup(groupId, readableOperatorUserId(request, null));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/groups")
    public ProductSelectionGroupView createGroup(
            @RequestBody(required = false) ProductSelectionGroupCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().createGroup(attachOperator(
                    command,
                    writableOperatorUserId(request, requestedStoreCode(command))
            ));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/groups/{groupId}/materials")
    public ProductSelectionGroupView addGroupMaterials(
            @PathVariable String groupId,
            @RequestBody(required = false) ProductSelectionGroupCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().addGroupMaterials(
                    groupId,
                    attachOperator(command, writableOperatorUserId(request, requestedStoreCode(command)))
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PatchMapping("/groups/{groupId}")
    public ProductSelectionGroupView updateGroup(
            @PathVariable String groupId,
            @RequestBody(required = false) ProductSelectionGroupCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().updateGroupName(
                    groupId,
                    attachOperator(command, writableOperatorUserId(request, requestedStoreCode(command)))
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/groups/{groupId}/procurement")
    public ProductSelectionGroupView updateGroupProcurement(
            @PathVariable String groupId,
            @RequestBody(required = false) ProductSelectionGroupCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().updateGroupProcurement(
                    groupId,
                    attachOperator(command, writableOperatorUserId(request, requestedStoreCode(command)))
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/groups/{groupId}/profit-estimate")
    public ProductSelectionGroupProfitSnapshotView getGroupProfitEstimate(
            @PathVariable String groupId,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().getGroupProfitEstimate(
                    groupId,
                    readableOperatorUserId(request, null)
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/groups/{groupId}/profit-estimate")
    public ProductSelectionGroupProfitSnapshotView saveGroupProfitEstimate(
            @PathVariable String groupId,
            @RequestBody(required = false) ProductSelectionGroupProfitSnapshotCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().saveGroupProfitEstimate(
                    groupId,
                    attachOperator(command, writableOperatorUserId(request, null))
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/groups/{groupId}/competitors")
    public ProductSelectionGroupView updateGroupCompetitors(
            @PathVariable String groupId,
            @RequestBody(required = false) ProductSelectionGroupCompetitorCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().updateGroupCompetitors(
                    groupId,
                    attachOperator(command, writableOperatorUserId(request, null))
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/groups/{groupId}/competitors/{competitorId}/recollect")
    public ProductSelectionGroupView recollectGroupCompetitor(
            @PathVariable String groupId,
            @PathVariable String competitorId,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().recollectGroupCompetitor(
                    groupId,
                    competitorId,
                    writableOperatorUserId(request, null)
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/groups/{groupId}/competitors/{competitorId}")
    public ProductSelectionGroupView deleteGroupCompetitor(
            @PathVariable String groupId,
            @PathVariable String competitorId,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().deleteGroupCompetitor(
                    groupId,
                    competitorId,
                    writableOperatorUserId(request, null)
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/analysis-items")
    public List<ProductSelectionAnalysisItemView> addAnalysisItems(
            @RequestBody(required = false) ProductSelectionAnalysisItemCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().addAnalysisItems(attachOperator(command, writableOperatorUserId(request, null)));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/analysis-items/{collectionId}/procurement")
    public ProductSelectionAnalysisItemView updateAnalysisItemProcurement(
            @PathVariable String collectionId,
            @RequestBody(required = false) ProductSelectionAnalysisItemCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().updateAnalysisItemProcurement(
                    collectionId,
                    attachOperator(command, writableOperatorUserId(request, null))
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/source-collections")
    public ProductSelectionSourceCollectionView createSourceCollection(
            @RequestBody ProductSelectionSourceCollectionCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().createSourceCollection(attachOperator(
                    command,
                    writableOperatorUserId(request, command == null ? null : command.getStoreCode())
            ));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/source-collections/plugin-ingest")
    public ProductSelectionPluginIngestResponse pluginIngestSourceCollection(
            @RequestBody ProductSelectionPluginIngestCommand command,
            HttpServletRequest request
    ) {
        try {
            return sourceCollectionService().pluginIngestSourceCollection(
                    attachPluginOperator(
                            command,
                            writableOperatorUserId(request, command == null ? null : command.getStoreCode())
                    )
            );
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/source-collections/plugin-ingest/status")
    public Map<String, Object> pluginIngestStatus(HttpServletRequest request) {
        Long operatorUserId = requireBusinessAccess(request).getSessionUserId();
        sourceCollectionService();
        return Map.of(
                "success", true,
                "capability", "source-collection-plugin-ingest",
                "operatorUserId", operatorUserId
        );
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
                    attachOperator(command, writableOperatorUserId(request, command == null ? null : command.getStoreCode()))
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
            return sourceCollectionService().getSourceCollectionAli1688(collectionId, readableOperatorUserId(request, null));
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
            return sourceCollectionService().recollectSourceCollectionAli1688(collectionId, writableOperatorUserId(request, null));
        } catch (ProductSelectionAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/source-collections/{collectionId}/analysis/ai")
    public ProductSelectionAnalysisView analyzeSourceCollection(
            @PathVariable String collectionId,
            @RequestBody(required = false) ProductSelectionAnalysisCommand command,
            HttpServletRequest request
    ) {
        try {
            return analysisSkill().analyze(collectionId, command, readableOperatorUserId(request, null));
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
            return sourceCollectionService().retryAli1688Collection(taskId, writableOperatorUserId(request, null));
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

    private ProductSelectionAnalysisSkill analysisSkill() {
        ProductSelectionAnalysisSkill skill = productSelectionAnalysisSkillProvider.getIfAvailable();
        if (skill == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "选品 AI 分析 skill 未启用。");
        }
        return skill;
    }

    private ProductSelectionAccessScope readableAccess(HttpServletRequest request, String storeCode) {
        return accessAdapter().requireReadableStore(requireBusinessAccess(request), storeCode);
    }

    private Long readableOperatorUserId(HttpServletRequest request, String storeCode) {
        return readableAccess(request, storeCode).getOperatorUserId();
    }

    private Long writableOperatorUserId(HttpServletRequest request, String storeCode) {
        return accessAdapter().requireWritableStore(requireBusinessAccess(request), storeCode).getOperatorUserId();
    }

    private BusinessAccessContext requireBusinessAccess(HttpServletRequest request) {
        return accessResolver.requireBusinessContext(request, BusinessCapability.PROCUREMENT);
    }

    private ProductSelectionAccessAdapter accessAdapter() {
        ProductSelectionAccessAdapter adapter = accessAdapterProvider.getIfAvailable();
        if (adapter == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "人工选品访问控制未启用。");
        }
        return adapter;
    }

    private String requestedStoreCode(ProductSelectionGroupCommand command) {
        return command == null ? null : command.getStoreCode();
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

    private ProductSelectionPluginIngestCommand attachPluginOperator(
            ProductSelectionPluginIngestCommand command,
            Long operatorUserId
    ) {
        ProductSelectionPluginIngestCommand source = command == null
                ? new ProductSelectionPluginIngestCommand()
                : command;
        source.setOperatorUserId(operatorUserId);
        return source;
    }

    private ProductSelectionAnalysisItemCommand attachOperator(
            ProductSelectionAnalysisItemCommand command,
            Long operatorUserId
    ) {
        ProductSelectionAnalysisItemCommand source = command == null
                ? new ProductSelectionAnalysisItemCommand()
                : command;
        source.setOperatorUserId(operatorUserId);
        return source;
    }

    private ProductSelectionGroupCommand attachOperator(
            ProductSelectionGroupCommand command,
            Long operatorUserId
    ) {
        ProductSelectionGroupCommand source = command == null
                ? new ProductSelectionGroupCommand()
                : command;
        source.setOperatorUserId(operatorUserId);
        return source;
    }

    private ProductSelectionGroupCompetitorCommand attachOperator(
            ProductSelectionGroupCompetitorCommand command,
            Long operatorUserId
    ) {
        ProductSelectionGroupCompetitorCommand source = command == null
                ? new ProductSelectionGroupCompetitorCommand()
                : command;
        source.setOperatorUserId(operatorUserId);
        return source;
    }

    private ProductSelectionGroupProfitSnapshotCommand attachOperator(
            ProductSelectionGroupProfitSnapshotCommand command,
            Long operatorUserId
    ) {
        ProductSelectionGroupProfitSnapshotCommand source = command == null
                ? new ProductSelectionGroupProfitSnapshotCommand()
                : command;
        source.setOperatorUserId(operatorUserId);
        return source;
    }
}
