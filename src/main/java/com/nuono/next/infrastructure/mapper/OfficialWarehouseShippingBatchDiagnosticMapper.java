package com.nuono.next.infrastructure.mapper;

import com.nuono.next.officialwarehouse.OfficialWarehouseShippingBatchDiagnosticRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

public interface OfficialWarehouseShippingBatchDiagnosticMapper {

    @SelectProvider(
            type = OfficialWarehouseShippingBatchDiagnosticSqlProvider.class,
            method = "selectExactBatchDiagnostic"
    )
    OfficialWarehouseShippingBatchDiagnosticRecord selectExactBatchDiagnostic(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keyword") String keyword
    );
}
