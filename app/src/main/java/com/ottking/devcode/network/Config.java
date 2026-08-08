package com.ottking.devcode.network;

public class Config {

    private static String assemble(int[] chars) {
        StringBuilder sb = new StringBuilder(chars.length);
        for (int c : chars) {
            sb.append((char) c);
        }
        return sb.toString();
    }

    public static String getApiUrl() {
        return assemble(new int[]{
            104, 116, 116, 112, 115, 58, 47, 47, 118, 101, 114, 105, 102, 121, 45, 97,
            112, 112, 46, 97, 108, 119, 97, 121, 115, 100, 97, 116, 97, 46, 110, 101,
            116, 47, 110, 101, 119, 47, 97, 112, 112, 47
        });
    }

    public static String getApiKey() {
        return assemble(new int[]{
            111, 116, 116, 95, 107, 105, 110, 103, 95, 115, 101, 99, 114, 101, 116, 95,
            97, 112, 105, 95, 107, 101, 121, 95, 50, 48, 50, 54
        });
    }

    public static String getHmacKey() {
        return assemble(new int[]{
            111, 116, 116, 95, 107, 105, 110, 103, 95, 104, 109, 97, 99, 95, 115, 101,
            99, 114, 101, 116, 95, 107, 101, 121, 95, 57, 57, 56, 56, 55, 55
        });
    }

    public static String getEncryptionKey() {
        return assemble(new int[]{
            111, 116, 116, 95, 107, 105, 110, 103, 95, 101, 110, 99, 95, 107, 101, 121,
            95, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54
        });
    }
}
