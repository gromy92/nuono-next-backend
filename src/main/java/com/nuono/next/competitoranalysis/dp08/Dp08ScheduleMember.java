package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.runtime.OperationCode;

/** Canonical member identity produced from one native DP08 source row. */
final class Dp08ScheduleMember {
    private final Dp08MemberSourceCursor cursor;
    private final Dp08MemberSetBase base;
    private final Dp08MemberSetItem item;
    private final String scopeIdentity;

    private Dp08ScheduleMember(
            Dp08MemberSourceCursor cursor,
            Dp08MemberSetBase base,
            Dp08MemberSetItem item,
            String scopeIdentity
    ) {
        this.cursor = cursor;
        this.base = base;
        this.item = item;
        this.scopeIdentity = scopeIdentity;
    }

    static Dp08ScheduleMember from(Dp08KeywordScopeRow row) {
        Dp08MemberSourceCursor cursor = Dp08MemberSourceCursor.from(row);
        String selfCode = "SELF".equals(row.getTrackedProductType())
                ? row.getTrackedNoonProductCode()
                : null;
        Dp08MemberSetBase base = selfCode == null
                ? null
                : new Dp08MemberSetBase(
                        OperationCode.DP08A,
                        row.getOwnerUserId(),
                        row.getLogicalStoreId(),
                        row.getWatchProductId(),
                        row.getKeywordId(),
                        cursor.store(),
                        cursor.site(),
                        row.getKeyword(),
                        MyBatisDp08ScopeCatalog.locale(row.getLocale(), cursor.site()),
                        selfCode,
                        row.getWatchProductId(),
                        null,
                        cursor.scopeKey()
                );
        String identity = identity(
                OperationCode.DP08A,
                row.getOwnerUserId(),
                row.getLogicalStoreId(),
                row.getWatchProductId(),
                row.getKeywordId(),
                cursor.store(),
                cursor.site(),
                MyBatisDp08ScopeCatalog.requireText(row.getKeyword(), "keyword"),
                MyBatisDp08ScopeCatalog.locale(row.getLocale(), cursor.site()),
                cursor.scopeKey()
        );
        return new Dp08ScheduleMember(
                cursor,
                base,
                Dp08MemberSetItem.keyword(row),
                identity
        );
    }

    static Dp08ScheduleMember from(Dp08ListTargetRow row) {
        Dp08MemberSourceCursor cursor = Dp08MemberSourceCursor.from(row);
        Dp08MemberSetBase base = new Dp08MemberSetBase(
                OperationCode.DP08B,
                row.getOwnerUserId(),
                row.getLogicalStoreId(),
                null,
                null,
                cursor.store(),
                cursor.site(),
                null,
                null,
                cursor.noonCode(),
                row.getWatchProductId(),
                row.getCompetitorProductId(),
                cursor.scopeKey()
        );
        return new Dp08ScheduleMember(
                cursor,
                base,
                Dp08MemberSetItem.list(row),
                scopeIdentity(base)
        );
    }

    static String scopeIdentity(Dp08MemberSetBase base) {
        if (base.operationCode() == OperationCode.DP08A) {
            return identity(
                    base.operationCode(),
                    base.ownerUserId(),
                    base.logicalStoreId(),
                    base.watchProductId(),
                    base.keywordId(),
                    base.storeCode(),
                    base.siteCode(),
                    base.keyword(),
                    base.locale(),
                    base.stableScopeKey()
            );
        }
        return identity(
                base.operationCode(),
                base.ownerUserId(),
                base.logicalStoreId(),
                base.storeCode(),
                base.siteCode(),
                base.noonProductCode(),
                base.stableScopeKey()
        );
    }

    private static String identity(Object... fields) {
        StringBuilder value = new StringBuilder();
        for (Object field : fields) {
            String text = String.valueOf(field);
            value.append(text.length()).append(':').append(text).append('|');
        }
        return value.toString();
    }

    Dp08MemberSourceCursor cursor() {
        return cursor;
    }

    Dp08MemberSetBase base() {
        return base;
    }

    Dp08MemberSetItem item() {
        return item;
    }

    String scopeIdentity() {
        return scopeIdentity;
    }
}
