package com.nuono.next.competitoranalysis;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

class CompetitorCorrectionMaintenanceInterceptor implements HandlerInterceptor {
    private static final String TRANSACTION_ATTRIBUTE =
            CompetitorCorrectionMaintenanceInterceptor.class.getName()
                    + ".transaction";

    private final CompetitorCorrectionWriterFenceGuard fenceGuard;
    private final PlatformTransactionManager transactionManager;

    CompetitorCorrectionMaintenanceInterceptor(
            CompetitorCorrectionWriterFenceGuard fenceGuard,
            PlatformTransactionManager transactionManager
    ) {
        this.fenceGuard = fenceGuard;
        this.transactionManager = transactionManager;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        DefaultTransactionDefinition definition =
                new DefaultTransactionDefinition();
        definition.setName("competitor-correction-maintenance-api");
        definition.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        definition.setReadOnly(false);
        TransactionStatus transaction = null;
        try {
            transaction = transactionManager.getTransaction(definition);
            request.setAttribute(TRANSACTION_ATTRIBUTE, transaction);
            fenceGuard.acquireForWrite();
            return true;
        } catch (CompetitorCorrectionMaintenanceException exception) {
            rollback(transaction);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception.getMessage(),
                    exception
            );
        } catch (RuntimeException exception) {
            rollback(transaction);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    CompetitorCorrectionWriterFenceGuard.UNAVAILABLE_CODE,
                    exception
            );
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        Object value = request.getAttribute(TRANSACTION_ATTRIBUTE);
        request.removeAttribute(TRANSACTION_ATTRIBUTE);
        if (!(value instanceof TransactionStatus)) {
            return;
        }
        TransactionStatus transaction = (TransactionStatus) value;
        if (transaction.isCompleted()) {
            return;
        }
        if (exception == null) {
            transactionManager.commit(transaction);
        } else {
            transactionManager.rollback(transaction);
        }
    }

    private void rollback(TransactionStatus transaction) {
        if (transaction != null && !transaction.isCompleted()) {
            transactionManager.rollback(transaction);
        }
    }
}
