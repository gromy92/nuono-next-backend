package com.nuono.next.filemanagement.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.auth.AuthenticatedSession;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class FileParseTaskCreationTransactionContractTest {

    @Test
    void facadeAndCreationModuleShareRequiredTransactionBoundary() throws Exception {
        Method facadeMethod = LocalDbFileManagementParseService.class.getMethod(
                "createTask",
                AuthenticatedSession.class,
                FileParseCreateTaskCommand.class,
                String.class
        );
        Method moduleMethod = FileParseTaskCreationService.class.getMethod(
                "create",
                FileParseUserContext.class,
                FileParseTargetPlanRow.class,
                FileParseTaskRow.class,
                FileParseCreateTaskCommand.class,
                String.class
        );

        assertRequiredTransaction(facadeMethod);
        assertRequiredTransaction(moduleMethod);
    }

    private void assertRequiredTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
