package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InTransitGoodsMapper extends
        InTransitForwarderMapper,
        InTransitBatchMapper,
        InTransitGoodsLineMapper,
        InTransitProductMatchCandidateMapper,
        InTransitLogisticsNodeMapper,
        InTransitImportAuditMapper {

    @Select("SELECT GET_LOCK(#{lockName}, #{timeoutSeconds})")
    Integer acquirePluginSyncBatchLock(
            @Param("lockName") String lockName,
            @Param("timeoutSeconds") int timeoutSeconds
    );

    @Select("SELECT RELEASE_LOCK(#{lockName})")
    Integer releasePluginSyncBatchLock(@Param("lockName") String lockName);
}
