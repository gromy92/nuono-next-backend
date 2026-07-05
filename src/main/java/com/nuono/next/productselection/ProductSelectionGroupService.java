package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

class ProductSelectionGroupService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProductSelectionMapper productSelectionMapper;
    private final ProductSelectionPermissionGuard permissionGuard;
    private final ProductSelectionSourceCollectionCollector sourceCollectionCollector;
    private final ProductSelectionSourceCollectionLocalizer sourceCollectionLocalizer;
    private final ObjectMapper objectMapper;
    private final ProductSelectionGroupViewAssembler groupViewAssembler;
    private final Function<ProductSelectionSourceCollectionRow, ProductSelectionSourceCollectionView> sourceCollectionViewMapper;
    private final BiFunction<ProductSelectionSourceCollectionRow, ProductSelectionSourceCollectionResult, ProductSelectionSourceCollectionView> sourceCollectionSnapshotViewMapper;

    ProductSelectionGroupService(
            ProductSelectionMapper productSelectionMapper,
            ProductSelectionPermissionGuard permissionGuard,
            ProductSelectionSourceCollectionCollector sourceCollectionCollector,
            ProductSelectionSourceCollectionLocalizer sourceCollectionLocalizer,
            ObjectMapper objectMapper,
            Function<ProductSelectionSourceCollectionRow, ProductSelectionSourceCollectionView> sourceCollectionViewMapper,
            BiFunction<ProductSelectionSourceCollectionRow, ProductSelectionSourceCollectionResult, ProductSelectionSourceCollectionView> sourceCollectionSnapshotViewMapper
    ) {
        this.productSelectionMapper = productSelectionMapper;
        this.permissionGuard = permissionGuard;
        this.sourceCollectionCollector = sourceCollectionCollector;
        this.sourceCollectionLocalizer = sourceCollectionLocalizer;
        this.objectMapper = objectMapper;
        this.groupViewAssembler = new ProductSelectionGroupViewAssembler(objectMapper);
        this.sourceCollectionViewMapper = sourceCollectionViewMapper;
        this.sourceCollectionSnapshotViewMapper = sourceCollectionSnapshotViewMapper;
    }

    List<ProductSelectionGroupView> listGroups(
            String storeName,
            String storeCode,
            Long operatorUserId
    ) {
        ProductSelectionStoreScope scope = permissionGuard.requireReadableStore(operatorUserId, storeCode);
        return toGroupViews(productSelectionMapper.listSelectionGroups(
                scope.getLogicalStoreId(),
                siteCodeFromScope(scope),
                100
        ));
    }

    ProductSelectionGroupView getGroup(
            String groupIdValue,
            Long operatorUserId
    ) {
        Long groupId = parseLongId(groupIdValue, "选品组不存在或已被删除。");
        ProductSelectionGroupRow group = productSelectionMapper.selectGroupById(groupId);
        requireGroupVisible(operatorUserId, group);
        return loadGroupView(groupId);
    }

    ProductSelectionGroupView createGroup(ProductSelectionGroupCommand command) {
        ProductSelectionGroupCommand source = command == null
                ? new ProductSelectionGroupCommand()
                : command;
        List<ProductSelectionSourceCollectionRow> sourceCollections = selectableSourceCollections(source);
        if (sourceCollections.isEmpty()) {
            throw new IllegalArgumentException("请选择未入组且采集成功的商品。");
        }
        ProductSelectionSourceCollectionRow first = sourceCollections.get(0);
        Long groupId = productSelectionMapper.nextSelectionGroupId();
        ProductSelectionGroupRow group = new ProductSelectionGroupRow();
        group.setGroupId(groupId);
        group.setGroupNo("PSG-" + groupId);
        group.setOwnerUserId(first.getOwnerUserId());
        group.setLogicalStoreId(first.getLogicalStoreId());
        group.setSiteCode(normalizeSiteCode(first.getSiteCode()));
        group.setGroupName(shrink(defaultText(source.getGroupName(), defaultProjectName(sourceCollections)), 200));
        group.setGroupStatus("active");
        group.setCreatedBy(source.getOperatorUserId());
        group.setUpdatedBy(source.getOperatorUserId());
        productSelectionMapper.insertSelectionGroup(group);

        for (ProductSelectionSourceCollectionRow sourceCollection : sourceCollections) {
            productSelectionMapper.insertSelectionGroupMaterial(
                    newGroupMaterial(productSelectionMapper.nextSelectionGroupMaterialId(), groupId, sourceCollection, source.getOperatorUserId())
            );
        }
        return loadGroupView(groupId);
    }

    ProductSelectionGroupView addGroupMaterials(String groupIdValue, ProductSelectionGroupCommand command) {
        ProductSelectionGroupCommand source = command == null
                ? new ProductSelectionGroupCommand()
                : command;
        Long groupId = parseLongId(groupIdValue, "选品组不存在或已被删除。");
        ProductSelectionGroupRow group = productSelectionMapper.selectGroupById(groupId);
        requireGroupVisible(source.getOperatorUserId(), group);
        List<ProductSelectionSourceCollectionRow> sourceCollections = selectableSourceCollections(source);
        if (sourceCollections.isEmpty()) {
            throw new IllegalArgumentException("请选择未入组且采集成功的商品。");
        }
        for (ProductSelectionSourceCollectionRow sourceCollection : sourceCollections) {
            String sourceSiteCode = normalizeSiteCode(sourceCollection.getSiteCode());
            String groupSiteCode = normalizeSiteCode(group.getSiteCode());
            if (!group.getLogicalStoreId().equals(sourceCollection.getLogicalStoreId())
                    || (StringUtils.hasText(groupSiteCode) && !groupSiteCode.equals(sourceSiteCode))) {
                throw new IllegalArgumentException("只能把同一店铺站点的采集商品加入同一个组。");
            }
            productSelectionMapper.insertSelectionGroupMaterial(
                    newGroupMaterial(productSelectionMapper.nextSelectionGroupMaterialId(), groupId, sourceCollection, source.getOperatorUserId())
            );
        }
        return loadGroupView(groupId);
    }

    ProductSelectionGroupView updateGroupName(
            String groupIdValue,
            ProductSelectionGroupCommand command
    ) {
        ProductSelectionGroupCommand source = command == null
                ? new ProductSelectionGroupCommand()
                : command;
        Long groupId = parseLongId(groupIdValue, "选品组不存在或已被删除。");
        ProductSelectionGroupRow group = productSelectionMapper.selectGroupById(groupId);
        requireGroupVisible(source.getOperatorUserId(), group);
        String groupName = shrink(defaultText(source.getGroupName(), "").trim(), 200);
        if (!StringUtils.hasText(groupName)) {
            throw new IllegalArgumentException("组名不能为空。");
        }
        productSelectionMapper.updateSelectionGroupName(groupId, groupName, source.getOperatorUserId());
        return loadGroupView(groupId);
    }

    ProductSelectionGroupView updateGroupProcurement(
            String groupIdValue,
            ProductSelectionGroupCommand command
    ) {
        ProductSelectionGroupCommand source = command == null
                ? new ProductSelectionGroupCommand()
                : command;
        Long groupId = parseLongId(groupIdValue, "选品组不存在或已被删除。");
        ProductSelectionGroupRow group = productSelectionMapper.selectGroupById(groupId);
        requireGroupVisible(source.getOperatorUserId(), group);
        BigDecimal purchasePrice = source.getPurchasePrice();
        if (purchasePrice != null && purchasePrice.signum() < 0) {
            throw new IllegalArgumentException("采购单价不能小于 0。");
        }
        productSelectionMapper.upsertSelectionGroupProcurement(
                groupId,
                shrink(defaultText(source.getAli1688PurchaseUrl(), ""), 1000),
                purchasePrice,
                source.getOperatorUserId()
        );
        return loadGroupView(groupId);
    }

    ProductSelectionGroupProfitSnapshotView getGroupProfitEstimate(
            String groupIdValue,
            Long operatorUserId
    ) {
        Long groupId = parseLongId(groupIdValue, "选品组不存在或已被删除。");
        ProductSelectionGroupRow group = productSelectionMapper.selectGroupById(groupId);
        requireGroupVisible(operatorUserId, group);
        return groupViewAssembler.toGroupProfitSnapshotView(productSelectionMapper.selectLatestSelectionGroupProfitSnapshot(groupId));
    }

    ProductSelectionGroupProfitSnapshotView saveGroupProfitEstimate(
            String groupIdValue,
            ProductSelectionGroupProfitSnapshotCommand command
    ) {
        ProductSelectionGroupProfitSnapshotCommand source = command == null
                ? new ProductSelectionGroupProfitSnapshotCommand()
                : command;
        Long groupId = parseLongId(groupIdValue, "选品组不存在或已被删除。");
        ProductSelectionGroupRow group = productSelectionMapper.selectGroupById(groupId);
        requireGroupVisible(source.getOperatorUserId(), group);
        ProductSelectionGroupProfitSnapshotRow row = new ProductSelectionGroupProfitSnapshotRow();
        row.setSnapshotId(productSelectionMapper.nextSelectionGroupProfitSnapshotId());
        row.setGroupId(groupId);
        row.setCurrencyCode(shrink(defaultText(source.getCurrencyCode(), "RMB"), 10));
        row.setProfitAmount(source.getProfitAmount());
        row.setProfitMargin(source.getProfitMargin());
        row.setSnapshotJson(writeMapJson(source.getSnapshot()));
        row.setStatus("saved");
        row.setCreatedAt(nowText());
        row.setCreatedBy(source.getOperatorUserId());
        row.setUpdatedBy(source.getOperatorUserId());
        productSelectionMapper.insertSelectionGroupProfitSnapshot(row);
        return groupViewAssembler.toGroupProfitSnapshotView(row);
    }

    ProductSelectionGroupView updateGroupCompetitors(
            String groupIdValue,
            ProductSelectionGroupCompetitorCommand command
    ) {
        ProductSelectionGroupCompetitorCommand source = command == null
                ? new ProductSelectionGroupCompetitorCommand()
                : command;
        Long groupId = parseLongId(groupIdValue, "选品组不存在或已被删除。");
        ProductSelectionGroupRow group = productSelectionMapper.selectGroupById(groupId);
        requireGroupVisible(source.getOperatorUserId(), group);
        productSelectionMapper.softDeleteSelectionGroupCompetitors(groupId, source.getOperatorUserId());
        Set<String> seenUrls = new LinkedHashSet<>();
        for (ProductSelectionAnalysisCommand.CompetitorContext competitor : source.getCompetitors()) {
            String url = shrink(defaultText(competitor.getUrl(), "").trim(), 1000);
            if (!StringUtils.hasText(url) || !seenUrls.add(url)) {
                continue;
            }
            ProductSelectionAnalysisCommand.CompetitorContext fetchedCompetitor =
                    fetchGroupCompetitorSnapshot(competitor, url, group);
            ProductSelectionGroupCompetitorRow row = new ProductSelectionGroupCompetitorRow();
            row.setCompetitorId(productSelectionMapper.nextSelectionGroupCompetitorId());
            row.setGroupId(groupId);
            row.setCompetitorUrl(url);
            row.setNote(shrink(defaultText(fetchedCompetitor.getNote(), ""), 500));
            row.setFetchStatus(shrink(defaultText(fetchedCompetitor.getFetchStatus(), "pending"), 30));
            row.setFetchedPayloadJson(writeCompetitorPayload(fetchedCompetitor));
            row.setFetchedAt("success".equals(row.getFetchStatus())
                    ? defaultText(fetchedCompetitor.getFetchedAt(), nowText())
                    : null);
            row.setCreatedBy(source.getOperatorUserId());
            row.setUpdatedBy(source.getOperatorUserId());
            productSelectionMapper.insertSelectionGroupCompetitor(row);
        }
        return loadGroupView(groupId);
    }

    ProductSelectionGroupView recollectGroupCompetitor(
            String groupIdValue,
            String competitorIdValue,
            Long operatorUserId
    ) {
        Long groupId = parseLongId(groupIdValue, "选品组不存在或已被删除。");
        Long competitorId = parseLongId(competitorIdValue, "竞品不存在或已被删除。");
        ProductSelectionGroupRow group = productSelectionMapper.selectGroupById(groupId);
        requireGroupVisible(operatorUserId, group);
        ProductSelectionGroupCompetitorRow existing =
                productSelectionMapper.selectGroupCompetitorById(groupId, competitorId);
        if (existing == null) {
            throw new IllegalArgumentException("竞品不存在或已被删除。");
        }
        String url = shrink(defaultText(existing.getCompetitorUrl(), "").trim(), 1000);
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("竞品缺少链接，不能重新采集。");
        }

        ProductSelectionAnalysisCommand.CompetitorContext source =
                copyCompetitor(readCompetitorPayload(existing.getFetchedPayloadJson()));
        source.setUrl(url);
        source.setNote(defaultText(existing.getNote(), source.getNote()));
        ProductSelectionAnalysisCommand.CompetitorContext fetchedCompetitor =
                fetchGroupCompetitorSnapshot(source, url, group);

        ProductSelectionGroupCompetitorRow updateRow = new ProductSelectionGroupCompetitorRow();
        updateRow.setCompetitorId(competitorId);
        updateRow.setGroupId(groupId);
        updateRow.setCompetitorUrl(url);
        updateRow.setNote(shrink(defaultText(fetchedCompetitor.getNote(), ""), 500));
        updateRow.setFetchStatus(shrink(defaultText(fetchedCompetitor.getFetchStatus(), "pending"), 30));
        updateRow.setFetchedPayloadJson(writeCompetitorPayload(fetchedCompetitor));
        updateRow.setFetchedAt("success".equals(updateRow.getFetchStatus())
                ? defaultText(fetchedCompetitor.getFetchedAt(), nowText())
                : null);
        updateRow.setUpdatedBy(operatorUserId);
        productSelectionMapper.updateSelectionGroupCompetitorSnapshot(updateRow);
        return loadGroupView(groupId);
    }

    ProductSelectionGroupView deleteGroupCompetitor(
            String groupIdValue,
            String competitorIdValue,
            Long operatorUserId
    ) {
        Long groupId = parseLongId(groupIdValue, "选品组不存在或已被删除。");
        Long competitorId = parseLongId(competitorIdValue, "竞品不存在或已被删除。");
        ProductSelectionGroupRow group = productSelectionMapper.selectGroupById(groupId);
        requireGroupVisible(operatorUserId, group);
        ProductSelectionGroupCompetitorRow existing =
                productSelectionMapper.selectGroupCompetitorById(groupId, competitorId);
        if (existing == null) {
            throw new IllegalArgumentException("竞品不存在或已被删除。");
        }
        productSelectionMapper.softDeleteSelectionGroupCompetitor(groupId, competitorId, operatorUserId);
        return loadGroupView(groupId);
    }

    private ProductSelectionAnalysisCommand.CompetitorContext fetchGroupCompetitorSnapshot(
            ProductSelectionAnalysisCommand.CompetitorContext source,
            String url,
            ProductSelectionGroupRow group
    ) {
        ProductSelectionAnalysisCommand.CompetitorContext target = copyCompetitor(source);
        target.setUrl(url);
        if (!StringUtils.hasText(url)) {
            target.setFetchStatus("failed");
            target.setFetchMessage("竞品链接为空。");
            return target;
        }

        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setLogicalStoreId(group.getLogicalStoreId());
        row.setSiteCode(group.getSiteCode());
        row.setSourcePlatform(inferSourcePlatform(url));
        row.setSourceUrl(url);
        row.setPageUrl(url);
        row.setCollectionSource("system");
        row.setSourceTitle(defaultText(target.getFetchedTitle(), ""));
        row.setSourceTitleAr(defaultText(target.getFetchedTitleAr(), ""));
        try {
            ProductSelectionSourceCollectionResult result = sourceCollectionLocalizer.localize(
                    row,
                    sourceCollectionCollector.collect(row)
            );
            ProductSelectionSourceCollectionView view = sourceCollectionSnapshotViewMapper.apply(row, result);
            List<String> imageUrls = normalizeList(result.getImageUrls(), 24);
            if (StringUtils.hasText(result.getSourceImageUrl()) && !imageUrls.contains(result.getSourceImageUrl().trim())) {
                imageUrls.add(0, result.getSourceImageUrl().trim());
            }
            target.setFetchStatus("success");
            target.setFetchedTitle(defaultText(result.getSourceTitle(), target.getFetchedTitle()));
            target.setFetchedTitleAr(defaultText(result.getSourceTitleAr(), target.getFetchedTitleAr()));
            target.setFetchedSourceImageUrl(defaultText(result.getSourceImageUrl(), imageUrls.isEmpty() ? "" : imageUrls.get(0)));
            target.setFetchedImageUrls(imageUrls);
            target.setFetchedDescriptionEn(defaultText(result.getSourceDescriptionEn(), target.getFetchedDescriptionEn()));
            target.setFetchedDescriptionAr(defaultText(result.getSourceDescriptionAr(), target.getFetchedDescriptionAr()));
            target.setFetchedSellingPointsEn(normalizeList(result.getSourceSellingPointsEn(), 12));
            target.setFetchedSellingPointsAr(normalizeList(result.getSourceSellingPointsAr(), 12));
            target.setFetchedSourceHost(defaultText(urlHost(defaultText(result.getPageUrl(), result.getSourceUrl())), urlHost(url)));
            target.setFetchedPriceSummary(defaultText(result.getPriceSummary(), ""));
            target.setFetchedCompleteness(view.getCollectedFieldCount() + "/" + view.getCollectedFieldTotal());
            target.setFetchedCollectionSource("系统采集");
            target.setFetchedAt(nowText());
            target.setFetchMessage("竞品内容已拉取");
        } catch (Exception exception) {
            target.setFetchStatus("failed");
            target.setFetchedSourceHost(defaultText(target.getFetchedSourceHost(), urlHost(url)));
            target.setFetchedPriceSummary(defaultText(target.getFetchedPriceSummary(), ""));
            target.setFetchedCompleteness(defaultText(target.getFetchedCompleteness(), "未采集"));
            target.setFetchedCollectionSource(defaultText(target.getFetchedCollectionSource(), "手动链接"));
            target.setFetchMessage(shrink(defaultText(exception.getMessage(), "竞品内容拉取失败。"), 200));
        }
        return target;
    }

    private ProductSelectionAnalysisCommand.CompetitorContext copyCompetitor(
            ProductSelectionAnalysisCommand.CompetitorContext source
    ) {
        ProductSelectionAnalysisCommand.CompetitorContext target = new ProductSelectionAnalysisCommand.CompetitorContext();
        if (source == null) {
            return target;
        }
        target.setUrl(source.getUrl());
        target.setNote(source.getNote());
        target.setFetchStatus(source.getFetchStatus());
        target.setFetchedTitle(source.getFetchedTitle());
        target.setFetchedTitleAr(source.getFetchedTitleAr());
        target.setFetchedSourceImageUrl(source.getFetchedSourceImageUrl());
        target.setFetchedImageUrls(source.getFetchedImageUrls());
        target.setFetchedDescriptionEn(source.getFetchedDescriptionEn());
        target.setFetchedDescriptionAr(source.getFetchedDescriptionAr());
        target.setFetchedSellingPointsEn(source.getFetchedSellingPointsEn());
        target.setFetchedSellingPointsAr(source.getFetchedSellingPointsAr());
        target.setFetchedSourceHost(source.getFetchedSourceHost());
        target.setFetchedPriceSummary(source.getFetchedPriceSummary());
        target.setFetchedCompleteness(source.getFetchedCompleteness());
        target.setFetchedCollectionSource(source.getFetchedCollectionSource());
        target.setFetchedAt(source.getFetchedAt());
        target.setFetchMessage(source.getFetchMessage());
        return target;
    }

    private ProductSelectionGroupView loadGroupView(Long groupId) {
        ProductSelectionGroupRow group = productSelectionMapper.selectGroupById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("选品组不存在或已被删除。");
        }
        List<ProductSelectionGroupMaterialRow> materials = productSelectionMapper.listGroupMaterialsByGroupIds(List.of(groupId));
        List<ProductSelectionGroupCompetitorRow> competitors = productSelectionMapper.listGroupCompetitorsByGroupIds(List.of(groupId));
        return groupViewAssembler.toGroupView(
                group,
                materials,
                productSelectionMapper.selectGroupProcurementByGroupId(groupId),
                competitors,
                sourceCollectionViewMapper
        );
    }

    private List<ProductSelectionGroupView> toGroupViews(List<ProductSelectionGroupRow> groups) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = groups.stream()
                .map(ProductSelectionGroupRow::getGroupId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toList());
        Map<Long, List<ProductSelectionGroupMaterialRow>> materialsByGroupId = new HashMap<>();
        if (!groupIds.isEmpty()) {
            for (ProductSelectionGroupMaterialRow material : productSelectionMapper.listGroupMaterialsByGroupIds(groupIds)) {
                materialsByGroupId.computeIfAbsent(material.getGroupId(), ignored -> new ArrayList<>()).add(material);
            }
        }
        Map<Long, List<ProductSelectionGroupCompetitorRow>> competitorsByGroupId = new HashMap<>();
        if (!groupIds.isEmpty()) {
            for (ProductSelectionGroupCompetitorRow competitor : productSelectionMapper.listGroupCompetitorsByGroupIds(groupIds)) {
                competitorsByGroupId.computeIfAbsent(competitor.getGroupId(), ignored -> new ArrayList<>()).add(competitor);
            }
        }
        return groups.stream()
                .map(group -> groupViewAssembler.toGroupView(
                        group,
                        materialsByGroupId.getOrDefault(group.getGroupId(), List.of()),
                        productSelectionMapper.selectGroupProcurementByGroupId(group.getGroupId()),
                        competitorsByGroupId.getOrDefault(group.getGroupId(), List.of()),
                        sourceCollectionViewMapper
                ))
                .collect(Collectors.toList());
    }

    private ProductSelectionGroupMaterialRow newGroupMaterial(
            Long materialId,
            Long groupId,
            ProductSelectionSourceCollectionRow sourceCollection,
            Long operatorUserId
    ) {
        ProductSelectionGroupMaterialRow row = new ProductSelectionGroupMaterialRow();
        row.setMaterialId(materialId);
        row.setGroupId(groupId);
        row.setSourceCollectionId(sourceCollection.getId());
        row.setOwnerUserId(sourceCollection.getOwnerUserId());
        row.setLogicalStoreId(sourceCollection.getLogicalStoreId());
        row.setSiteCode(normalizeSiteCode(sourceCollection.getSiteCode()));
        row.setMaterialStatus("active");
        row.setCreatedBy(operatorUserId);
        row.setUpdatedBy(operatorUserId);
        return row;
    }

    private List<ProductSelectionSourceCollectionRow> selectableSourceCollections(ProductSelectionGroupCommand command) {
        List<Long> sourceCollectionIds = normalizeSourceCollectionIds(command.getSourceCollectionIds());
        if (sourceCollectionIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要加入组的商品。");
        }
        List<ProductSelectionSourceCollectionRow> sourceCollections = new ArrayList<>();
        Long logicalStoreId = null;
        String siteCode = null;
        for (Long sourceCollectionId : sourceCollectionIds) {
            ProductSelectionSourceCollectionRow sourceCollection =
                    productSelectionMapper.selectSourceCollectionById(sourceCollectionId);
            requireSourceCollectionVisible(command.getOperatorUserId(), sourceCollection);
            if (!"success".equals(defaultText(sourceCollection.getStatus(), ""))) {
                throw new IllegalArgumentException("采集成功后才能加入组。");
            }
            if (productSelectionMapper.selectActiveGroupMaterialBySourceCollectionId(sourceCollectionId) != null) {
                continue;
            }
            String sourceSiteCode = normalizeSiteCode(sourceCollection.getSiteCode());
            if (logicalStoreId == null) {
                logicalStoreId = sourceCollection.getLogicalStoreId();
                siteCode = sourceSiteCode;
            }
            if (!logicalStoreId.equals(sourceCollection.getLogicalStoreId())
                    || (StringUtils.hasText(siteCode) && !siteCode.equals(sourceSiteCode))) {
                throw new IllegalArgumentException("只能把同一店铺站点的采集商品打成一个组。");
            }
            sourceCollections.add(sourceCollection);
        }
        return sourceCollections;
    }

    private void requireSourceCollectionVisible(Long operatorUserId, ProductSelectionSourceCollectionRow row) {
        if (row == null) {
            throw new IllegalArgumentException("采集记录不存在或已被删除。");
        }
        ProductSelectionUserContext user = permissionGuard.requireActiveUser(operatorUserId);
        if (isSuperAdmin(user)) {
            return;
        }
        int visibleSites = productSelectionMapper.countVisibleLogicalStoreSites(
                user.getUserId(),
                row.getLogicalStoreId()
        );
        if (visibleSites <= 0) {
            throw new ProductSelectionAccessDeniedException("当前账号不能访问该采集记录。");
        }
    }

    private void requireGroupVisible(Long operatorUserId, ProductSelectionGroupRow row) {
        if (row == null) {
            throw new IllegalArgumentException("选品组不存在或已被删除。");
        }
        ProductSelectionUserContext user = permissionGuard.requireActiveUser(operatorUserId);
        if (isSuperAdmin(user)) {
            return;
        }
        int visibleSites = productSelectionMapper.countVisibleLogicalStoreSites(
                user.getUserId(),
                row.getLogicalStoreId()
        );
        if (visibleSites <= 0) {
            throw new ProductSelectionAccessDeniedException("当前账号不能访问该选品组。");
        }
    }

    private boolean isSuperAdmin(ProductSelectionUserContext user) {
        return user != null
                && (Integer.valueOf(0).equals(user.getLevel()) || "admin".equalsIgnoreCase(user.getAccountNo()));
    }

    private String inferSourcePlatform(String url) {
        String host = urlHost(url);
        if (hostMatches(host, "noon.com")) {
            return "Noon";
        }
        if (hostMatches(host, "amazon.sa")
                || hostMatches(host, "amazon.ae")
                || hostMatches(host, "amazon.com")
                || hostMatches(host, "amazon.co.uk")) {
            return "Amazon";
        }
        if (hostMatches(host, "shein.com")) {
            return "SHEIN";
        }
        if (hostMatches(host, "temu.com")) {
            return "Temu";
        }
        if (hostMatches(host, "1688.com")) {
            return "1688";
        }
        return "其他";
    }

    private String urlHost(String url) {
        try {
            String value = defaultText(url, "");
            if (!StringUtils.hasText(value)) {
                return "";
            }
            URI uri = URI.create(value);
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        } catch (RuntimeException error) {
            return "";
        }
    }

    private boolean hostMatches(String host, String domain) {
        return host != null && (host.equals(domain) || host.endsWith("." + domain));
    }

    private String siteCodeFromScope(ProductSelectionStoreScope scope) {
        if (scope == null) {
            return "SA";
        }
        String normalizedSite = normalizeSiteCode(scope.getSite());
        if (StringUtils.hasText(normalizedSite)) {
            return normalizedSite;
        }
        return normalizeSiteCode(scope.getStoreCode());
    }

    private String normalizeSiteCode(String value) {
        String normalized = defaultText(value, "").toUpperCase();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.equals("AE")
                || normalized.equals("ARE")
                || normalized.equals("UAE")
                || normalized.contains("-NAE")
                || normalized.endsWith("NAE")
                || normalized.contains("阿联酋")) {
            return "AE";
        }
        if (normalized.equals("SA")
                || normalized.equals("SAU")
                || normalized.equals("KSA")
                || normalized.contains("-NSA")
                || normalized.endsWith("NSA")
                || normalized.contains("沙特")) {
            return "SA";
        }
        return normalized;
    }

    private String writeCompetitorPayload(ProductSelectionAnalysisCommand.CompetitorContext competitor) {
        try {
            return objectMapper.writeValueAsString(competitor == null
                    ? new ProductSelectionAnalysisCommand.CompetitorContext()
                    : competitor);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private ProductSelectionAnalysisCommand.CompetitorContext readCompetitorPayload(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProductSelectionAnalysisCommand.CompetitorContext.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private String writeMapJson(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? Map.of() : values);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private List<String> normalizeList(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(Math.max(0, limit))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Long> normalizeSourceCollectionIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalizedIds = new LinkedHashSet<>();
        for (String id : ids) {
            if (StringUtils.hasText(id)) {
                normalizedIds.add(parseLongId(id, "采集记录不存在或已被删除。"));
            }
        }
        return new ArrayList<>(normalizedIds);
    }

    private String defaultProjectName(List<ProductSelectionSourceCollectionRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "未命名选品项目";
        }
        return rows.stream()
                .map(this::defaultProjectName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("未命名选品项目");
    }

    private String defaultProjectName(ProductSelectionSourceCollectionRow row) {
        if (row == null) {
            return "";
        }
        return defaultText(row.getSourceTitleCn(), defaultText(row.getSourceTitle(), "未命名选品项目"));
    }

    private Long parseLongId(String id, String message) {
        try {
            return Long.parseLong(defaultText(id, ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String shrink(String value, int maxLength) {
        String text = defaultText(value, "");
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
}
