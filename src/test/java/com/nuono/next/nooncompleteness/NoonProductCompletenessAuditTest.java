package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoonProductCompletenessAuditTest {

    @Test
    void classifiesProductListBaselineFromSiteOffers() {
        assertTrue(new NoonProductCompletenessAudit(3, 2, 0).hasProductListBaseline());
        assertFalse(new NoonProductCompletenessAudit(3, 0, 0).hasProductListBaseline());
    }

    @Test
    void classifiesDetailCoverageAsCompletePartialOrMissing() {
        assertEquals(
                NoonProductCompletenessAudit.DetailCoverage.COMPLETE,
                new NoonProductCompletenessAudit(4, 4, 4).detailCoverage()
        );
        assertEquals(
                NoonProductCompletenessAudit.DetailCoverage.PARTIAL,
                new NoonProductCompletenessAudit(4, 4, 2).detailCoverage()
        );
        assertEquals(
                NoonProductCompletenessAudit.DetailCoverage.MISSING,
                new NoonProductCompletenessAudit(4, 4, 0).detailCoverage()
        );
    }
}
