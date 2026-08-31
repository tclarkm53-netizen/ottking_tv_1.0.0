package com.ottking.devcode.config;

public class Config {

    public static String BaseUrl = "https://verify-app.alwaysdata.net/new/app/";
    public static String ApiKey = "ott_king_secret_api_key_2026";
    public static String HmacKey = "ott_king_hmac_secret_key_998877";
    public static String EncryptionKey = "ott_king_enc_key_1234567890123456";

    public static String XAppClientToken="6202_nekot_tneilc_gnik_tto";


    public static String getXAppClientTokens(){
      if(XAppClientToken== null){
        return null;
      }
      return StringUtil.repair_str(XAppClientToken);
    }

    public static String getXAppClientToken(){
      if(XAppClientToken== null){
        return null;
      }
      return StringUtil.repair_str(XAppClientToken);
    }
    public static String getApiKey() {
        if (ApiKey == null) {
            return null;
        }
        return ApiKey;
    }
















    public static String getHmacKey() {
        if (HmacKey == null) {
            return null;
        }
        return HmacKey;
    }

    public static String getEncryptionKey() {
        if (EncryptionKey == null) {
            return null;
        }
        return EncryptionKey;
    }

    public static String getBaseUrl() {
        return BaseUrl;
    }



    public static class StringUtil {
        
        public static String repair_str(String str) {
            if (str == null) {
                return null;
            }
            return new StringBuilder(str).reverse().toString();
        }
        
        public static String processString(String str) {
            if (str == null || str.length() % 4 != 0) {
                return "";
            }
            
            int partSize = str.length() / 4;
            StringBuilder finalResult = new StringBuilder();
            
            for (int i = 0; i < str.length(); i += partSize) {
                String part = str.substring(i, i + partSize);
                String reversedPart = new StringBuilder(part).reverse().toString();
                finalResult.append(reversedPart);
            }
            
            return finalResult.toString();
        }
    }
}