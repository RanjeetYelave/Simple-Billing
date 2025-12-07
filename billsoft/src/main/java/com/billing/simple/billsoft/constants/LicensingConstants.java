package com.billing.simple.billsoft.constants;

public final class LicensingConstants {

    private LicensingConstants() {}

    // License Levels
    public static final String LICENSE_TRIAL   = "TRIAL";
    public static final String LICENSE_PREMIUM = "PREMIUM";
    public static final String LICENSE_TEST    = "PREMIUM_TEST";

    // Trial duration
    public static final long TRIAL_DAYS = 30L;

    // 🔐 Activation key HASHES (SHA-256)
    // Plain string keys (not in code):
    //   INV-1Y-PREMIUM-2025-RY
    //   INV-3Y-PREMIUM-2025-RY
    //   INV-LIFE-PREMIUM-2025-RY
    //   INV-TEST-2MIN-2025-RY

    public static final String KEY_1Y_HASH =
            "AA88CF028330909AD8F004C9E0DBF7A3D00AE2520B572ED55B5C990F2BD75B8A";

    public static final String KEY_3Y_HASH =
            "47B76C53B3A59C32DD82405D2469CD3A36D30A91A38E08408E436DDFA14E5730";

    public static final String KEY_LIFE_HASH =
            "39008773163C09AF80B4155D3ED9BECBAEDD5A8AE2932DBA7F4F96B85C7E86C5";

    public static final String KEY_TEST_HASH =
            "E723BC870375726F3451211F079C007A2837786620EC1783EDD5D3C1B26E5F52";
}
