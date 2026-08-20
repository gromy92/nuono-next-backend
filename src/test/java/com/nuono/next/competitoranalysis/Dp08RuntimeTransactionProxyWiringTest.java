package com.nuono.next.competitoranalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nuono.next.datapull.leader.MyBatisDataPullRuntimeLeaderStore;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.report.MyBatisReportCreateAttemptFence;
import com.nuono.next.datapull.scope.MyBatisDataPullScopeBindingStore;
import com.nuono.next.datapull.snapshot.MyBatisSnapshotStageStore;
import com.nuono.next.datapull.snapshot.SnapshotCurrentFactStore;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorListingObservationMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeBindingMapper;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import com.nuono.next.infrastructure.mapper.Dp08RuntimeMapper;
import com.nuono.next.infrastructure.mapper.Dp08ScheduleEvidenceMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

class Dp08RuntimeTransactionProxyWiringTest {

    @Test
    void runtimeTransactionComponentsCanBeProxiedByTheProductionProxyMode() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local-db",
                        DataPullExecutionMode.PROPERTY + "=RUNTIME"
                )
                .withBean(PlatformTransactionManager.class,
                        () -> mock(PlatformTransactionManager.class))
                .withBean(Dp08RuntimeMapper.class, () -> mock(Dp08RuntimeMapper.class))
                .withBean(Dp08MemberSetMapper.class, () -> mock(Dp08MemberSetMapper.class))
                .withBean(Dp08ScheduleEvidenceMapper.class,
                        () -> mock(Dp08ScheduleEvidenceMapper.class))
                .withBean(CompetitorAnalysisMapper.class,
                        () -> mock(CompetitorAnalysisMapper.class))
                .withBean(CompetitorListingObservationMapper.class,
                        () -> mock(CompetitorListingObservationMapper.class))
                .withBean(CompetitorProductSnapshotService.class,
                        () -> mock(CompetitorProductSnapshotService.class))
                .withBean(Dp08ImmutableRankingPageWriter.class,
                        () -> mock(Dp08ImmutableRankingPageWriter.class))
                .withBean(DataPullScopeBindingMapper.class,
                        () -> mock(DataPullScopeBindingMapper.class))
                .withUserConfiguration(TransactionComponents.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Dp08EvidenceBatchTransaction.class);
                    assertThat(context).hasSingleBean(Dp08RankingFactTransaction.class);
                    assertThat(context).hasSingleBean(Dp08ListFactTransaction.class);
                    assertThat(context).hasSingleBean(MyBatisDataPullScopeBindingStore.class);
                    assertThat(AopUtils.isAopProxy(
                            context.getBean(Dp08EvidenceBatchTransaction.class))).isTrue();
                    assertThat(AopUtils.isAopProxy(
                            context.getBean(Dp08RankingFactTransaction.class))).isTrue();
                    assertThat(AopUtils.isAopProxy(
                            context.getBean(Dp08ListFactTransaction.class))).isTrue();
                    assertThat(AopUtils.isAopProxy(
                            context.getBean(MyBatisDataPullScopeBindingStore.class))).isTrue();
                });
    }

    @Test
    void runtimeTransactionTypesRemainSubclassableWithPublicTransactionMethods() {
        List<Class<?>> transactionTypes = List.of(
                Dp08EvidenceBatchTransaction.class,
                Dp08RankingFactTransaction.class,
                Dp08ListFactTransaction.class,
                MyBatisDataPullScopeBindingStore.class,
                MyBatisDataPullRuntimeLeaderStore.class,
                MyBatisReportCreateAttemptFence.class,
                MyBatisSnapshotStageStore.class,
                SnapshotCurrentFactStore.class
        );

        for (Class<?> type : transactionTypes) {
            assertThat(Modifier.isFinal(type.getModifiers()))
                    .as("%s must support the production CGLIB transaction proxy", type.getName())
                    .isFalse();
            List<Method> transactionMethods = List.of(type.getDeclaredMethods()).stream()
                    .filter(method -> method.isAnnotationPresent(Transactional.class))
                    .collect(java.util.stream.Collectors.toList());
            assertThat(transactionMethods).as(type.getName()).isNotEmpty();
            assertThat(transactionMethods).allSatisfy(method -> assertThat(
                    Modifier.isPublic(method.getModifiers())
            ).as(method.toGenericString()).isTrue());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({
            Dp08EvidenceBatchTransaction.class,
            Dp08RankingFactTransaction.class,
            Dp08ListFactTransaction.class,
            MyBatisDataPullScopeBindingStore.class
    })
    static class TransactionComponents {
    }
}
