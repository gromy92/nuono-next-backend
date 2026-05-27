package com.nuono.next.sales;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSalesActivityWindowRepository implements SalesActivityWindowRepository {

    private final SalesDataMapper mapper;

    public MyBatisSalesActivityWindowRepository(SalesDataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SalesActivityWindowRecord save(SalesActivityWindowRecord record) {
        Long id = mapper.nextSalesActivityWindowId();
        mapper.insertSalesActivityWindow(id, record);
        return record.withId(id);
    }

    @Override
    public SalesActivityWindowRecord find(Long id) {
        return mapper.selectSalesActivityWindowById(id);
    }

    @Override
    public void setEnabled(Long id, boolean enabled, Long updatedBy) {
        mapper.updateSalesActivityWindowEnabled(id, enabled, updatedBy);
    }

    @Override
    public List<SalesActivityWindowRecord> listHistory(SalesActivityWindowScope scope) {
        return mapper.selectSalesActivityWindowHistory(scope);
    }

    @Override
    public List<SalesActivityWindowRecord> listActive(SalesActivityWindowScope scope) {
        return mapper.selectActiveSalesActivityWindows(scope);
    }
}
