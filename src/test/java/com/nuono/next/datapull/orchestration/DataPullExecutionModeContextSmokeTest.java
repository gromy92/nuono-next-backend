package com.nuono.next.datapull.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.DataPullScopeAdmissionStore;
import com.nuono.next.datapull.snapshot.SnapshotFactApplyGuard;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.productpublicdetail.LegacyProductPublicDetailSyncService;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncScheduler;
import com.nuono.next.datapull.wiring.ScheduleRuntimeConfiguration;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

class DataPullExecutionModeContextSmokeTest {

    @Test
    void legacyModeRefreshesOnlyTheLegacyAutomaticEntry() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local-db",
                        DataPullExecutionMode.PROPERTY + "=LEGACY"
                )
                .withBean(ProductPublicDetailMapper.class,
                        () -> mock(ProductPublicDetailMapper.class))
                .withBean(LegacyProductPublicDetailSyncService.class,
                        () -> mock(LegacyProductPublicDetailSyncService.class))
                .withUserConfiguration(
                        DataPullRuntimeConfiguration.class,
                        ScheduleRuntimeConfiguration.class,
                        ProductPublicDetailSyncScheduler.class
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProductPublicDetailSyncScheduler.class);
                    assertThat(context).doesNotHaveBean(ScheduleReconciler.class);
                    assertThat(context).doesNotHaveBean(DataPullRuntimeScheduler.class);
                });
    }

    @Test
    void runtimeModeRefreshesOnlyTheRuntimeAutomaticEntry() {
        DataPullRuntimeContextFixture.runtime(true)
                .withUserConfiguration(
                        DataPullRuntimeConfiguration.class,
                        ScheduleRuntimeConfiguration.class,
                        ProductPublicDetailSyncScheduler.class
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ScheduleReconciler.class);
                    assertThat(context).hasSingleBean(DataPullRuntimeReconciler.class);
                    assertThat(context.getBean(DataPullRuntimeReconciler.class))
                            .isSameAs(context.getBean(ScheduleReconciler.class));
                    assertThat(context).hasSingleBean(DataPullRuntimeScheduler.class);
                    assertThat(context).doesNotHaveBean(ProductPublicDetailSyncScheduler.class);
                    assertThat(context).doesNotHaveBean(SnapshotFactApplyGuard.class);
                    assertThat(injectedBoundedEngine(context.getBean(ScheduleReconciler.class)))
                            .isSameAs(context.getBean(ScheduleBatchEngine.class));
                });
    }

    @Test
    void runtimeCompositionFailsClosedWhenScheduleConfigurationIsMissing() {
        DataPullRuntimeContextFixture.runtime(false)
                .withUserConfiguration(DataPullRuntimeConfiguration.class)
                .run(context -> {
                    Throwable root = rootCause(context.getStartupFailure());

                    assertThat(root).isInstanceOf(NoSuchBeanDefinitionException.class);
                    assertThat(root.getMessage())
                            .contains(DataPullRuntimeReconciler.class.getName());
                });
    }

    @Test
    void productionScheduleFactoryRequiresBoundedEngineAndCallsRegistryCompleteness()
            throws Exception {
        Method factory = ScheduleRuntimeConfiguration.class.getDeclaredMethod(
                "dataPullScheduleReconciler",
                DataPullScheduleRegistry.class,
                DataPullJobRegistry.class,
                DataPullTaskStore.class,
                DataPullScheduleAnchorStore.class,
                DataPullScopeAdmissionStore.class,
                PlatformTransactionManager.class,
                ScheduleBatchEngine.class,
                DataPullRuntimeTechnicalHealth.class
        );

        assertThat(factory.getAnnotation(Bean.class)).isNotNull();
        assertThat(countExactRegistryCompletenessCalls(factory)).isEqualTo(1);
    }

    private static Object injectedBoundedEngine(ScheduleReconciler reconciler) {
        try {
            Field field = ScheduleReconciler.class.getDeclaredField("boundedEngine");
            field.setAccessible(true);
            return field.get(reconciler);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("ScheduleReconciler bounded engine is not inspectable", failure);
        }
    }

    private static int countExactRegistryCompletenessCalls(Method factory) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String factoryDescriptor = Type.getMethodDescriptor(factory);
        String registryOwner = Type.getInternalName(DataPullScheduleRegistry.class);
        try (InputStream bytecode = ScheduleRuntimeConfiguration.class.getResourceAsStream(
                "ScheduleRuntimeConfiguration.class"
        )) {
            assertThat(bytecode).isNotNull();
            new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    if (!factory.getName().equals(name)
                            || !factoryDescriptor.equals(descriptor)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface
                        ) {
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                    && registryOwner.equals(owner)
                                    && "requireComplete".equals(name)
                                    && "()V".equals(descriptor)) {
                                calls.incrementAndGet();
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return calls.get();
    }

    private static Throwable rootCause(Throwable failure) {
        assertThat(failure).isNotNull();
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }
}
