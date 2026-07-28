package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface OfficialWarehouseAsnSyncThrottleMapper {
    @Delete({
            "DELETE FROM official_warehouse_asn_sync_throttle",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}",
            "  AND claim_token = #{claimToken}"
    })
    int release(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("claimToken") String claimToken
    );
}
