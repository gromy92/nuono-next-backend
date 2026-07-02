package com.nuono.next.noonads;

import com.nuono.next.infrastructure.mapper.NoonAdvertisingMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisNoonAdvertisingRepository implements NoonAdvertisingRepository, NoonAdvertisingImportRepository {
    private final NoonAdvertisingMapper mapper;

    public MyBatisNoonAdvertisingRepository(NoonAdvertisingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public NoonAdvertisingLatestReportWindowView selectLatestReportWindow(NoonAdvertisingScopeQuery query) {
        return mapper.selectLatestReportWindow(query);
    }

    @Override
    public NoonAdvertisingSummaryView selectAdSummary(NoonAdvertisingDashboardQuery query) {
        return mapper.selectAdSummary(query);
    }

    @Override
    public NoonAdvertisingSalesSummaryView selectSalesSummary(NoonAdvertisingDashboardQuery query) {
        return mapper.selectSalesSummary(query);
    }

    @Override
    public NoonAdvertisingDataStatus selectDataStatus(NoonAdvertisingDashboardQuery query) {
        return mapper.selectDataStatus(query);
    }

    @Override
    public List<NoonAdvertisingCampaignRow> selectCampaignRows(NoonAdvertisingDashboardQuery query) {
        return mapper.selectCampaignRows(query);
    }

    @Override
    public List<NoonAdvertisingProductRow> selectProductRows(NoonAdvertisingDashboardQuery query) {
        return mapper.selectProductRows(query);
    }

    @Override
    public List<NoonAdvertisingQueryRow> selectZeroOrderQueryRows(NoonAdvertisingDashboardQuery query) {
        return mapper.selectZeroOrderQueryRows(query);
    }

    @Override
    public List<NoonAdvertisingQueryRow> selectWinningQueryRows(NoonAdvertisingDashboardQuery query) {
        return mapper.selectWinningQueryRows(query);
    }

    @Override
    public Long nextReportBatchId() {
        return mapper.nextReportBatchId();
    }

    @Override
    public Long nextCampaignFactId() {
        return mapper.nextCampaignFactId();
    }

    @Override
    public Long nextQueryFactId() {
        return mapper.nextQueryFactId();
    }

    @Override
    public Long findReportBatchId(NoonAdvertisingReportBatch batch) {
        return mapper.findReportBatchId(batch);
    }

    @Override
    public void insertReportBatch(NoonAdvertisingReportBatch batch) {
        mapper.insertReportBatch(batch);
    }

    @Override
    public void upsertCampaignFact(NoonAdvertisingCampaignFact fact) {
        mapper.upsertCampaignFact(fact);
    }

    @Override
    public void upsertQueryFact(NoonAdvertisingQueryFact fact) {
        mapper.upsertQueryFact(fact);
    }
}
