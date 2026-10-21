package com.guo.member.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/13
 */
@Service
public class ValidCodeService {
    Logger LOG = LoggerFactory.getLogger(ValidCodeService.class);
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    // 验证码校验
    public boolean ValidForCode(String token, String code) {
        LOG.info("token:{},code:{}", token, code);
        String redisCode = (String) redisTemplate.opsForValue().get(token);
        LOG.info("redisCode:{},code:{}", redisCode, code);
        // 验证码校验
        if (redisCode != null && redisCode.equalsIgnoreCase(code)) {
            redisTemplate.delete(token);
            LOG.info("验证码正确，放行");
            return true;
        } else {
            LOG.error("验证码错误");
        }
        return false;
    }
    // 验证码是否过期
   public boolean isCodeExpired(String token) {
    LOG.info("token:{}", token);



    // 获取验证码并检查是否为 null
    String code = (String) redisTemplate.opsForValue().get(token);
    if (code == null) {
        LOG.error("验证码已过期或不存在");
        return false;
    }

    LOG.info("验证码: {}", code);
    return true;
}

}
