package com.nuono.next.replenishmentplan;

import com.nuono.next.infrastructure.mapper.ReplenishmentPlanMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisReplenishmentPlanRepository implements ReplenishmentPlanRepository {

    private final ReplenishmentPlanMapper mapper;

    public MyBatisReplenishmentPlanRepository(ReplenishmentPlanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<StockRow> listFbnSupermallStock(Long ownerUserId, String storeCode, String siteCode) {
        return mapper.selectFbnSupermallStock(ownerUserId, storeCode, siteCode);
    }

    @Override
    public List<InboundRow> listActiveInbound(Long ownerUserId, String storeCode, String siteCode) {
        return mapper.selectActiveInbound(ownerUserId, storeCode, siteCode);
    }
}
