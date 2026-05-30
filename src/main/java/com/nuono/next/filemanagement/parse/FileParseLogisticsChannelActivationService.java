package com.nuono.next.filemanagement.parse;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class FileParseLogisticsChannelActivationService {

    private static final String LEGACY_ITEM_TYPE = FileParseLogisticsQuoteStandard.LEGACY_CHANNEL_RULE;
    private static final String SERVICE_LINE_ITEM_TYPE = FileParseLogisticsQuoteStandard.SERVICE_LINE;
    private static final Set<String> QUOTE_PACKAGE_ITEM_TYPES = Set.of(
            FileParseLogisticsQuoteStandard.CARGO_CATEGORY,
            FileParseLogisticsQuoteStandard.BASE_PRICE,
            FileParseLogisticsQuoteStandard.SURCHARGE,
            FileParseLogisticsQuoteStandard.BILLING_RULE,
            FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE,
            FileParseLogisticsQuoteStandard.RESTRICTION
    );

    private final FileManagementParseMapper fileManagementParseMapper;
    private final FileParseResultItemViewAssembler itemViewAssembler;

    public FileParseLogisticsChannelActivationService(
            FileManagementParseMapper fileManagementParseMapper,
            FileParseResultItemViewAssembler itemViewAssembler
    ) {
        this.fileManagementParseMapper = fileManagementParseMapper;
        this.itemViewAssembler = itemViewAssembler;
    }

    public FileParseLogisticsChannelActivationView listActivations(
            FileParseTargetPlanRow targetPlan,
            FileParseVersionSummaryRow version,
            Long ownerUserId
    ) {
        requireVersionBelongsToPlan(targetPlan, version);
        Set<String> selectedKeys = new LinkedHashSet<>(
                fileManagementParseMapper.selectActiveLogisticsChannelKeys(
                        ownerUserId,
                        targetPlan.getId(),
                        version.getId()
                )
        );
        return buildView(targetPlan, version, ownerUserId, selectedKeys);
    }

    public FileParseLogisticsChannelActivationView saveActivations(
            FileParseTargetPlanRow targetPlan,
            FileParseVersionSummaryRow version,
            Long ownerUserId,
            List<String> requestedChannelKeys,
            Long operatorUserId
    ) {
        requireVersionBelongsToPlan(targetPlan, version);
        List<FileParseLogisticsChannelView> channels = buildChannels(version, Set.of());
        Map<String, FileParseLogisticsChannelView> channelsByKey = new LinkedHashMap<>();
        for (FileParseLogisticsChannelView channel : channels) {
            channelsByKey.put(channel.getChannelKey(), channel);
        }

        Set<String> selectedKeys = normalizeSelectedKeys(requestedChannelKeys);
        for (String selectedKey : selectedKeys) {
            if (!channelsByKey.containsKey(selectedKey)) {
                throw new IllegalArgumentException("物流渠道不存在或不属于该发布版本：" + selectedKey);
            }
        }

        fileManagementParseMapper.softDeleteLogisticsChannelActivations(
                ownerUserId,
                targetPlan.getId(),
                operatorUserId
        );
        for (String selectedKey : selectedKeys) {
            FileParseLogisticsChannelView channel = channelsByKey.get(selectedKey);
            Long id = fileManagementParseMapper.nextLogisticsChannelActivationId();
            fileManagementParseMapper.insertLogisticsChannelActivation(
                    id,
                    targetPlan.getId(),
                    version.getId(),
                    channel.getVersionItemId(),
                    ownerUserId,
                    channel.getChannelKey(),
                    channel.getNaturalKey(),
                    channel.getNaturalKeyHash(),
                    operatorUserId
            );
        }

        return buildView(targetPlan, version, ownerUserId, selectedKeys);
    }

    private FileParseLogisticsChannelActivationView buildView(
            FileParseTargetPlanRow targetPlan,
            FileParseVersionSummaryRow version,
            Long ownerUserId,
            Set<String> selectedKeys
    ) {
        FileParseLogisticsChannelActivationView view = new FileParseLogisticsChannelActivationView();
        view.setTargetPlanId(targetPlan.getId());
        view.setTargetPlanCode(targetPlan.getCode());
        view.setTargetPlanLabel(targetPlan.getLabel());
        view.setVersionId(version.getId());
        view.setVersionNo(version.getVersionNo());
        view.setOwnerUserId(ownerUserId);
        view.setSelectedChannelKeys(new ArrayList<>(selectedKeys));
        view.setChannels(buildChannels(version, selectedKeys));
        return view;
    }

    private List<FileParseLogisticsChannelView> buildChannels(
            FileParseVersionSummaryRow version,
            Set<String> selectedKeys
    ) {
        List<FileParseVersionItemRow> items = fileManagementParseMapper.selectVersionItems(version.getId());
        List<FileParseLogisticsChannelView> serviceLineChannels = items.stream()
                .filter(item -> SERVICE_LINE_ITEM_TYPE.equals(item.getItemType()))
                .map(item -> toServiceLineChannelView(item, selectedKeys, items))
                .collect(Collectors.toList());
        List<FileParseLogisticsChannelView> channels = serviceLineChannels.isEmpty()
                ? items.stream()
                        .filter(item -> LEGACY_ITEM_TYPE.equals(item.getItemType()))
                        .map(item -> toLegacyChannelView(item, selectedKeys))
                        .collect(Collectors.toList())
                : serviceLineChannels;
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("该版本没有可选择的物流渠道。");
        }
        return channels;
    }

    private FileParseLogisticsChannelView toLegacyChannelView(
            FileParseVersionItemRow item,
            Set<String> selectedKeys
    ) {
        Map<String, Object> fields = itemViewAssembler.readMap(item.getVersionPayloadJson());
        String channelKey = firstText(fields.get("channelKey"), item.getNaturalKey(), item.getNaturalKeyHash());
        FileParseLogisticsChannelView view = new FileParseLogisticsChannelView();
        view.setVersionItemId(item.getId());
        view.setNaturalKey(item.getNaturalKey());
        view.setNaturalKeyHash(item.getNaturalKeyHash());
        view.setChannelKey(channelKey);
        view.setCountry(text(fields.get("country")));
        view.setCity(text(fields.get("city")));
        view.setShippingMethod(text(fields.get("shippingMethod")));
        view.setFeeItem(text(fields.get("feeItem")));
        view.setBillingRule(text(fields.get("billingRule")));
        view.setLeadTime(text(fields.get("leadTime")));
        view.setSelected(selectedKeys.contains(channelKey));
        view.setFields(fields);
        return view;
    }

    private FileParseLogisticsChannelView toServiceLineChannelView(
            FileParseVersionItemRow item,
            Set<String> selectedKeys,
            List<FileParseVersionItemRow> versionItems
    ) {
        Map<String, Object> fields = new LinkedHashMap<>(itemViewAssembler.readMap(item.getVersionPayloadJson()));
        String channelKey = firstText(fields.get("serviceLineKey"), item.getNaturalKey(), item.getNaturalKeyHash());
        fields.put("itemType", SERVICE_LINE_ITEM_TYPE);
        fields.put("relatedItemCounts", relatedItemCounts(channelKey, item.getNaturalKey(), fields, versionItems));

        FileParseLogisticsChannelView view = new FileParseLogisticsChannelView();
        view.setVersionItemId(item.getId());
        view.setNaturalKey(item.getNaturalKey());
        view.setNaturalKeyHash(item.getNaturalKeyHash());
        view.setChannelKey(channelKey);
        view.setCountry(text(fields.get("country")));
        view.setCity(firstText(fields.get("destinationNode"), fields.get("destinationWarehouse")));
        view.setShippingMethod(text(fields.get("transportMode")));
        view.setFeeItem(text(fields.get("serviceScope")));
        view.setBillingRule(firstText(fields.get("departureFrequency"), fields.get("sourceVersion")));
        view.setLeadTime(text(fields.get("leadTimeText")));
        view.setSelected(selectedKeys.contains(channelKey));
        view.setFields(fields);
        return view;
    }

    private Map<String, Integer> relatedItemCounts(
            String channelKey,
            String serviceLineNaturalKey,
            Map<String, Object> serviceLineFields,
            List<FileParseVersionItemRow> versionItems
    ) {
        Set<String> acceptableKeys = new LinkedHashSet<>();
        if (StringUtils.hasText(channelKey)) {
            acceptableKeys.add(channelKey);
        }
        if (StringUtils.hasText(serviceLineNaturalKey)) {
            acceptableKeys.add(serviceLineNaturalKey);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String itemType : QUOTE_PACKAGE_ITEM_TYPES) {
            counts.put(itemType, 0);
        }
        for (FileParseVersionItemRow relatedItem : versionItems) {
            if (!QUOTE_PACKAGE_ITEM_TYPES.contains(relatedItem.getItemType())) {
                continue;
            }
            Map<String, Object> relatedFields = itemViewAssembler.readMap(relatedItem.getVersionPayloadJson());
            if (matchesServiceLine(relatedItem, relatedFields, acceptableKeys, serviceLineFields)) {
                counts.computeIfPresent(relatedItem.getItemType(), (key, count) -> count + 1);
            }
        }
        return counts;
    }

    private boolean matchesServiceLine(
            FileParseVersionItemRow relatedItem,
            Map<String, Object> relatedFields,
            Set<String> acceptableKeys,
            Map<String, Object> serviceLineFields
    ) {
        String relatedServiceLineKey = firstText(relatedFields.get("serviceLineKey"), relatedItem.getNaturalKey());
        if (acceptableKeys.contains(relatedServiceLineKey)) {
            return true;
        }
        if (!FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE.equals(relatedItem.getItemType())) {
            return false;
        }

        String relatedCountry = text(relatedFields.get("country"));
        String serviceLineCountry = text(serviceLineFields.get("country"));
        if (StringUtils.hasText(relatedCountry)
                && StringUtils.hasText(serviceLineCountry)
                && !relatedCountry.equalsIgnoreCase(serviceLineCountry)) {
            return false;
        }

        Set<String> serviceLineWarehouseNodes = new LinkedHashSet<>();
        addText(serviceLineWarehouseNodes, serviceLineFields.get("destinationNode"));
        addText(serviceLineWarehouseNodes, serviceLineFields.get("destinationWarehouse"));
        addText(serviceLineWarehouseNodes, serviceLineFields.get("originWarehouse"));
        String warehouseNode = text(relatedFields.get("warehouseNode"));
        return StringUtils.hasText(warehouseNode) && serviceLineWarehouseNodes.contains(warehouseNode);
    }

    private void addText(Set<String> values, Object value) {
        String text = text(value);
        if (StringUtils.hasText(text)) {
            values.add(text);
        }
    }

    private Set<String> normalizeSelectedKeys(List<String> requestedChannelKeys) {
        Set<String> selectedKeys = new LinkedHashSet<>();
        if (requestedChannelKeys == null) {
            return selectedKeys;
        }
        if (requestedChannelKeys.size() > 100) {
            throw new IllegalArgumentException("单次最多选择 100 个物流渠道。");
        }
        for (String key : requestedChannelKeys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            selectedKeys.add(key.trim());
        }
        return selectedKeys;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    // 数据一致性深度防御：service 自身保证 version 归属 targetPlan，不依赖编排层
    // （orchestrator.requireLogisticsVersion 已有的同名校验）。防止未来绕过编排层直接
    // 调用本 service、或编排重构漏校验时，softDelete/insert 写到错位的 plan/version 组合。
    private void requireVersionBelongsToPlan(
            FileParseTargetPlanRow targetPlan,
            FileParseVersionSummaryRow version
    ) {
        if (version == null
                || version.getTargetPlanId() == null
                || !version.getTargetPlanId().equals(targetPlan.getId())) {
            throw new IllegalArgumentException(
                    "发布版本不属于该目标输出方案，已阻止跨方案写入：version="
                            + (version == null ? "null" : version.getId())
                            + " plan=" + targetPlan.getId());
        }
    }
}
