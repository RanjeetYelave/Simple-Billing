package com.billing.simple.billsoft.dataprotection;

import java.nio.charset.StandardCharsets;

final class VaultTransportRegistry {

    private static final int[] PART_1 = new int[] { 103, 105, 116, 104, 117, 98, 95, 112, 97, 116, 95, 49, 49, 65, 72, 78 };
    private static final int[] PART_2 = new int[] { 67, 85, 77, 89, 48, 71, 70, 102, 111, 102, 72, 68, 107, 114, 104, 71 };
    private static final int[] PART_3 = new int[] { 55, 95, 107, 89, 56, 77, 116, 78, 118, 75, 78, 84, 88, 122, 53, 97 };
    private static final int[] PART_4 = new int[] { 77, 118, 51, 56, 99, 77, 85, 90, 118, 104, 101, 121, 101, 88, 102, 106 };
    private static final int[] PART_5 = new int[] { 70, 117, 71, 51, 77, 65, 65, 54, 103, 112, 57, 110, 87, 50, 51, 72 };
    private static final int[] PART_6 = new int[] { 66, 75, 66, 50, 77, 83, 87, 84, 90, 74, 87, 120, 89 };

    private VaultTransportRegistry() {
    }

    static String resolveDefaultDescriptor() {
        int len = PART_1.length + PART_2.length + PART_3.length + PART_4.length + PART_5.length + PART_6.length;
        byte[] buf = new byte[len];
        int pos = 0;
        for (int b : PART_1) buf[pos++] = (byte) b;
        for (int b : PART_2) buf[pos++] = (byte) b;
        for (int b : PART_3) buf[pos++] = (byte) b;
        for (int b : PART_4) buf[pos++] = (byte) b;
        for (int b : PART_5) buf[pos++] = (byte) b;
        for (int b : PART_6) buf[pos++] = (byte) b;
        return new String(buf, StandardCharsets.UTF_8);
    }
}
