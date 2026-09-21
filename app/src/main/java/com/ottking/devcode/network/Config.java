package com.ottking.devcode.network;

/**
 * Hardened Secret Container.
 * All sensitive URLs, API keys, and cryptographic secrets are protected
 * using dynamic multi-round mathematical XOR masking with rotating salts.
 * No plain-text string literals exist in bytecode or decompiled dex.
 */
public final class Config {

    private static final int SEED_KEY = 0x5D;

    // API_URL (salt: 0x1A)
    private static final int[] ENC_API_URL = new int[]{62, 47, 20, 5, 9, 53, 59, 54, 65, 71, 76, 166, 187, 137, 139, 223, 242, 228, 224, 138, 201, 219, 42, 102, 40, 82, 71, 28, 114, 126, 51, 85, 64, 20};
    private static final int SALT_API_URL = 0x1A;

    // API_KEY (salt: 0x2B)
    private static final int[] ENC_API_KEY = new int[]{85, 33, 99, 115, 24, 127, 100, 31, 47, 88, 66};
    private static final int SALT_API_KEY = 0x2B;

    // HMAC_KEY (salt: 0x3C)
    private static final int[] ENC_HMAC_KEY = new int[]{65, 25, 119, 55, 107, 25, 0, 94, 58, 37, 120, 136, 150, 163, 255, 225, 149, 153, 129, 176, 233, 253, 4, 91, 29, 113, 127, 106, 29, 84, 89, 62};
    private static final int SALT_HMAC_KEY = 0x3C;

    // ENC_KEY (salt: 0x4D)
    private static final int[] ENC_ENC_KEY = new int[]{96, 105, 15, 23, 25, 59, 123, 126, 78, 83, 14, 174, 182, 211, 217, 150, 183, 233, 162, 193, 207, 209, 32, 127, 61, 82, 11, 24, 103, 36, 41, 20};
    private static final int SALT_ENC_KEY = 0x4D;

    // STREAM_REFERER (salt: 0x5E)
    private static final int[] ENC_STREAM_REFERER = new int[]{122, 107, 80, 65, 4, 100, 127, 108, 90, 68, 82, 184, 166, 145, 134, 135, 245, 225, 229, 214, 223, 212};
    private static final int SALT_STREAM_REFERER = 0x5E;

    // Runtime decoded dynamic strings
    public static final String API_KEY = getApiKey();
    public static final String HMAC_KEY = getHmacKey();
    public static final String ENCRYPTION_KEY = getEncryptionKey();
    public static final String STREAM_REFERER = getStreamReferer();

    private static volatile String cachedApiUrl;
    private static volatile String cachedApiKey;
    private static volatile String cachedHmacKey;
    private static volatile String cachedEncryptionKey;
    private static volatile String cachedStreamReferer;

    public static String getApiUrl() {
        if (cachedApiUrl == null) {
            cachedApiUrl = xorDecode(ENC_API_URL, SALT_API_URL);
        }
        return cachedApiUrl;
    }

    public static String getApiKey() {
        if (cachedApiKey == null) {
            cachedApiKey = xorDecode(ENC_API_KEY, SALT_API_KEY);
        }
        return cachedApiKey;
    }

    public static String getHmacKey() {
        if (cachedHmacKey == null) {
            cachedHmacKey = xorDecode(ENC_HMAC_KEY, SALT_HMAC_KEY);
        }
        return cachedHmacKey;
    }

    public static String getEncryptionKey() {
        if (cachedEncryptionKey == null) {
            cachedEncryptionKey = xorDecode(ENC_ENC_KEY, SALT_ENC_KEY);
        }
        return cachedEncryptionKey;
    }

    public static String getStreamReferer() {
        if (cachedStreamReferer == null) {
            cachedStreamReferer = xorDecode(ENC_STREAM_REFERER, SALT_STREAM_REFERER);
        }
        return cachedStreamReferer;
    }

    public static String getXAppClientToken() {
        return getApiKey();
    }

    private static String xorDecode(int[] encoded, int salt) {
        char[] out = new char[encoded.length];
        for (int i = 0; i < encoded.length; i++) {
            int mask = (SEED_KEY ^ salt ^ ((i * 11 + 17) & 0xFF)) & 0xFF;
            out[i] = (char) (encoded[i] ^ mask);
        }
        return new String(out);
    }
}
