package com.nuono.next.competitoranalysis.dp08;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Constant-size resumable digest over the complete ordered DP08 member multiset. */
final class Dp08MemberOrderedDigest {
    private static final String INITIAL = hash("DP08_MEMBER_ORDER_V1");
    private String state;

    private Dp08MemberOrderedDigest(String state) {
        if (state == null || !state.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid DP08 member digest state");
        }
        this.state = state;
    }

    static Dp08MemberOrderedDigest initial() { return new Dp08MemberOrderedDigest(INITIAL); }
    static Dp08MemberOrderedDigest resume(String state) { return new Dp08MemberOrderedDigest(state); }

    Dp08MemberOrderedDigest append(Dp08MemberSetItem item) {
        item.validate();
        state = hash(state + field(item.getMemberKey()) + field(item.getMemberKind())
                + field(Long.toString(item.getWatchProductId()))
                + field(item.getCompetitorProductId() == null
                    ? "" : Long.toString(item.getCompetitorProductId()))
                + field(item.getNoonProductCode()));
        return this;
    }

    String snapshot() { return state; }
    private static String field(String value) { return value.length() + ":" + value + "|"; }
    private static String hash(String value) {
        try {
            byte[] result=MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);
            for(byte item:result)hex.append(String.format("%02x",item&255));
            return hex.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
