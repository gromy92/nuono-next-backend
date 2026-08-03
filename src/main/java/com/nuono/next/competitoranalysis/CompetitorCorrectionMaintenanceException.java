package com.nuono.next.competitoranalysis;

final class CompetitorCorrectionMaintenanceException extends IllegalStateException {
    CompetitorCorrectionMaintenanceException(String code) {
        super(code);
    }

    CompetitorCorrectionMaintenanceException(String code, Throwable cause) {
        super(code, cause);
    }
}
