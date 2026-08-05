package com.nuono.next.noonpull;

import java.util.List;

public interface NoonOrderFactWriter {
    void upsertLine(NoonOrderLineFact fact);

    default void upsertLines(List<NoonOrderLineFact> facts) {
        if (facts == null) {
            return;
        }
        for (NoonOrderLineFact fact : facts) {
            upsertLine(fact);
        }
    }
}
