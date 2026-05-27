package com.nuono.next.noonpull;

public interface NoonOrderFactWriter {
    void upsertLine(NoonOrderLineFact fact);
}
