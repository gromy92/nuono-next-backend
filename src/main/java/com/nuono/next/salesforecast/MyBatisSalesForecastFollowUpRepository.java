package com.nuono.next.salesforecast;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSalesForecastFollowUpRepository implements SalesForecastFollowUpRepository {

    private final SalesDataMapper mapper;

    public MyBatisSalesForecastFollowUpRepository(SalesDataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SalesForecastFollowUpRecord setMarked(SalesForecastFollowUpCommand command) {
        Long id = mapper.nextSalesForecastFollowUpId();
        mapper.upsertSalesForecastFollowUp(id, command);
        return new SalesForecastFollowUpRecord(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getPartnerSku(),
                command.getSku(),
                command.isMarked(),
                command.getOperatorUserId()
        );
    }

    @Override
    public List<SalesForecastFollowUpRecord> listMarked(SalesForecastQuery query) {
        return mapper.selectSalesForecastFollowUps(query);
    }
}
