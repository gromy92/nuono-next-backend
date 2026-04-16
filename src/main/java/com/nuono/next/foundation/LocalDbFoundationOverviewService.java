package com.nuono.next.foundation;

import com.nuono.next.infrastructure.mapper.FoundationOverviewMapper;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class LocalDbFoundationOverviewService {

    private final FoundationOverviewMapper foundationOverviewMapper;
    private final LocalDbBootstrapStatusService localDbBootstrapStatusService;

    public LocalDbFoundationOverviewService(
            FoundationOverviewMapper foundationOverviewMapper,
            LocalDbBootstrapStatusService localDbBootstrapStatusService
    ) {
        this.foundationOverviewMapper = foundationOverviewMapper;
        this.localDbBootstrapStatusService = localDbBootstrapStatusService;
    }

    public FoundationOverview buildOverview() {
        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();

        FoundationOverview overview = new FoundationOverview();
        overview.setMode("local-db");
        overview.setReady(inspection.isReady());
        overview.setMissingCoreTables(inspection.getMissingTables());

        if (!inspection.isReady()) {
            overview.setMessage("本地库已启用，但第一批核心表还没有补齐，先执行初始化 SQL。");
            return overview;
        }

        overview.setCounts(foundationOverviewMapper.selectStats());
        overview.setSampleUsers(foundationOverviewMapper.listSampleUsers(8));
        overview.setMessage("已接入本地样本库，可以开始推进登录、用户、角色和店铺主链路。");
        return overview;
    }
}
