package com.guo.auth.utils;


import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

public class PwdAESCryUtil {
    private static final String KEY = "Role_paidaxing_t";
    private static SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, KEY.getBytes(StandardCharsets.UTF_8));


    public static String encrypt(String str) {
        return  aes.encryptHex(str);
    }
    public static String decrypt(String str) {
        return  aes.decryptStr(str);
    }
}
