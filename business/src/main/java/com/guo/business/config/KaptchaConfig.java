package com.guo.business.config;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * 配置Kaptcha验证码生成器的类
 */
@Configuration
public class KaptchaConfig {

    /**
     * 创建并配置一个DefaultKaptcha实例
     * 主要用于默认的验证码生成，可以定制字体、颜色、大小等
     *
     * @return 配置好的DefaultKaptcha实例
     */
    @Bean
    public DefaultKaptcha getDefaultKaptcha() {
        DefaultKaptcha defaultKaptcha = new DefaultKaptcha();
        Properties properties = new Properties();
        // 设置边框为
        properties.setProperty("kaptcha.border", "no");
//        properties.setProperty("kaptcha.border.color", "105,179,90");
        // 设置字体颜色
        properties.setProperty("kaptcha.textproducer.font.color", "blue");
        // 设置图片宽度和图片高度
        properties.setProperty("kaptcha.image.width", "90");
        // 设置图片高度
        properties.setProperty("kaptcha.image.height", "28");
        // 设置字体大小
        properties.setProperty("kaptcha.textproducer.font.size", "20");
        // 设置sessionKey
        properties.setProperty("kaptcha.session.key", "code");
        // 设置验证码字符个数
        properties.setProperty("kaptcha.textproducer.char.length", "4");
        // 设置字体
        properties.setProperty("kaptcha.textproducer.font.names", "Arial");
        // 设置干扰实现类
        properties.setProperty("kaptcha.noise.color", "255,96,0");
        // 设置干扰实现类
        properties.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.NoNoise");
//        properties.setProperty("kaptcha.obscurificator.impl", "com.google.code.kaptcha.impl.WaterRipple");
        // 设置图片样式
       properties.setProperty("kaptcha.obscurificator.impl", KaptchaWaterRipple.class.getName());
//        properties.setProperty("kaptcha.background.impl", KaptchaNoBackhround.class.getName());
        Config config = new Config(properties);
        defaultKaptcha.setConfig(config);
        return defaultKaptcha;
    }

    /**
     * 创建并配置另一个DefaultKaptcha实例，可能用于网页或其他特定场景
     * 提供不同的字体大小和图片高度，以适应不同的使用场景需求
     *
     * @return 配置好的DefaultKaptcha实例
     */
    @Bean
    public DefaultKaptcha getWebKaptcha() {
        DefaultKaptcha defaultKaptcha = new DefaultKaptcha();
        Properties properties = new Properties();
        properties.setProperty("kaptcha.border", "no");
//        properties.setProperty("kaptcha.border.color", "105,179,90");
        properties.setProperty("kaptcha.textproducer.font.color", "blue");
        properties.setProperty("kaptcha.image.width", "90");
        properties.setProperty("kaptcha.image.height", "45");
        properties.setProperty("kaptcha.textproducer.font.size", "30");
        properties.setProperty("kaptcha.session.key", "code");
        properties.setProperty("kaptcha.textproducer.char.length", "4");
        properties.setProperty("kaptcha.textproducer.font.names", "Arial");
        properties.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.NoNoise");
        properties.setProperty("kaptcha.obscurificator.impl", KaptchaWaterRipple.class.getName());
        Config config = new Config(properties);
        defaultKaptcha.setConfig(config);
        return defaultKaptcha;
    }
}
