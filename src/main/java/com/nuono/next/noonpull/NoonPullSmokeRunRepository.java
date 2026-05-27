package com.nuono.next.noonpull;

import java.util.List;

public interface NoonPullSmokeRunRepository {
    NoonPullSmokeRunRecord save(NoonPullSmokeRunRecord run);

    List<NoonPullSmokeRunRecord> listRecent(int limit);
}
