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

    private static final String LOGISTICS_ITEM_TYPE = "logistics_channel_rule";

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
        List<FileParseLogisticsChannelView> channels = items.stream()
                .filter(item -> LOGISTICS_ITEM_TYPE.equals(item.getItemType()))
                .map(item -> toChannelView(item, selectedKeys))
                .collect(Collectors.toList());
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("该版本没有可选择的物流渠道。");
        }
        return channels;
    }

    private FileParseLogisticsChannelView toChannelView(
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
}
