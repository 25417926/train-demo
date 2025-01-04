package com.guo.common.util;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.crypto.GlobalBouncyCastleProvider;
import cn.hutool.json.JSONObject;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import com.guo.common.req.UserDetailsReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class JWTUtil {
    private static final Logger LOG = LoggerFactory.getLogger(JWTUtil.class);

    /**
     * 盐值很重要，不能泄漏，且每个项目都应该不一样，可以放到配置文件中
     */
    private static final String SECRET_KEY_STRING = "guo"; // 秘钥字符串
    private static final DateTime now = DateTime.now();
    private static final DateTime EXPIRATION_TIME = now.offsetNew(DateField.MINUTE, 10); // 过期时间，单位分钟，这里设置为10分钟，可根据实际需求调整
    private static final String CLAIM_KEY_USERNAME = "sub"; // 存储用户名的键名
    private static final String CLAIM_KEY_CREATED = "created"; // 存储令牌创建时间的键名
    private static final String TOKEN_PREFIX = "Bearer"; // 令牌前缀

    public static String createToken(UserDetailsReq req) {
        LOG.info("开始生成JWT token，mobile：{}，password：{}", req.getPassword(), req.getMobile());
        GlobalBouncyCastleProvider.setUseBouncyCastle(false);
        Map<String, Object> payload = new HashMap<>();
        // 签发时间
        payload.put(JWTPayload.ISSUED_AT, now);
        // 过期时间
        payload.put(JWTPayload.EXPIRES_AT, EXPIRATION_TIME);
        // 生效时间
        payload.put(JWTPayload.NOT_BEFORE, now);
        // 内容
        payload.put("id", req.getId());
        payload.put("mobile", req.getMobile());
        payload.put("password", req.getPassword());

        String token = cn.hutool.jwt.JWTUtil.createToken(payload, SECRET_KEY_STRING.getBytes());
        LOG.info("生成JWT token：{}", token);
        return token;
    }

    public static boolean validate(String token) {
        LOG.info("开始JWT token校验，token：{}", token);
        GlobalBouncyCastleProvider.setUseBouncyCastle(false);
        JWT jwt = cn.hutool.jwt.JWTUtil.parseToken(token).setKey(SECRET_KEY_STRING.getBytes());
        // validate包含了verify
        boolean validate = jwt.validate(0);
        LOG.info("JWT token校验结果：{}", validate);
        return validate;
    }

    public static JSONObject getJSONObject(String token) {
        GlobalBouncyCastleProvider.setUseBouncyCastle(false);
        JWT jwt = cn.hutool.jwt.JWTUtil.parseToken(token).setKey(SECRET_KEY_STRING.getBytes());
        JSONObject payloads = jwt.getPayloads();
        payloads.remove(JWTPayload.ISSUED_AT);
        payloads.remove(JWTPayload.EXPIRES_AT);
        payloads.remove(JWTPayload.NOT_BEFORE);
        LOG.info("根据token获取原始内容：{}", payloads);
        return payloads;
    }


}
