package com.nuono.next.competitoranalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.CompetitorListCoverageMapper;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class CompetitorListCoverageMapperSqlTest {

    @Test
    void noActiveKeywordsDoesNotBlockExactListCoverage() throws Exception {
        Select select = CompetitorListCoverageMapper.class
                .getMethod(
                        "hasCompleteRankScanCoverage",
                        Long.class,
                        LocalDate.class
                )
                .getAnnotation(Select.class);
        String sql = String.join(" ", select.value())
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertThat(sql)
                .contains("case when not exists")
                .doesNotContain("case when exists");
    }
}
