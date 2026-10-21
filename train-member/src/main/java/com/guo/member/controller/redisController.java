package com.guo.member.controller;


//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import javax.annotation.Resource;
//
//
//@RestController
//public class redisController {
//    Logger LOG = LoggerFactory.getLogger(redisController.class);
//    @Resource
//    private RedisTemplate<String, Object> redisTemplate;
//
//    @PostMapping("/test")
//    public String testRedisConnection() {
//        if (redisTemplate == null) {
//            LOG.error("RedisTemplate is not initialized");
//            redisTemplate.opsForValue().set("test", "test1");
//        }
//        Object test = redisTemplate.opsForValue().get("test");
//
//        return "true"+test;
//    }
//}
