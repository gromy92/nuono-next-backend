package com.nuono.next.competitoranalysis.dp08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.runtime.OperationCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class Dp08MemberSetHandleTest {
    @Test
    void taskHandleSizeIsConstantWhenTheMemberCountIsHuge() {
        Dp08MemberSetBase base=new Dp08MemberSetBase(
                OperationCode.DP08B,307L,10L,null,null,"STORE","SA",null,null,
                "ZABC123",99L,null,"dp08b-scope"
        );
        Dp08MemberSetHandleCodec codec=new Dp08MemberSetHandleCodec(new ObjectMapper());
        Dp08MemberSetHandle small=codec.seal(base,1,"a".repeat(64),
                LocalDateTime.of(2026,8,4,0,0));
        Dp08MemberSetHandle huge=codec.seal(base,10_000_000L,"b".repeat(64),
                LocalDateTime.of(2026,8,4,0,0));

        int smallBytes=codec.encode(small).getBytes(StandardCharsets.UTF_8).length;
        int hugeBytes=codec.encode(huge).getBytes(StandardCharsets.UTF_8).length;

        assertThat(hugeBytes).isLessThan(Dp08MemberSetHandleCodec.MAX_PAYLOAD_BYTES);
        assertThat(hugeBytes-smallBytes).isLessThan(16);
    }

    @Test
    void copyResumeCursorAdvancesScheduleProgressButPreservesTheNativeKeyset() {
        Dp08ListTargetRow row=new Dp08ListTargetRow();
        row.setOwnerUserId(307L);row.setStoreCode("STORE");row.setSiteCode("SA");
        row.setNoonProductCode("ZABC123");row.setWatchProductId(99L);
        Dp08MemberSourceCursor nativeCursor=Dp08MemberSourceCursor.from(row);
        String nativeValue=nativeCursor.encode();
        String resumed=Dp08MemberSourceCursor.resume(nativeValue,"COPY:00000000000000000099");

        Dp08MemberSourceCursor parsed=Dp08MemberSourceCursor.parse(OperationCode.DP08B,resumed);

        assertThat(resumed).isNotEqualTo(nativeValue);
        assertThat(parsed.encode()).isEqualTo(nativeValue);
        assertThat(parsed.scopeKey()).isEqualTo(nativeCursor.scopeKey());
        assertThatThrownBy(() -> Dp08MemberSourceCursor.parse(
                OperationCode.DP08B,nativeValue+":R:"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
