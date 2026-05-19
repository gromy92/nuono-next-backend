package com.nuono.next.logisticsquote;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LogisticsQuoteNoteInterpreterTest {

    @Test
    void shouldDeriveManualRuleAndRestrictionsFromSupplementNote() {
        LogisticsQuoteNoteInterpreter interpreter = new LogisticsQuoteNoteInterpreter();
        LogisticsQuoteNotePreviewView view = interpreter.interpret(
                "单品需要分别加240/方；单箱重量不超40公斤；单边尺寸超100CM以上价格单询；低于10个方以下的货不接报关件"
        );

        Assertions.assertTrue(view.isReady());
        Assertions.assertFalse(view.getRulePreviews().isEmpty());
        Assertions.assertEquals("CBM", view.getRulePreviews().get(0).getBillingUnit());
        Assertions.assertEquals(240d, view.getRulePreviews().get(0).getUnitPrice());
        Assertions.assertTrue(view.getRestrictionPreviews().stream().anyMatch(item -> "MAX_BOX_WEIGHT".equals(item.getRestrictionType())));
        Assertions.assertTrue(view.getRestrictionPreviews().stream().anyMatch(item -> "INQUIRY_REQUIRED".equals(item.getRestrictionType())));
        Assertions.assertTrue(view.getRestrictionPreviews().stream().anyMatch(item -> "CUSTOMS_DECLARATION_LIMIT".equals(item.getRestrictionType())));
    }
}
