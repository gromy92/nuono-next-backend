package com.nuono.next.datapull.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import com.nuono.next.infrastructure.mapper.Dp05RuntimeMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingStageMapper;
import com.nuono.next.infrastructure.mapper.Dp08RuntimeMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class DataPullLeaseSqlTimezoneTest {

    @Test
    void databaseClockLeaseChecksUseUtcForEveryDpSpecificMapper() {
        List<String> leaseQueries = List.of(
                selectSql(CompleteSnapshotStageMapper.class, "selectTaskForUpdate"),
                selectSql(Dp05RuntimeMapper.class, "selectTaskFenceForUpdate"),
                selectSql(Dp05RuntimeMapper.class, "countLiveTaskFence"),
                selectSql(Dp06AdvertisingStageMapper.class, "selectTaskForUpdate"),
                selectSql(Dp06AdvertisingStageMapper.class, "countLiveFence"),
                selectSql(Dp08RuntimeMapper.class, "countLiveRuntimeTask")
        );

        assertThat(leaseQueries)
                .allSatisfy(sql -> assertThat(sql)
                        .contains("lease_until")
                        .contains("UTC_TIMESTAMP(3)")
                        .doesNotContain("NOW(3)"));
    }

    private String selectSql(Class<?> mapperType, String methodName) {
        Method method = Arrays.stream(mapperType.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        return String.join(" ", select.value()).replaceAll("\\s+", " ");
    }
}
