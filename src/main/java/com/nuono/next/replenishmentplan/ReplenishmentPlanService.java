package com.nuono.next.replenishmentplan;

public interface ReplenishmentPlanService {

    ReplenishmentPlanOverviewView getOverview(ReplenishmentPlanRecords.PlanQuery query);
}
