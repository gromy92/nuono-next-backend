package com.nuono.next.infrastructure.mapper;

final class NoonAuthRecoverySql {
    static final String PROJECT_BINDING_FINGERPRINT = "SHA2(CONCAT("
            + "'cookie#', CHAR_LENGTH(COALESCE(up.noon_partner_cookie, '')), ':', "
            + "COALESCE(up.noon_partner_cookie, ''), "
            + "'|user#', COALESCE(up.noon_partner_user, ''), "
            + "'|partner#', COALESCE(up.noon_partner_id, ''), "
            + "'|bind#', COALESCE(CAST(up.bind_status AS CHAR), 'NULL'), "
            + "'|authorized#', COALESCE(CAST(up.is_authorized AS CHAR), 'NULL'), "
            + "'|deleted#', COALESCE(CAST(up.is_deleted AS CHAR), 'NULL')"
            + "), 256)";

    private NoonAuthRecoverySql() { }
}
