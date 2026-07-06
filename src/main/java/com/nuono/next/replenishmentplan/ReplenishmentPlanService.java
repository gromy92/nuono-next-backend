package com.nuono.next.replenishmentplan;

public interface ReplenishmentPlanService {

    ReplenishmentPlanRecords.PlanOverviewView getOverview(ReplenishmentPlanRecords.PlanQuery query);
}
