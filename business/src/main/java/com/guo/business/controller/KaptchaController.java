package com.guo.business.controller;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.Base64;
import com.guo.common.util.HttpStatus;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/kaptcha")
public class KaptchaController {
    Logger LOG = org.slf4j.LoggerFactory.getLogger(KaptchaController.class);
    @Qualifier("getDefaultKaptcha")
    @Autowired
    DefaultKaptcha defaultKaptcha;

    @Resource
    public RedisTemplate redisTemplate;

    @GetMapping("/image-code/{imageCodeToken}")
    public CommonResp imageCode(@PathVariable(value = "imageCodeToken") String imageCodeToken, HttpServletResponse httpServletResponse) throws Exception {
        CommonResp commonResp = new CommonResp();
        ByteArrayOutputStream jpegOutputStream = new ByteArrayOutputStream();
        try {
            // 生成验证码字符串
            String createText = defaultKaptcha.createText();
            LOG.info("createText:{}", createText);
            // 将生成的验证码放入redis缓存中，后续验证的时候用到
            LOG.info("imageCodeToken:{}", imageCodeToken);
            redisTemplate.opsForValue().set(imageCodeToken, createText, 300, TimeUnit.SECONDS);
            Object o = redisTemplate.opsForValue().get(imageCodeToken);
            LOG.info("redis获取验证码:{}", o);
            // 使用验证码字符串生成验证码图片
            BufferedImage challenge = defaultKaptcha.createImage(createText);
            ImageIO.write(challenge, "jpg", jpegOutputStream);
            byte[] captchaChallengeAsJpeg = jpegOutputStream.toByteArray();
            commonResp.setSuccess(true);
            commonResp.setMessage("验证码生成成功");
            commonResp.setCode(HttpStatus.SUCCESS);
            commonResp.setContent(Base64.encode(captchaChallengeAsJpeg));

        } catch (IllegalArgumentException e) {
            httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND);

        }
        return commonResp;
    }
}
