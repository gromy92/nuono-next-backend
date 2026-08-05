package com.nuono.next.datapull.orchestration;

import java.util.Map;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class DataPullExecutionModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(
                ConditionalOnDataPullExecutionMode.class.getName()
        );
        if (attributes == null) {
            throw new IllegalStateException("Data-pull execution mode condition has no declaration");
        }
        Object required = attributes.get("value");
        if (!(required instanceof DataPullExecutionMode)) {
            throw new IllegalStateException("Data-pull execution mode condition is invalid");
        }
        return DataPullExecutionMode.resolve(context.getEnvironment()) == required;
    }
}
