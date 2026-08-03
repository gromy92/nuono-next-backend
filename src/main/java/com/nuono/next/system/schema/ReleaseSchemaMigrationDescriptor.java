package com.nuono.next.system.schema;

final class ReleaseSchemaMigrationDescriptor {
    private final int order;
    private final String key;
    private final String kind;
    private final String checksum;
    private final String postcheckChecksum;

    ReleaseSchemaMigrationDescriptor(
            int order,
            String key,
            String kind,
            String checksum,
            String postcheckChecksum
    ) {
        this.order = order;
        this.key = key;
        this.kind = kind;
        this.checksum = checksum;
        this.postcheckChecksum = postcheckChecksum;
    }

    int getOrder() {
        return order;
    }

    String getKey() {
        return key;
    }

    String getKind() {
        return kind;
    }

    String getChecksum() {
        return checksum;
    }

    String getPostcheckChecksum() {
        return postcheckChecksum;
    }
}
