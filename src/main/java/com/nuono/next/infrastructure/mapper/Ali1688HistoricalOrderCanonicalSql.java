package com.nuono.next.infrastructure.mapper;

/** Shared workbench predicate for one visible header per external 1688 order. */
final class Ali1688HistoricalOrderCanonicalSql {
    private Ali1688HistoricalOrderCanonicalSql() {}

    static final String PREDICATE =
            "  AND id = ("
            + "    SELECT canonical_order.id"
            + "    FROM procurement_ali1688_order_header canonical_order"
            + "    JOIN procurement_ali1688_order_authorization canonical_authorization"
            + "      ON canonical_authorization.id = canonical_order.authorization_id"
            + "     AND canonical_authorization.owner_user_id = canonical_order.owner_user_id"
            + "    WHERE canonical_order.owner_user_id = procurement_ali1688_order_header.owner_user_id"
            + "      AND BINARY canonical_order.provider_order_no = BINARY procurement_ali1688_order_header.provider_order_no"
            + "      AND canonical_order.superseded_by_order_id IS NULL"
            + "      AND canonical_order.is_deleted = b'0'"
            + "      AND canonical_order.authorization_id IN"
            + "      <foreach collection='authorizationIds' item='canonicalAuthorizationId' open='(' separator=',' close=')'>"
            + "        #{canonicalAuthorizationId}"
            + "      </foreach>"
            + "    ORDER BY CASE"
            + "      WHEN canonical_authorization.provider_code = 'ALI1688_OPEN_API' THEN 0"
            + "      WHEN canonical_authorization.provider_code = 'ALI1688_EXCEL_UPLOAD' THEN 1"
            + "      WHEN canonical_authorization.provider_code = 'ALI1688_EXCEL_LOCAL' THEN 2"
            + "      ELSE 3 END,"
            + "      canonical_order.gmt_updated DESC, canonical_order.id DESC"
            + "    LIMIT 1"
            + "  )";
}
