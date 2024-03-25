package com.demo.websocket.web.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @Description md5加密工具类
 * @Author abel
 * @Date @2024/3/18 @13:12
 */
@Slf4j
public class MD5Util {
    /**
     * 生成32位MD5加密字符串
     */
    public static String string2MD5(String input) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not found", e);
            return "";
        }
    }

    /**
     * 判断输入的密码和数据库中保存的MD5密码是否一致
     */
    public static boolean passwordIsTrue(String inputPassword, String md5DB) {
        String md5 = string2MD5(inputPassword);
        return md5DB.equals(md5);
    }

    public static void main(String[] args) {
        String password = "123456";
        log.info("原始密码：" + password);
        String md5Password = string2MD5(password);
        log.info("MD5加密后：" + md5Password);
        log.info("密码是否一致：" + passwordIsTrue("123456", "e10adc3949ba59abbe56e057f20f883e"));
    }
}