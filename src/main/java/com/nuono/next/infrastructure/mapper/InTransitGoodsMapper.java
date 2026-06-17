package com.nuono.next.infrastructure.mapper;

public interface InTransitGoodsMapper extends
        InTransitForwarderMapper,
        InTransitBatchMapper,
        InTransitGoodsLineMapper,
        InTransitLogisticsNodeMapper,
        InTransitImportAuditMapper {
}
