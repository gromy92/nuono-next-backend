package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.DataPullScope;
import java.util.List;

/** Source of every valid, bound scope for one report operation; there is no ordinary enable flag. */
public interface ExportReportScopeSource {

    List<DataPullScope> listScopes();
}
