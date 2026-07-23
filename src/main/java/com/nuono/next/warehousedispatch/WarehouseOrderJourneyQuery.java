package com.nuono.next.warehousedispatch;

import com.nuono.next.infrastructure.mapper.WarehouseOrderJourneyMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseOrderJourneyQuery {

    private final WarehouseOrderJourneyMapper mapper;

    public WarehouseOrderJourneyQuery(WarehouseOrderJourneyMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<WarehouseOrderJourneyView> list(BusinessAccessContext access) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        Long ownerUserId = access.getBusinessOwnerUserId() == null
                ? access.getSessionUserId()
                : access.getBusinessOwnerUserId();
        return mapper.listWarehouseOrderJourneys(ownerUserId, access.getStoreCodes());
    }
}
