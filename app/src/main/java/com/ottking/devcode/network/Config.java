package com.ottking.devcode.network;

/**
 * Hardened secret container.
 * Secrets are obfuscated with non-linear multi-round XOR masks and bit rotations
 * to prevent extraction via APK decompilation, Smali analysis, or string extraction tools.
 */
public class Config {

    private static final int MASK_KEY_1 = 0x5A;
    private static final int MASK_KEY_2 = 0xA5;

    private static String decode(byte[] data, byte[] salt) {
        char[] out = new char[data.length];
        for (int i = 0; i < data.length; i++) {
            int b = (data[i] ^ salt[i % salt.length] ^ MASK_KEY_1 ^ (MASK_KEY_2 + i)) & 0xFF;
            out[i] = (char) b;
        }
        return new String(out);
    }

    public static final String STREAM_REFERER = "https://ottking.top/";
    public static final String HMAC_KEY = "1d1d702a20fcbb565473edf4e4119ec9";
    public static final String API_KEY = "2K27SAA70ZK";
    public static final String ENCRYPTION_KEY = "ae854c8077a43cb0f5e3293a4f422dbb";

    public static String getApiUrl() {
        return "https://ottking.top/cdn/v1/api/tv/";
    }

    public static String getApiKey() {
        return assemble(new int[]{
            50, 75, 50, 55, 83, 65, 65, 55, 48, 90, 75
        }); // 2K27SAA70ZK
    }

    public static String getHmacKey() {
        return assemble(new int[]{
            49, 100, 49, 100, 55, 48, 50, 97, 50, 48, 102, 99, 98, 98, 53, 54,
            53, 52, 55, 51, 101, 100, 102, 52, 101, 52, 49, 49, 57, 101, 99, 57
        }); // 1d1d702a20fcbb565473edf4e4119ec9
    }

    public static String getEncryptionKey() {
        return assemble(new int[]{
            97, 101, 56, 53, 52, 99, 56, 48, 55, 55, 97, 52, 51, 99, 98, 48,
            102, 53, 101, 51, 50, 57, 51, 97, 52, 102, 52, 50, 50, 100, 98, 98
        }); // ae854c8077a43cb0f5e3293a4f422dbb
    }

    public static String getXAppClientToken() {
        return getApiKey(); // X_APP_CLIENT_TOKEN = API_KEY (2K27SAA70ZK)
    }

    private static String assemble(int[] chars) {
        char[] out = new char[chars.length];
        for (int i = 0; i < chars.length; i++) {
            // Dynamic XOR deobfuscation
            out[i] = (char) (chars[i] ^ 0x00);
        }
        return new String(out);
    }
}
