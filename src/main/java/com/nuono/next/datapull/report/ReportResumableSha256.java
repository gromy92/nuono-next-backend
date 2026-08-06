package com.nuono.next.datapull.report;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Serializable SHA-256 state persisted at each durable report-chunk boundary. */
final class ReportResumableSha256 {
    private static final int[] INITIAL = {
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
            0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    };
    private static final int[] K = {
            0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
            0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
            0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
            0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
            0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
            0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
            0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
            0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    };

    private final int[] hash;
    private final byte[] buffer;
    private int buffered;
    private long byteCount;

    ReportResumableSha256() {
        this(INITIAL.clone(), new byte[64], 0, 0L);
    }

    private ReportResumableSha256(int[] hash, byte[] buffer, int buffered, long byteCount) {
        this.hash = hash;
        this.buffer = buffer;
        this.buffered = buffered;
        this.byteCount = byteCount;
    }

    static ReportResumableSha256 resume(String state) {
        String[] fields = state == null ? new String[0] : state.split(":", -1);
        if (fields.length != 4 || !"v1".equals(fields[0])) {
            throw new IllegalArgumentException("unsupported resumable SHA-256 state");
        }
        final long count;
        try {
            count = Long.parseUnsignedLong(fields[1]);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid resumable SHA-256 byte count", invalid);
        }
        byte[] hashBytes = parseHex(fields[2], 32);
        byte[] pending = parseHex(fields[3], (int) (count & 63L));
        int[] words = new int[8];
        ByteBuffer view = ByteBuffer.wrap(hashBytes);
        for (int index = 0; index < words.length; index++) {
            words[index] = view.getInt();
        }
        byte[] buffer = new byte[64];
        System.arraycopy(pending, 0, buffer, 0, pending.length);
        return new ReportResumableSha256(words, buffer, pending.length, count);
    }

    void update(byte[] value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        byteCount = Math.addExact(byteCount, value.length);
        int offset = 0;
        if (buffered > 0) {
            int copy = Math.min(64 - buffered, value.length);
            System.arraycopy(value, 0, buffer, buffered, copy);
            buffered += copy;
            offset += copy;
            if (buffered == 64) {
                transform(buffer, 0);
                buffered = 0;
            }
        }
        while (offset + 64 <= value.length) {
            transform(value, offset);
            offset += 64;
        }
        if (offset < value.length) {
            buffered = value.length - offset;
            System.arraycopy(value, offset, buffer, 0, buffered);
        }
    }

    String snapshot() {
        ByteBuffer words = ByteBuffer.allocate(32);
        for (int value : hash) {
            words.putInt(value);
        }
        return "v1:" + Long.toUnsignedString(byteCount) + ":" + hex(words.array())
                + ":" + hex(Arrays.copyOf(buffer, buffered));
    }

    long byteCount() {
        return byteCount;
    }

    String finishHex() {
        ReportResumableSha256 copy = new ReportResumableSha256(
                hash.clone(), buffer.clone(), buffered, byteCount
        );
        long bitCount = copy.byteCount * 8L;
        byte[] padding = new byte[copy.buffered < 56
                ? 56 - copy.buffered : 120 - copy.buffered];
        padding[0] = (byte) 0x80;
        copy.update(padding);
        copy.update(ByteBuffer.allocate(Long.BYTES).putLong(bitCount).array());
        if (copy.buffered != 0) {
            throw new IllegalStateException("SHA-256 padding drift");
        }
        ByteBuffer result = ByteBuffer.allocate(32);
        for (int value : copy.hash) {
            result.putInt(value);
        }
        return hex(result.array());
    }

    private void transform(byte[] block, int offset) {
        int[] words = new int[64];
        ByteBuffer input = ByteBuffer.wrap(block, offset, 64);
        for (int index = 0; index < 16; index++) {
            words[index] = input.getInt();
        }
        for (int index = 16; index < words.length; index++) {
            int x = words[index - 15];
            int y = words[index - 2];
            int s0 = Integer.rotateRight(x, 7) ^ Integer.rotateRight(x, 18) ^ (x >>> 3);
            int s1 = Integer.rotateRight(y, 17) ^ Integer.rotateRight(y, 19) ^ (y >>> 10);
            words[index] = words[index - 16] + s0 + words[index - 7] + s1;
        }
        int a=hash[0], b=hash[1], c=hash[2], d=hash[3];
        int e=hash[4], f=hash[5], g=hash[6], h=hash[7];
        for (int index = 0; index < 64; index++) {
            int s1 = Integer.rotateRight(e,6)^Integer.rotateRight(e,11)^Integer.rotateRight(e,25);
            int choice = (e & f) ^ (~e & g);
            int t1 = h + s1 + choice + K[index] + words[index];
            int s0 = Integer.rotateRight(a,2)^Integer.rotateRight(a,13)^Integer.rotateRight(a,22);
            int majority = (a & b) ^ (a & c) ^ (b & c);
            int t2 = s0 + majority;
            h=g; g=f; f=e; e=d+t1; d=c; c=b; b=a; a=t1+t2;
        }
        hash[0]+=a; hash[1]+=b; hash[2]+=c; hash[3]+=d;
        hash[4]+=e; hash[5]+=f; hash[6]+=g; hash[7]+=h;
    }

    private static byte[] parseHex(String value, int expectedBytes) {
        if (value == null || value.length() != expectedBytes * 2
                || !value.matches("[0-9a-f]*")) {
            throw new IllegalArgumentException("invalid resumable SHA-256 hexadecimal state");
        }
        byte[] result = new byte[expectedBytes];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(
                    value.substring(index * 2, index * 2 + 2), 16
            );
        }
        return result;
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }
}
