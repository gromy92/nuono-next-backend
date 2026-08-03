package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.lang.reflect.Method;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class CompetitorCorrectionWriterFenceGuardTest {
    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void writerTakesSharedLockOnTheFixedFenceRow() throws Exception {
        Method method = CompetitorAnalysisMapper.class.getMethod(
                "lockCompetitorCorrectionWriterFence"
        );
        String sql = normalized(
                String.join(" ", method.getAnnotation(Select.class).value())
        );

        assertTrue(sql.contains(
                "FENCE_NAME = 'HISTORICAL_BUSINESS_DATE_CORRECTION'"
        ));
        assertTrue(sql.contains("ELSE 'INVALID' END"));
        assertTrue(sql.contains("REOPENED_BY IS NOT NULL"));
        assertTrue(sql.contains("REOPENED_AT IS NOT NULL"));
        assertTrue(sql.contains("LIMIT 1 FOR SHARE"));
    }

    @Test
    void openWriterFenceRequiresTransactionAndHoldsDatabaseLock() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        when(mapper.lockCompetitorCorrectionWriterFence()).thenReturn("OPEN");
        CompetitorCorrectionWriterFenceGuard guard =
                new CompetitorCorrectionWriterFenceGuard(mapper);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        guard.acquireForWrite();

        verify(mapper).lockCompetitorCorrectionWriterFence();
    }

    @Test
    void activeOrMissingFenceFailsClosed() {
        CompetitorAnalysisMapper activeMapper =
                mock(CompetitorAnalysisMapper.class);
        when(activeMapper.lockCompetitorCorrectionWriterFence())
                .thenReturn("ACTIVE");
        TransactionSynchronizationManager.setActualTransactionActive(true);

        CompetitorCorrectionMaintenanceException active = assertThrows(
                CompetitorCorrectionMaintenanceException.class,
                () -> new CompetitorCorrectionWriterFenceGuard(activeMapper)
                        .acquireForWrite()
        );
        assertEquals(
                CompetitorCorrectionWriterFenceGuard.ACTIVE_CODE,
                active.getMessage()
        );

        CompetitorAnalysisMapper missingMapper =
                mock(CompetitorAnalysisMapper.class);
        CompetitorCorrectionMaintenanceException missing = assertThrows(
                CompetitorCorrectionMaintenanceException.class,
                () -> new CompetitorCorrectionWriterFenceGuard(missingMapper)
                        .acquireForWrite()
        );
        assertEquals(
                CompetitorCorrectionWriterFenceGuard.UNAVAILABLE_CODE,
                missing.getMessage()
        );

        CompetitorAnalysisMapper invalidMapper =
                mock(CompetitorAnalysisMapper.class);
        when(invalidMapper.lockCompetitorCorrectionWriterFence())
                .thenReturn("INVALID");
        CompetitorCorrectionMaintenanceException invalid = assertThrows(
                CompetitorCorrectionMaintenanceException.class,
                () -> new CompetitorCorrectionWriterFenceGuard(invalidMapper)
                        .acquireForWrite()
        );
        assertEquals(
                CompetitorCorrectionWriterFenceGuard.UNAVAILABLE_CODE,
                invalid.getMessage()
        );
    }

    @Test
    void enabledWriterFenceCannotRunOutsideTransaction() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);

        assertThrows(
                IllegalStateException.class,
                () -> new CompetitorCorrectionWriterFenceGuard(mapper)
                        .acquireForWrite()
        );

        verifyNoInteractions(mapper);
    }

    @Test
    void apiGateRollsBackAndReturnsServiceUnavailableWhileActive() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        when(mapper.lockCompetitorCorrectionWriterFence())
                .thenReturn("ACTIVE");
        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        CompetitorCorrectionMaintenanceInterceptor interceptor =
                new CompetitorCorrectionMaintenanceInterceptor(
                        new CompetitorCorrectionWriterFenceGuard(mapper),
                        transactionManager
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interceptor.preHandle(
                        mock(HttpServletRequest.class), null, new Object()
                )
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertEquals(
                CompetitorCorrectionWriterFenceGuard.ACTIVE_CODE,
                exception.getReason()
        );
        assertEquals(
                "/api/competitor-analysis/**",
                CompetitorCorrectionMaintenanceWebConfig.API_PATH
        );
        verify(transactionManager).rollback(transaction);
    }

    @Test
    void apiGateHoldsSharedLockUntilResponseCompletion() throws Exception {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        when(mapper.lockCompetitorCorrectionWriterFence()).thenReturn("OPEN");
        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(any())).thenReturn(transaction);
        when(request.getMethod()).thenReturn("GET");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        CompetitorCorrectionMaintenanceInterceptor interceptor =
                new CompetitorCorrectionMaintenanceInterceptor(
                        new CompetitorCorrectionWriterFenceGuard(mapper),
                        transactionManager
                );

        assertTrue(interceptor.preHandle(request, null, new Object()));
        interceptor.afterCompletion(request, null, new Object(), null);

        verify(mapper).lockCompetitorCorrectionWriterFence();
        verify(transactionManager).commit(transaction);
        ArgumentCaptor<TransactionDefinition> definition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertEquals(false, definition.getValue().isReadOnly());
        assertEquals(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definition.getValue().getPropagationBehavior()
        );
    }

    @Test
    void mutationApiUsesWritableOuterTransactionForRequiredBusinessWrites() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        when(mapper.lockCompetitorCorrectionWriterFence()).thenReturn("OPEN");
        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        CompetitorCorrectionMaintenanceInterceptor interceptor =
                new CompetitorCorrectionMaintenanceInterceptor(
                        new CompetitorCorrectionWriterFenceGuard(mapper),
                        transactionManager
                );

        assertTrue(interceptor.preHandle(request, null, new Object()));

        ArgumentCaptor<TransactionDefinition> definition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertEquals(false, definition.getValue().isReadOnly());
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
