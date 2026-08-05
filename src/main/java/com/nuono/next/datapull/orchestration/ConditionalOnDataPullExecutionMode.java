package com.nuono.next.datapull.orchestration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/** Selects exactly one automatic data-pull implementation for a process. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(DataPullExecutionModeCondition.class)
public @interface ConditionalOnDataPullExecutionMode {
    DataPullExecutionMode value();
}
