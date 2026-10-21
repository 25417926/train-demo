package com.guo.business.controller;


//import com.guo.business.service.Confirm_OrderService;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import javax.annotation.Resource;
//
//
//
//@RestController
//public class redisController {
//
//    @Resource
//    private RedisTemplate<String, Object> redisTemplate;
//    @Resource
//    private Confirm_OrderService confirm_OrderService;
//
//    @GetMapping("/test")
//    public String testRedisConnection() {
//        redisTemplate.opsForValue().set("test", "test1");
//        Object test = redisTemplate.opsForValue().get("test");
//        return "true"+test;
//    }
//
//}
