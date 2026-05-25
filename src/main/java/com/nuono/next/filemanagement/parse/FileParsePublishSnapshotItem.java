package com.nuono.next.filemanagement.parse;

class FileParsePublishSnapshotItem {

    private final String itemType;
    private final String naturalKey;
    private final String naturalKeyHash;
    private final String payloadJson;
    private final Long sourceResultItemId;
    private final Integer sortNo;

    private FileParsePublishSnapshotItem(
            String itemType,
            String naturalKey,
            String naturalKeyHash,
            String payloadJson,
            Long sourceResultItemId,
            Integer sortNo
    ) {
        this.itemType = itemType;
        this.naturalKey = naturalKey;
        this.naturalKeyHash = naturalKeyHash;
        this.payloadJson = payloadJson;
        this.sourceResultItemId = sourceResultItemId;
        this.sortNo = sortNo;
    }

    static FileParsePublishSnapshotItem fromBase(FileParseVersionItemRow row, String payloadJson) {
        return new FileParsePublishSnapshotItem(
                row.getItemType(),
                row.getNaturalKey(),
                row.getNaturalKeyHash(),
                payloadJson,
                null,
                row.getSortNo()
        );
    }

    static FileParsePublishSnapshotItem fromResult(FileParseResultItemRow row, String payloadJson, Integer sortNo) {
        return new FileParsePublishSnapshotItem(
                row.getItemType(),
                row.getNaturalKey(),
                row.getNaturalKeyHash(),
                payloadJson,
                row.getId(),
                sortNo
        );
    }

    String getItemType() {
        return itemType;
    }

    String getNaturalKey() {
        return naturalKey;
    }

    String getNaturalKeyHash() {
        return naturalKeyHash;
    }

    String getPayloadJson() {
        return payloadJson;
    }

    Long getSourceResultItemId() {
        return sourceResultItemId;
    }

    Integer getSortNo() {
        return sortNo;
    }
}
