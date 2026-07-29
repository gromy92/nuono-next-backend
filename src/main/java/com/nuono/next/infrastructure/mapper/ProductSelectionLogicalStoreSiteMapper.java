package com.nuono.next.infrastructure.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductSelectionLogicalStoreSiteMapper {

    @Select({
            "SELECT lss.store_code",
            "FROM logical_store_site lss",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = b'0'",
            "WHERE lss.logical_store_id = #{logicalStoreId}",
            "  AND lss.is_deleted = b'0'",
            "  AND CASE",
            "    WHEN UPPER(TRIM(COALESCE(NULLIF(TRIM(lss.site), ''), lss.store_code)))",
            "           IN ('AE', 'ARE', 'UAE', 'NAE')",
            "      OR UPPER(TRIM(COALESCE(NULLIF(TRIM(lss.site), ''), lss.store_code))) LIKE '%-NAE'",
            "      OR lss.site LIKE '%阿联酋%' THEN 'AE'",
            "    WHEN UPPER(TRIM(COALESCE(NULLIF(TRIM(lss.site), ''), lss.store_code)))",
            "           IN ('SA', 'SAU', 'KSA', 'NSA')",
            "      OR UPPER(TRIM(COALESCE(NULLIF(TRIM(lss.site), ''), lss.store_code))) LIKE '%-NSA'",
            "      OR lss.site LIKE '%沙特%' THEN 'SA'",
            "    ELSE UPPER(TRIM(COALESCE(NULLIF(TRIM(lss.site), ''), lss.store_code)))",
            "  END = #{siteCode}",
            "ORDER BY lss.is_reference_site DESC, lss.id ASC"
    })
    List<String> listLogicalStoreSiteCodes(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("siteCode") String siteCode
    );
}
